-- Complete the cross-project Nutrition link contract used by PriceTrace.
-- Nutrition remains the owner of link state; PriceTrace never receives a
-- foreign key into its database.

alter table public.product_nutrition_links
    add column if not exists proposal_source_revision text;

alter table public.product_nutrition_links
    drop constraint if exists product_nutrition_links_proposal_source_revision_check;

alter table public.product_nutrition_links
    add constraint product_nutrition_links_proposal_source_revision_check
    check (
        proposal_source_revision is null
        or proposal_source_revision ~ '^sha256:[0-9a-f]{64}$'
    );

create table if not exists public.product_nutrition_link_unlink_proposals (
    id uuid primary key default gen_random_uuid(),
    owner_id text not null,
    nutrition_food_id text not null references public.nutrition_foods(id) on delete cascade,
    catalog_product_id uuid not null,
    source_revision text not null check (source_revision ~ '^sha256:[0-9a-f]{64}$'),
    source jsonb not null,
    status text not null default 'pending' check (status in ('pending', 'accepted', 'rejected')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists product_nutrition_link_unlink_pending_idx
    on public.product_nutrition_link_unlink_proposals
    (owner_id, nutrition_food_id, catalog_product_id)
    where status = 'pending';

alter table public.product_nutrition_link_unlink_proposals enable row level security;

grant select on public.product_nutrition_link_unlink_proposals to authenticated;
revoke insert, update, delete on public.product_nutrition_link_unlink_proposals
    from anon, authenticated;

drop policy if exists product_nutrition_link_unlink_proposals_select
    on public.product_nutrition_link_unlink_proposals;
create policy product_nutrition_link_unlink_proposals_select
    on public.product_nutrition_link_unlink_proposals
    for select
    to authenticated
    using (owner_id = ((select auth.uid())::text));

-- Keep the proposal source revision immutable once a trusted proposal exists.
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
       or new.source_type is distinct from old.source_type
       or new.proposal_reference is distinct from old.proposal_reference
       or new.proposal_source_revision is distinct from old.proposal_source_revision
       or new.product_contract_version is distinct from old.product_contract_version
    then
        raise exception 'Product nutrition link identity and provenance are immutable.'
            using errcode = '23514';
    end if;

    if row(
            new.status,
            new.reviewed_at,
            new.deleted_at,
            new.catalog_product_revision,
            new.catalog_content_amount,
            new.catalog_content_unit,
            new.catalog_package_count
       ) is distinct from row(
            old.status,
            old.reviewed_at,
            old.deleted_at,
            old.catalog_product_revision,
            old.catalog_content_amount,
            old.catalog_content_unit,
            old.catalog_package_count
       )
    then
        new.revision := greatest(coalesce(new.revision, 1), old.revision + 1);
    else
        new.revision := old.revision;
    end if;
    return new;
end;
$$;

drop trigger if exists product_nutrition_links_update_guard
    on public.product_nutrition_links;
create trigger product_nutrition_links_update_guard
before update or delete on public.product_nutrition_links
for each row execute function public.guard_product_nutrition_link_update();

create or replace function public.nutrition_read_v1_row(
    p_food public.nutrition_foods,
    p_catalog_product_id uuid,
    p_catalog_product_revision text default null,
    p_catalog_content_amount numeric default null,
    p_catalog_content_unit text default null,
    p_catalog_package_count integer default null
)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
    select jsonb_build_object(
        'contract_version', 'nutrition-read.v1',
        'nutrition_food_id', p_food.id,
        'name', p_food.name,
        'kind', p_food.kind,
        'basis_amount', p_food.basis_amount,
        'basis_unit', p_food.basis_unit,
        'prep_state', p_food.prep_state,
        'nutrition_values', jsonb_build_object(
            'calories_kcal', p_food.calories_kcal,
            'protein_grams', p_food.protein_grams,
            'carbs_grams', p_food.carbs_grams,
            'fat_grams', p_food.fat_grams,
            'sodium_mg', p_food.sodium_mg,
            'saturated_fat_grams', p_food.saturated_fat_grams,
            'sugars_grams', p_food.sugars_grams,
            'fiber_grams', p_food.fiber_grams,
            'added_sugars_grams', p_food.added_sugars_grams,
            'trans_fat_grams', p_food.trans_fat_grams,
            'cholesterol_mg', p_food.cholesterol_mg
        ),
        'micronutrients', coalesce((
            select jsonb_object_agg(
                nutrient.nutrient_code,
                jsonb_build_object('amount', nutrient.amount, 'unit', nutrient.unit)
            )
            from public.nutrition_food_nutrients nutrient
            where nutrient.food_id = p_food.id
              and nutrient.deleted_at is null
              and nutrient.amount is not null
        ), '{}'::jsonb),
        'source_type', p_food.source_type,
        'source_reference', p_food.source_reference,
        'source_revision', p_food.source_version,
        'revision', p_food.revision,
        'catalog_product_id', p_catalog_product_id,
        'catalog_product_revision', p_catalog_product_revision,
        'catalog_content_amount', p_catalog_content_amount,
        'catalog_content_unit', p_catalog_content_unit,
        'catalog_package_count', p_catalog_package_count
    );
$$;

revoke all on function public.nutrition_read_v1_row(
    public.nutrition_foods, uuid, text, numeric, text, integer
) from public;

create or replace function public.get_product_nutrition_link_state_v1(
    p_namespace text,
    p_catalog_product_id uuid
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_user_id text := (select auth.uid())::text;
    v_approved jsonb;
    v_pending jsonb;
    v_revision text;
    v_payload jsonb;
begin
    if v_user_id is null then
        raise exception 'Authentication is required.' using errcode = '42501';
    end if;
    if p_namespace <> 'pricetrace' or p_catalog_product_id is null then
        raise exception 'A valid PriceTrace namespace and catalog product ID are required.'
            using errcode = '22023';
    end if;

    select coalesce(jsonb_agg(item.document order by item.approved_at desc, item.id), '[]'::jsonb)
    into v_approved
    from (
        select
            link.id,
            link.reviewed_at as approved_at,
            jsonb_build_object(
                'id', link.id,
                'identity', jsonb_build_object(
                    'namespace', p_namespace,
                    'catalogProductId', link.catalog_product_id,
                    'nutritionFoodId', link.nutrition_food_id
                ),
                'status', 'approved',
                'sourceRevision', link.catalog_product_revision,
                'approvalRevision', link.revision,
                'approvedAt', link.reviewed_at,
                'candidateEvidence', jsonb_build_object(
                    'nutritionFoodName', food.name,
                    'nutritionContract', 'nutrition-read.v1',
                    'nutritionSourceType', food.source_type,
                    'nutritionSourceReference', food.source_reference,
                    'nutritionSourceRevision', food.source_version,
                    'nutritionRevision', food.revision
                ),
                'nutritionFood', public.nutrition_read_v1_row(
                    food,
                    link.catalog_product_id,
                    link.catalog_product_revision,
                    link.catalog_content_amount,
                    link.catalog_content_unit,
                    link.catalog_package_count
                )
            ) as document
        from public.product_nutrition_links link
        join public.nutrition_foods food on food.id = link.nutrition_food_id
        where link.owner_id = v_user_id
          and link.catalog_product_id = p_catalog_product_id
          and link.status = 'approved'
          and link.deleted_at is null
          and food.deleted_at is null
          and link.catalog_product_revision ~ '^sha256:[0-9a-f]{64}$'
    ) item;

    select coalesce(jsonb_agg(item.document order by item.created_at desc, item.id), '[]'::jsonb)
    into v_pending
    from (
        select
            link.id,
            link.created_at,
            jsonb_build_object(
                'schemaVersion', 'product-nutrition-link-proposal.v1',
                'id', link.id,
                'action', 'link',
                'identity', jsonb_build_object(
                    'namespace', p_namespace,
                    'catalogProductId', link.catalog_product_id,
                    'nutritionFoodId', link.nutrition_food_id
                ),
                'status', 'pending',
                'sourceRevision', link.proposal_source_revision,
                'createdAt', link.created_at
            ) as document
        from public.product_nutrition_links link
        where link.owner_id = v_user_id
          and link.catalog_product_id = p_catalog_product_id
          and link.status = 'suggested'
          and link.deleted_at is null
          and link.proposal_source_revision ~ '^sha256:[0-9a-f]{64}$'

        union all

        select
            proposal.id,
            proposal.created_at,
            jsonb_build_object(
                'schemaVersion', 'product-nutrition-link-proposal.v1',
                'id', proposal.id,
                'action', 'unlink',
                'identity', jsonb_build_object(
                    'namespace', p_namespace,
                    'catalogProductId', proposal.catalog_product_id,
                    'nutritionFoodId', proposal.nutrition_food_id
                ),
                'status', 'pending',
                'sourceRevision', proposal.source_revision,
                'createdAt', proposal.created_at
            ) as document
        from public.product_nutrition_link_unlink_proposals proposal
        where proposal.owner_id = v_user_id
          and proposal.catalog_product_id = p_catalog_product_id
          and proposal.status = 'pending'
    ) item;

    v_payload := jsonb_build_object(
        'schemaVersion', 'product-nutrition-link-state.v1',
        'namespace', p_namespace,
        'catalogProductId', p_catalog_product_id,
        'approvedLinks', v_approved,
        'pendingProposals', v_pending
    );
    v_revision := 'sha256:' || encode(
        extensions.digest(convert_to(v_payload::text, 'UTF8'), 'sha256'),
        'hex'
    );
    return v_payload || jsonb_build_object('revision', v_revision);
end;
$$;

revoke all on function public.get_product_nutrition_link_state_v1(text, uuid) from public;
grant execute on function public.get_product_nutrition_link_state_v1(text, uuid) to authenticated;

create or replace function public.propose_product_nutrition_link_v1(
    p_action text,
    p_namespace text,
    p_catalog_product_id uuid,
    p_nutrition_food_id text,
    p_source_revision text,
    p_source jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id text := (select auth.uid())::text;
    v_id uuid;
    v_created_at timestamptz;
begin
    if v_user_id is null then
        raise exception 'Authentication is required.' using errcode = '42501';
    end if;
    if p_action not in ('link', 'unlink')
       or p_namespace <> 'pricetrace'
       or p_catalog_product_id is null
       or nullif(btrim(p_nutrition_food_id), '') is null
       or coalesce(p_source_revision, '') !~ '^sha256:[0-9a-f]{64}$'
       or p_source is null
       or coalesce(p_source->>'catalogProductId', '') <> p_catalog_product_id::text
       or coalesce(p_source->>'productRevision', '') <> p_source_revision
    then
        raise exception 'A valid Nutrition link proposal is required.' using errcode = '22023';
    end if;

    if not exists (
        select 1
        from public.nutrition_foods food
        where food.id = p_nutrition_food_id
          and food.deleted_at is null
          and (food.visibility = 'public' or food.owner_id = v_user_id)
    ) then
        raise exception 'The Nutrition row is not available to this user.' using errcode = '42501';
    end if;

    if p_action = 'link' then
        select link.id, link.created_at
        into v_id, v_created_at
        from public.product_nutrition_links link
        where link.owner_id = v_user_id
          and link.nutrition_food_id = p_nutrition_food_id
          and link.catalog_product_id = p_catalog_product_id
          and link.status = 'suggested'
          and link.deleted_at is null
        order by link.created_at desc
        limit 1;

        if v_id is null then
            insert into public.product_nutrition_links (
                owner_id,
                nutrition_food_id,
                catalog_product_id,
                status,
                source_type,
                proposal_reference,
                proposal_source_revision,
                product_contract_version
            ) values (
                v_user_id,
                p_nutrition_food_id,
                p_catalog_product_id,
                'suggested',
                'pricetrace_suggestion',
                'PriceTrace',
                p_source_revision,
                'product-read.v1'
            )
            returning id, created_at into v_id, v_created_at;
        end if;
    else
        select proposal.id, proposal.created_at
        into v_id, v_created_at
        from public.product_nutrition_link_unlink_proposals proposal
        where proposal.owner_id = v_user_id
          and proposal.nutrition_food_id = p_nutrition_food_id
          and proposal.catalog_product_id = p_catalog_product_id
          and proposal.status = 'pending'
        limit 1;

        if v_id is null then
            insert into public.product_nutrition_link_unlink_proposals (
                owner_id,
                nutrition_food_id,
                catalog_product_id,
                source_revision,
                source
            ) values (
                v_user_id,
                p_nutrition_food_id,
                p_catalog_product_id,
                p_source_revision,
                p_source
            )
            returning id, created_at into v_id, v_created_at;
        end if;
    end if;

    return jsonb_build_object(
        'schemaVersion', 'product-nutrition-link-proposal.v1',
        'id', v_id,
        'action', p_action,
        'identity', jsonb_build_object(
            'namespace', p_namespace,
            'catalogProductId', p_catalog_product_id,
            'nutritionFoodId', p_nutrition_food_id
        ),
        'status', 'pending',
        'sourceRevision', p_source_revision,
        'createdAt', v_created_at
    );
end;
$$;

revoke all on function public.propose_product_nutrition_link_v1(
    text, text, uuid, text, text, jsonb
) from public;
grant execute on function public.propose_product_nutrition_link_v1(
    text, text, uuid, text, text, jsonb
) to authenticated;

-- Re-apply the read grants required by the v1/v2 Nutrition projections.
grant execute on function public.get_nutrition_read_v1(text) to anon, authenticated;
grant execute on function public.get_nutrition_read_v2(text) to anon, authenticated;

-- Public Nutrition reads may expose only approved links whose Nutrition row is
-- already public. The table remains otherwise private to the owning user.
grant select on public.product_nutrition_links to anon, authenticated;

drop policy if exists product_nutrition_links_public_select
    on public.product_nutrition_links;
create policy product_nutrition_links_public_select
    on public.product_nutrition_links
    for select
    to anon
    using (
        status = 'approved'
        and deleted_at is null
        and exists (
            select 1
            from public.nutrition_foods food
            where food.id = nutrition_food_id
              and food.visibility = 'public'
              and food.deleted_at is null
        )
    );
