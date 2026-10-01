-- Keep owner-authored provenance private and route every public read through the
-- dedicated projection. This also restores trigger invariants that were lost
-- when the publication guard replaced earlier functions.

alter table public.product_nutrition_links
    add column if not exists catalog_content_amount numeric,
    add column if not exists catalog_content_unit text,
    add column if not exists catalog_package_count integer,
    add column if not exists catalog_product_revision text;

alter table public.product_nutrition_links
    drop constraint if exists product_nutrition_links_catalog_specification_valid;

alter table public.product_nutrition_links
    add constraint product_nutrition_links_catalog_specification_valid
    check (
        (
            catalog_content_amount is null
            and catalog_content_unit is null
            and catalog_package_count is null
            and catalog_product_revision is null
        )
        or (
            catalog_content_amount > 0
            and nullif(btrim(catalog_content_unit), '') is not null
            and catalog_package_count > 0
            and catalog_product_revision ~ '^sha256:[0-9a-f]{64}$'
        )
    );

comment on column public.product_nutrition_links.catalog_content_amount is
    'Trusted content amount verified server-side from the PriceTrace product-read.v1 catalog child.';
comment on column public.product_nutrition_links.catalog_content_unit is
    'Trusted content unit verified server-side from the PriceTrace product-read.v1 catalog child.';
comment on column public.product_nutrition_links.catalog_package_count is
    'Trusted package count verified server-side from the PriceTrace product-read.v1 catalog child.';
comment on column public.product_nutrition_links.catalog_product_revision is
    'Trusted sha256 revision returned by the exact PriceTrace product-read.v1 document.';

create or replace function public.nutrition_product_basis_matches_v1(
    p_food_amount numeric,
    p_food_unit text,
    p_catalog_amount numeric,
    p_catalog_unit text
)
returns boolean
language plpgsql
immutable
set search_path = ''
as $$
declare
    v_food_unit text := lower(btrim(p_food_unit));
    v_catalog_unit text := lower(btrim(p_catalog_unit));
    v_food_dimension text;
    v_catalog_dimension text;
    v_food_factor numeric := 1;
    v_catalog_factor numeric := 1;
    v_food_base numeric;
    v_catalog_base numeric;
    v_tolerance numeric;
begin
    if p_food_amount is null
       or p_food_amount <= 0
       or nullif(v_food_unit, '') is null
       or p_catalog_amount is null
       or p_catalog_amount <= 0
       or nullif(v_catalog_unit, '') is null
    then
        return false;
    end if;

    case v_food_unit
        when 'gram' then v_food_unit := 'g';
        when 'grams' then v_food_unit := 'g';
        when 'milligram' then v_food_unit := 'mg';
        when 'milligrams' then v_food_unit := 'mg';
        when 'kilogram' then v_food_unit := 'kg';
        when 'kilograms' then v_food_unit := 'kg';
        when 'milliliter' then v_food_unit := 'ml';
        when 'milliliters' then v_food_unit := 'ml';
        when 'millilitre' then v_food_unit := 'ml';
        when 'millilitres' then v_food_unit := 'ml';
        when 'liter' then v_food_unit := 'l';
        when 'liters' then v_food_unit := 'l';
        when 'litre' then v_food_unit := 'l';
        when 'litres' then v_food_unit := 'l';
        when 'servings' then v_food_unit := 'serving';
        when 'srv' then v_food_unit := 'serving';
        when 'piece' then v_food_unit := 'piece';
        when 'pieces' then v_food_unit := 'piece';
        when 'each' then v_food_unit := 'piece';
        when 'unit' then v_food_unit := 'piece';
        when 'units' then v_food_unit := 'piece';
        when '개' then v_food_unit := 'piece';
        when 'portions' then v_food_unit := 'portion';
        when 'packs' then v_food_unit := 'pack';
        else null;
    end case;

    case v_catalog_unit
        when 'gram' then v_catalog_unit := 'g';
        when 'grams' then v_catalog_unit := 'g';
        when 'milligram' then v_catalog_unit := 'mg';
        when 'milligrams' then v_catalog_unit := 'mg';
        when 'kilogram' then v_catalog_unit := 'kg';
        when 'kilograms' then v_catalog_unit := 'kg';
        when 'milliliter' then v_catalog_unit := 'ml';
        when 'milliliters' then v_catalog_unit := 'ml';
        when 'millilitre' then v_catalog_unit := 'ml';
        when 'millilitres' then v_catalog_unit := 'ml';
        when 'liter' then v_catalog_unit := 'l';
        when 'liters' then v_catalog_unit := 'l';
        when 'litre' then v_catalog_unit := 'l';
        when 'litres' then v_catalog_unit := 'l';
        when 'servings' then v_catalog_unit := 'serving';
        when 'srv' then v_catalog_unit := 'serving';
        when 'piece' then v_catalog_unit := 'piece';
        when 'pieces' then v_catalog_unit := 'piece';
        when 'each' then v_catalog_unit := 'piece';
        when 'unit' then v_catalog_unit := 'piece';
        when 'units' then v_catalog_unit := 'piece';
        when '개' then v_catalog_unit := 'piece';
        when 'portions' then v_catalog_unit := 'portion';
        when 'packs' then v_catalog_unit := 'pack';
        else null;
    end case;

    if v_food_unit = 'mg' then
        v_food_dimension := 'mass';
        v_food_factor := 0.001;
    elsif v_food_unit = 'g' then
        v_food_dimension := 'mass';
    elsif v_food_unit = 'kg' then
        v_food_dimension := 'mass';
        v_food_factor := 1000;
    elsif v_food_unit = 'ml' then
        v_food_dimension := 'volume';
    elsif v_food_unit = 'l' then
        v_food_dimension := 'volume';
        v_food_factor := 1000;
    else
        v_food_dimension := v_food_unit;
    end if;

    if v_catalog_unit = 'mg' then
        v_catalog_dimension := 'mass';
        v_catalog_factor := 0.001;
    elsif v_catalog_unit = 'g' then
        v_catalog_dimension := 'mass';
    elsif v_catalog_unit = 'kg' then
        v_catalog_dimension := 'mass';
        v_catalog_factor := 1000;
    elsif v_catalog_unit = 'ml' then
        v_catalog_dimension := 'volume';
    elsif v_catalog_unit = 'l' then
        v_catalog_dimension := 'volume';
        v_catalog_factor := 1000;
    else
        v_catalog_dimension := v_catalog_unit;
    end if;

    if v_food_dimension is distinct from v_catalog_dimension then
        return false;
    end if;

    v_food_base := p_food_amount * v_food_factor;
    v_catalog_base := p_catalog_amount * v_catalog_factor;
    v_tolerance := greatest(0.001 * v_food_factor, abs(v_food_base) * 0.0001);
    return abs(v_food_base - v_catalog_base) <= v_tolerance;
end;
$$;

revoke execute on function public.nutrition_product_basis_matches_v1(
    numeric, text, numeric, text
) from public, anon, authenticated;
grant execute on function public.nutrition_product_basis_matches_v1(
    numeric, text, numeric, text
) to service_role;

revoke select on table public.nutrition_foods from anon;
revoke select on table public.nutrition_food_nutrients from anon;
revoke select on table public.nutrition_food_components from anon;
revoke select on table public.product_nutrition_links from anon;

alter policy nutrition_foods_select
    on public.nutrition_foods
    to authenticated
    using (
        deleted_at is null
        and owner_id = ((select auth.uid())::text)
    );

alter policy nutrition_food_nutrients_select
    on public.nutrition_food_nutrients
    to authenticated
    using (
        deleted_at is null
        and owner_id = ((select auth.uid())::text)
    );

alter policy nutrition_food_components_select
    on public.nutrition_food_components
    to authenticated
    using (
        deleted_at is null
        and owner_id = ((select auth.uid())::text)
    );

revoke execute on function public.get_nutrition_read_v1(text) from anon;
revoke execute on function public.get_nutrition_read_v2(text) from anon;

create or replace function public.bump_nutrition_food_revision()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if row(
        new.name,
        new.brand,
        new.kind,
        new.category,
        new.basis_amount,
        new.basis_unit,
        new.prep_state,
        new.cooking_method,
        new.calories_kcal,
        new.protein_grams,
        new.carbs_grams,
        new.fat_grams,
        new.sodium_mg,
        new.saturated_fat_grams,
        new.sugars_grams,
        new.fiber_grams,
        new.added_sugars_grams,
        new.trans_fat_grams,
        new.cholesterol_mg,
        new.source_type,
        new.source_reference,
        new.source_version,
        new.deleted_at
    ) is distinct from row(
        old.name,
        old.brand,
        old.kind,
        old.category,
        old.basis_amount,
        old.basis_unit,
        old.prep_state,
        old.cooking_method,
        old.calories_kcal,
        old.protein_grams,
        old.carbs_grams,
        old.fat_grams,
        old.sodium_mg,
        old.saturated_fat_grams,
        old.sugars_grams,
        old.fiber_grams,
        old.added_sugars_grams,
        old.trans_fat_grams,
        old.cholesterol_mg,
        old.source_type,
        old.source_reference,
        old.source_version,
        old.deleted_at
    ) then
        new.revision := greatest(coalesce(new.revision, 1), old.revision + 1);
    else
        new.revision := greatest(coalesce(new.revision, old.revision), old.revision);
    end if;
    return new;
end;
$$;

create or replace function public.guard_product_nutrition_link_update()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
    v_food_id text := case when tg_op = 'DELETE'
        then old.nutrition_food_id
        else new.nutrition_food_id
    end;
    v_is_service_role boolean := coalesce((select auth.role()) = 'service_role', false);
begin
    if exists (
        select 1
        from public.nutrition_foods food
        where food.id = v_food_id
          and food.visibility = 'public'
          and food.deleted_at is null
    ) then
        raise exception 'Unpublish Nutrition before changing its product link.'
            using errcode = '55000';
    end if;

    if tg_op = 'DELETE' then
        return old;
    end if;

    if new.owner_id is distinct from old.owner_id
       or new.nutrition_food_id is distinct from old.nutrition_food_id
       or new.catalog_product_id is distinct from old.catalog_product_id
       or new.standard_product_id is distinct from old.standard_product_id
       or new.source_type is distinct from old.source_type
       or new.proposal_reference is distinct from old.proposal_reference
       or new.product_contract_version is distinct from old.product_contract_version
    then
        raise exception 'Product nutrition link identity and provenance are immutable.'
            using errcode = '23514';
    end if;

    if row(
        new.catalog_content_amount,
        new.catalog_content_unit,
        new.catalog_package_count,
        new.catalog_product_revision
    ) is distinct from row(
        old.catalog_content_amount,
        old.catalog_content_unit,
        old.catalog_package_count,
        old.catalog_product_revision
    ) and not v_is_service_role then
        raise exception 'Only the trusted publication service can set PriceTrace catalog verification.'
            using errcode = '42501';
    end if;

    if row(
        new.status,
        new.reviewed_at,
        new.deleted_at,
        new.catalog_content_amount,
        new.catalog_content_unit,
        new.catalog_package_count,
        new.catalog_product_revision
    ) is distinct from row(
        old.status,
        old.reviewed_at,
        old.deleted_at,
        old.catalog_content_amount,
        old.catalog_content_unit,
        old.catalog_package_count,
        old.catalog_product_revision
    )
    then
        new.revision := greatest(coalesce(new.revision, 1), old.revision + 1);
    else
        new.revision := old.revision;
    end if;
    return new;
end;
$$;

alter policy product_nutrition_links_insert
    on public.product_nutrition_links
    with check (
        owner_id = ((select auth.uid())::text)
        and source_type = 'manual_selection'
        and status = 'approved'
        and reviewed_at is not null
        and catalog_content_amount is null
        and catalog_content_unit is null
        and catalog_package_count is null
        and catalog_product_revision is null
        and exists (
            select 1
            from public.nutrition_foods food
            where food.id = nutrition_food_id
              and food.owner_id = ((select auth.uid())::text)
              and food.visibility = 'private'
              and food.deleted_at is null
        )
    );

revoke execute on function public.set_product_nutrition_publication_v1(
    text, uuid, boolean
) from public, anon, authenticated;

create or replace function public.set_product_nutrition_publication_v2(
    p_owner_id text,
    p_nutrition_food_id text,
    p_catalog_product_id uuid,
    p_standard_product_id uuid,
    p_catalog_content_amount numeric,
    p_catalog_content_unit text,
    p_catalog_package_count integer,
    p_catalog_product_revision text,
    p_publish boolean
)
returns table (
    nutrition_food_id text,
    catalog_product_id uuid,
    visibility text,
    publication_revision integer,
    published_at timestamptz,
    updated_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_food public.nutrition_foods%rowtype;
    v_link public.product_nutrition_links%rowtype;
    v_now timestamptz := now();
    v_snapshot jsonb;
begin
    if coalesce((select auth.role()), '') <> 'service_role' then
        raise exception 'Trusted publication service role is required.' using errcode = '42501';
    end if;
    if nullif(btrim(p_owner_id), '') is null then
        raise exception 'A verified Nutrition owner is required.' using errcode = '22023';
    end if;

    select food.*
    into v_food
    from public.nutrition_foods food
    where food.id = p_nutrition_food_id
      and food.owner_id = p_owner_id
      and food.deleted_at is null
    for update;

    if not found then
        raise exception 'The owner Nutrition row was not found.' using errcode = 'P0002';
    end if;

    select link.*
    into v_link
    from public.product_nutrition_links link
    where link.owner_id = p_owner_id
      and link.nutrition_food_id = p_nutrition_food_id
      and link.catalog_product_id = p_catalog_product_id
      and (p_publish is false or (
          link.status = 'approved'
          and link.deleted_at is null
      ))
    order by
        case when link.status = 'approved' and link.deleted_at is null then 0 else 1 end,
        link.reviewed_at desc nulls last,
        link.created_at desc
    limit 1
    for update;

    if not found then
        raise exception 'An exact PriceTrace product link is required.' using errcode = '23514';
    end if;

    if not p_publish and v_food.visibility = 'private' then
        return query
        select
            v_food.id,
            p_catalog_product_id,
            v_food.visibility,
            v_food.publication_revision,
            v_food.published_at,
            v_food.updated_at;
        return;
    end if;

    if p_publish and (
        v_food.basis_amount <= 0
        or nullif(btrim(v_food.basis_unit), '') is null
        or v_food.calories_kcal is null
        or v_food.protein_grams is null
        or v_food.carbs_grams is null
        or v_food.fat_grams is null
        or v_food.sodium_mg is null
        or v_food.saturated_fat_grams is null
        or v_food.sugars_grams is null
    ) then
        raise exception 'Basis and seven required Nutrition values are required for publication.'
            using errcode = '23514';
    end if;

    if p_publish and (
        p_standard_product_id is null
        or p_catalog_content_amount is null
        or p_catalog_content_amount <= 0
        or nullif(btrim(p_catalog_content_unit), '') is null
        or p_catalog_package_count is null
        or p_catalog_package_count <= 0
        or p_catalog_product_revision is null
        or p_catalog_product_revision !~ '^sha256:[0-9a-f]{64}$'
    ) then
        raise exception 'A server-verified PriceTrace product-read.v1 document is required.'
            using errcode = '23514';
    end if;

    if p_publish and v_link.standard_product_id is distinct from p_standard_product_id then
        raise exception 'The approved link standard product does not match PriceTrace.'
            using errcode = '23514';
    end if;

    if p_publish and not public.nutrition_product_basis_matches_v1(
        v_food.basis_amount,
        v_food.basis_unit,
        p_catalog_content_amount,
        p_catalog_content_unit
    ) then
        raise exception 'Nutrition basis does not match the server-verified PriceTrace catalog specification.'
            using errcode = '23514';
    end if;

    if p_publish and v_food.visibility = 'public' then
        if row(
            v_link.catalog_content_amount,
            v_link.catalog_content_unit,
            v_link.catalog_package_count,
            v_link.catalog_product_revision
        ) is distinct from row(
            p_catalog_content_amount,
            p_catalog_content_unit,
            p_catalog_package_count,
            p_catalog_product_revision
        ) then
            raise exception 'Unpublish Nutrition before accepting a changed PriceTrace product revision.'
                using errcode = '55000';
        end if;
        return query
        select
            v_food.id,
            p_catalog_product_id,
            v_food.visibility,
            v_food.publication_revision,
            v_food.published_at,
            v_food.updated_at;
        return;
    end if;

    if p_publish then
        update public.product_nutrition_links link
        set catalog_content_amount = p_catalog_content_amount,
            catalog_content_unit = btrim(p_catalog_content_unit),
            catalog_package_count = p_catalog_package_count,
            catalog_product_revision = p_catalog_product_revision,
            updated_at = v_now
        where link.id = v_link.id
        returning link.* into v_link;
    end if;

    update public.nutrition_foods food
    set visibility = case when p_publish then 'public' else 'private' end,
        publication_revision = food.publication_revision + 1,
        published_at = case when p_publish then v_now else null end,
        published_by = case when p_publish then p_owner_id else null end,
        updated_at = v_now
    where food.id = p_nutrition_food_id
    returning food.* into v_food;

    select jsonb_build_object(
        'contract_version', 'nutrition-read.v1',
        'nutrition_food_id', v_food.id,
        'name', v_food.name,
        'kind', v_food.kind,
        'basis_amount', v_food.basis_amount,
        'basis_unit', v_food.basis_unit,
        'prep_state', v_food.prep_state,
        'nutrition_values', jsonb_build_object(
            'calories_kcal', v_food.calories_kcal,
            'protein_grams', v_food.protein_grams,
            'carbs_grams', v_food.carbs_grams,
            'fat_grams', v_food.fat_grams,
            'sodium_mg', v_food.sodium_mg,
            'saturated_fat_grams', v_food.saturated_fat_grams,
            'sugars_grams', v_food.sugars_grams,
            'fiber_grams', v_food.fiber_grams,
            'added_sugars_grams', v_food.added_sugars_grams,
            'trans_fat_grams', v_food.trans_fat_grams,
            'cholesterol_mg', v_food.cholesterol_mg
        ),
        'source_type', v_food.source_type,
        'source_reference', null,
        'source_revision', null,
        'revision', v_food.revision,
        'catalog_product_id', p_catalog_product_id,
        'catalog_specification', jsonb_build_object(
            'content_amount', v_link.catalog_content_amount,
            'content_unit', v_link.catalog_content_unit,
            'package_count', v_link.catalog_package_count,
            'product_revision', v_link.catalog_product_revision
        )
    ) into v_snapshot;

    insert into public.nutrition_food_publication_events (
        nutrition_food_id,
        product_nutrition_link_id,
        owner_id,
        catalog_product_id,
        action,
        food_revision,
        publication_revision,
        nutrition_snapshot
    ) values (
        v_food.id,
        v_link.id,
        p_owner_id,
        p_catalog_product_id,
        case when p_publish then 'publish' else 'unpublish' end,
        v_food.revision,
        v_food.publication_revision,
        v_snapshot
    );

    return query
    select
        v_food.id,
        p_catalog_product_id,
        v_food.visibility,
        v_food.publication_revision,
        v_food.published_at,
        v_food.updated_at;
end;
$$;

revoke all on function public.set_product_nutrition_publication_v2(
    text, text, uuid, uuid, numeric, text, integer, text, boolean
) from public, anon, authenticated;
grant execute on function public.set_product_nutrition_publication_v2(
    text, text, uuid, uuid, numeric, text, integer, text, boolean
) to service_role;

comment on function public.set_product_nutrition_publication_v2(
    text, text, uuid, uuid, numeric, text, integer, text, boolean
) is 'Service-role-only publication boundary. Exact catalog identity and specification must come from a server-verified PriceTrace product-read.v1 response.';

create or replace function public.get_public_product_nutrition_v1(
    p_namespace text,
    p_catalog_product_id uuid
)
returns table (
    contract_version text,
    nutrition_food_id text,
    name text,
    kind text,
    basis_amount numeric,
    basis_unit text,
    prep_state text,
    nutrition_values jsonb,
    micronutrients jsonb,
    source_type text,
    source_reference text,
    source_revision text,
    revision integer,
    catalog_product_id uuid
)
language sql
stable
security definer
set search_path = ''
as $$
    select
        'nutrition-read.v1'::text,
        food.id,
        food.name,
        food.kind,
        food.basis_amount,
        food.basis_unit,
        food.prep_state,
        jsonb_build_object(
            'calories_kcal', food.calories_kcal,
            'protein_grams', food.protein_grams,
            'carbs_grams', food.carbs_grams,
            'fat_grams', food.fat_grams,
            'sodium_mg', food.sodium_mg,
            'saturated_fat_grams', food.saturated_fat_grams,
            'sugars_grams', food.sugars_grams,
            'fiber_grams', food.fiber_grams,
            'added_sugars_grams', food.added_sugars_grams,
            'trans_fat_grams', food.trans_fat_grams,
            'cholesterol_mg', food.cholesterol_mg
        ),
        coalesce(nutrients.values, '{}'::jsonb),
        food.source_type,
        null::text,
        null::text,
        food.revision,
        link.catalog_product_id
    from public.product_nutrition_links link
    join public.nutrition_foods food
      on food.id = link.nutrition_food_id
    left join lateral (
        select jsonb_object_agg(
            nutrient.nutrient_code,
            jsonb_build_object('amount', nutrient.amount, 'unit', nutrient.unit)
        ) as values
        from public.nutrition_food_nutrients nutrient
        where nutrient.food_id = food.id
          and nutrient.deleted_at is null
          and nutrient.amount is not null
    ) nutrients on true
    where p_namespace = 'pricetrace'
      and link.catalog_product_id = p_catalog_product_id
      and link.status = 'approved'
      and link.deleted_at is null
      and food.visibility = 'public'
      and food.published_at is not null
      and food.deleted_at is null
      and link.catalog_product_revision ~ '^sha256:[0-9a-f]{64}$'
      and public.nutrition_product_basis_matches_v1(
          food.basis_amount,
          food.basis_unit,
          link.catalog_content_amount,
          link.catalog_content_unit
      )
    order by food.published_at desc, link.reviewed_at desc, link.created_at desc
    limit 1;
$$;

comment on function public.get_public_product_nutrition_v1(text, uuid) is
    'Public nutrition-read.v1 projection. Owner-authored source references are redacted.';
