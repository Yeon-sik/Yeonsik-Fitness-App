-- Atomic OCR publication boundary for one user-reviewed restaurant estimate.
-- Generic canonical imports remain private and do not create publication intent.

create table if not exists public.nutrition_ocr_dining_out_publications (
    owner_id text not null,
    idempotency_key text not null
        check (length(btrim(idempotency_key)) between 1 and 200),
    request_payload jsonb not null check (jsonb_typeof(request_payload) = 'object'),
    canonical_import_id uuid not null
        references public.nutrition_canonical_imports(id) on delete restrict,
    nutrition_food_id text not null
        references public.nutrition_foods(id) on delete restrict,
    restaurant_id uuid not null,
    restaurant_location_id uuid not null,
    restaurant_menu_id uuid not null,
    catalog_product_id uuid not null,
    nutrition_link_id uuid not null
        references public.product_nutrition_links(id) on delete restrict,
    nutrition_link_revision integer not null check (nutrition_link_revision >= 1),
    food_revision integer not null check (food_revision >= 1),
    publication_revision integer not null check (publication_revision >= 1),
    visibility text not null check (visibility = 'public'),
    published_at timestamptz not null,
    created_at timestamptz not null default now(),
    primary key (owner_id, idempotency_key)
);

alter table public.nutrition_ocr_dining_out_publications enable row level security;
revoke all on public.nutrition_ocr_dining_out_publications from public, anon, authenticated;

comment on table public.nutrition_ocr_dining_out_publications is
    'Owner-scoped immutable request/result ledger for explicitly requested OCR dining-out publication.';

create or replace function public.publish_verified_ocr_dining_out_nutrition_v1(
    p_idempotency_key text,
    p_canonical_import_id uuid,
    p_nutrition_food_id text,
    p_restaurant_id uuid,
    p_restaurant_location_id uuid,
    p_restaurant_menu_id uuid,
    p_catalog_product_id uuid
)
returns table (
    canonical_import_id uuid,
    nutrition_food_id text,
    restaurant_id uuid,
    restaurant_location_id uuid,
    restaurant_menu_id uuid,
    catalog_product_id uuid,
    nutrition_link_id uuid,
    nutrition_link_revision integer,
    visibility text,
    food_revision integer,
    publication_revision integer,
    published_at timestamptz,
    replayed boolean
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_id text := (select auth.uid())::text;
    v_key text := btrim(coalesce(p_idempotency_key, ''));
    v_request_payload jsonb;
    v_existing public.nutrition_ocr_dining_out_publications%rowtype;
    v_canonical public.nutrition_canonical_imports%rowtype;
    v_food public.nutrition_foods%rowtype;
    v_food_identity jsonb;
    v_identity_document jsonb;
    v_identity_values jsonb;
    v_identity_value jsonb;
    v_identity_key text;
    v_candidate_id uuid;
    v_now timestamptz := now();
    v_existing_link_id uuid;
    v_existing_link_deleted_at timestamptz;
    v_attached record;
    v_link record;
    v_publication record;
begin
    if v_owner_id is null then
        raise exception 'Authentication is required.' using errcode = '42501';
    end if;
    if length(v_key) not between 1 and 200
       or p_canonical_import_id is null
       or nullif(btrim(coalesce(p_nutrition_food_id, '')), '') is null
       or p_restaurant_id is null
       or p_restaurant_location_id is null
       or p_restaurant_menu_id is null
       or p_catalog_product_id is null then
        raise exception 'A key, canonical import, Nutrition food, and all four exact PriceTrace IDs are required.'
            using errcode = '23514';
    end if;

    v_request_payload := jsonb_build_object(
        'contract_version', 'ocr-dining-out-nutrition-publication.v1',
        'idempotency_key', v_key,
        'canonical_import_id', p_canonical_import_id,
        'nutrition_food_id', p_nutrition_food_id,
        'restaurant_id', p_restaurant_id,
        'restaurant_location_id', p_restaurant_location_id,
        'restaurant_menu_id', p_restaurant_menu_id,
        'catalog_product_id', p_catalog_product_id
    );

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(v_owner_id || ':ocr-dining-out-publication:' || v_key, 0)
    );

    select saved.*
    into v_existing
    from public.nutrition_ocr_dining_out_publications as saved
    where saved.owner_id = v_owner_id
      and saved.idempotency_key = v_key
    for update;

    if found then
        if v_existing.request_payload <> v_request_payload then
            raise exception 'The OCR publication idempotency key was already used with a different request.'
                using errcode = '23505';
        end if;
        return query
        select
            saved.canonical_import_id,
            saved.nutrition_food_id,
            saved.restaurant_id,
            saved.restaurant_location_id,
            saved.restaurant_menu_id,
            saved.catalog_product_id,
            saved.nutrition_link_id,
            saved.nutrition_link_revision,
            saved.visibility,
            saved.food_revision,
            saved.publication_revision,
            saved.published_at,
            true
        from public.nutrition_ocr_dining_out_publications as saved
        where saved.owner_id = v_owner_id
          and saved.idempotency_key = v_key;
        return;
    end if;

    select imported.*
    into v_canonical
    from public.nutrition_canonical_imports as imported
    where imported.id = p_canonical_import_id
      and imported.owner_id = v_owner_id
      and imported.nutrition_food_id = p_nutrition_food_id
      and imported.input_contract = 'food-estimate.v1'
      and imported.projection_source_type = 'food_image_estimate'
      and imported.user_verified is true
    for update;

    if not found then
        raise exception 'A verified owner-owned canonical food-estimate import is required.'
            using errcode = 'P0002';
    end if;

    select food.*
    into v_food
    from public.nutrition_foods as food
    where food.id = p_nutrition_food_id
      and food.owner_id = v_owner_id
      and food.deleted_at is null
    for update;

    if not found then
        raise exception 'The owner Nutrition food was not found.' using errcode = 'P0002';
    end if;
    if v_food.kind <> 'external_menu'
       or v_food.source_type <> 'food_image_estimate'
       or v_food.visibility <> 'private'
       or v_food.calories_kcal is null
       or v_food.protein_grams is null
       or v_food.carbs_grams is null
       or v_food.fat_grams is null then
        raise exception 'Only a private canonical OCR dining-out Nutrition food can be published by this RPC.'
            using errcode = '23514';
    end if;

    begin
        v_food_identity := nullif(btrim(v_food.source_reference), '')::jsonb;
    exception when others then
        raise exception 'The canonical dining-out source identity is malformed.' using errcode = '23514';
    end;

    -- Fitness cannot join into PriceTrace's separate database. If the canonical
    -- import already captured any exact PT IDs, however, reject a changed or
    -- mismatched identity instead of silently replacing it.
    for v_identity_document in
        select v_canonical.pricetrace_identity
        union all
        select v_food_identity
    loop
        if jsonb_typeof(v_identity_document) <> 'object' then
            continue;
        end if;
        foreach v_identity_values in array array[
            v_identity_document,
            v_identity_document -> 'pricetrace_identity'
        ] loop
            if jsonb_typeof(v_identity_values) <> 'object' then
                continue;
            end if;
            foreach v_identity_key in array array[
                'restaurant_id',
                'restaurant_location_id',
                'restaurant_menu_id',
                'catalog_product_id'
            ] loop
                v_identity_value := v_identity_values -> v_identity_key;
                if v_identity_value is null or v_identity_value = 'null'::jsonb then
                    continue;
                end if;
                begin
                    v_candidate_id := (v_identity_value #>> '{}')::uuid;
                exception when others then
                    raise exception 'The canonical import contains a malformed PriceTrace identity.'
                        using errcode = '23514';
                end;
                if v_candidate_id is distinct from (v_request_payload ->> v_identity_key)::uuid then
                    raise exception 'The requested PriceTrace identity conflicts with the canonical import.'
                        using errcode = '23514';
                end if;
            end loop;
        end loop;
    end loop;

    -- These owner-only RPCs retain their existing validation and auditing. A
    -- failure at any step aborts this outer SQL statement and rolls back all
    -- identity, link, publication-event, and idempotency writes together.
    select attached.*
    into v_attached
    from public.attach_dining_out_menu_identity_v1(
        p_nutrition_food_id,
        p_restaurant_id,
        p_restaurant_location_id,
        p_restaurant_menu_id,
        p_catalog_product_id
    ) as attached;
    if not found
       or v_attached.nutrition_food_id <> p_nutrition_food_id then
        raise exception 'The exact owner dining-out identity was not attached.' using errcode = '23514';
    end if;

    -- The Sep20 helper's INSERT RETURNING uses output-column names that are
    -- ambiguous in PL/pgSQL. Prepare the exact approved row here so that its
    -- existing-link branch returns it without entering that broken INSERT
    -- branch. Keep the already-applied helper definition unchanged.
    select link.id, link.deleted_at
    into v_existing_link_id, v_existing_link_deleted_at
    from public.product_nutrition_links as link
    where link.owner_id = v_owner_id
      and link.nutrition_food_id = p_nutrition_food_id
      and link.catalog_product_id = p_catalog_product_id
      and link.status = 'approved'
    order by link.created_at desc
    limit 1
    for update;

    update public.product_nutrition_links as link
    set deleted_at = v_now,
        updated_at = v_now
    where link.owner_id = v_owner_id
      and link.nutrition_food_id = p_nutrition_food_id
      and link.status = 'approved'
      and link.deleted_at is null
      and link.catalog_product_id <> p_catalog_product_id;

    if v_existing_link_id is not null and v_existing_link_deleted_at is not null then
        update public.product_nutrition_links as link
        set deleted_at = null,
            reviewed_at = v_now,
            updated_at = v_now
        where link.id = v_existing_link_id;
    elsif v_existing_link_id is null then
        insert into public.product_nutrition_links (
            owner_id,
            nutrition_food_id,
            catalog_product_id,
            status,
            source_type,
            proposal_reference,
            product_contract_version,
            revision,
            reviewed_at,
            created_at,
            updated_at,
            deleted_at
        ) values (
            v_owner_id,
            p_nutrition_food_id,
            p_catalog_product_id,
            'approved',
            'manual_selection',
            'FitnessApp dining-out publication',
            'product-read.v1',
            1,
            v_now,
            v_now,
            v_now,
            null
        );
    end if;

    select linked.*
    into v_link
    from public.attach_dining_out_menu_nutrition_link_v1(
        p_nutrition_food_id,
        p_catalog_product_id
    ) as linked;
    if not found
       or v_link.status <> 'approved'
       or v_link.nutrition_food_id <> p_nutrition_food_id
       or v_link.catalog_product_id <> p_catalog_product_id then
        raise exception 'The exact approved Nutrition link was not created.' using errcode = '23514';
    end if;

    select published.*
    into v_publication
    from public.set_dining_out_menu_publication_v1(p_nutrition_food_id, true) as published;
    if not found
       or v_publication.visibility <> 'public'
       or v_publication.restaurant_id is distinct from p_restaurant_id
       or v_publication.restaurant_location_id is distinct from p_restaurant_location_id
       or v_publication.restaurant_menu_id is distinct from p_restaurant_menu_id
       or v_publication.catalog_product_id is distinct from p_catalog_product_id then
        raise exception 'The publication result does not match the requested exact identity.'
            using errcode = '23514';
    end if;

    select food.*
    into v_food
    from public.nutrition_foods as food
    where food.id = p_nutrition_food_id
      and food.owner_id = v_owner_id;

    insert into public.nutrition_ocr_dining_out_publications (
        owner_id,
        idempotency_key,
        request_payload,
        canonical_import_id,
        nutrition_food_id,
        restaurant_id,
        restaurant_location_id,
        restaurant_menu_id,
        catalog_product_id,
        nutrition_link_id,
        nutrition_link_revision,
        food_revision,
        publication_revision,
        visibility,
        published_at
    ) values (
        v_owner_id,
        v_key,
        v_request_payload,
        p_canonical_import_id,
        p_nutrition_food_id,
        p_restaurant_id,
        p_restaurant_location_id,
        p_restaurant_menu_id,
        p_catalog_product_id,
        v_link.link_id,
        v_link.revision,
        v_food.revision,
        v_publication.publication_revision,
        v_publication.visibility,
        v_publication.published_at
    );

    return query select
        p_canonical_import_id,
        p_nutrition_food_id,
        p_restaurant_id,
        p_restaurant_location_id,
        p_restaurant_menu_id,
        p_catalog_product_id,
        v_link.link_id,
        v_link.revision,
        v_publication.visibility,
        v_food.revision,
        v_publication.publication_revision,
        v_publication.published_at,
        false;
end;
$$;

revoke all on function public.publish_verified_ocr_dining_out_nutrition_v1(
    text, uuid, text, uuid, uuid, uuid, uuid
) from public, anon;
grant execute on function public.publish_verified_ocr_dining_out_nutrition_v1(
    text, uuid, text, uuid, uuid, uuid, uuid
) to authenticated;

comment on function public.publish_verified_ocr_dining_out_nutrition_v1(
    text, uuid, text, uuid, uuid, uuid, uuid
) is
    'Explicit OCR final-review publication for one owner-owned, verified food-estimate.v1 dining-out import and four exact PriceTrace IDs; atomically attaches identity, approves the nutrition link, publishes, and records idempotent replay state.';
