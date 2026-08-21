-- Connect an explicitly published FitnessApp dining-out menu to the public
-- Nutrition product link projection consumed by PriceTrace.

create or replace function public.attach_dining_out_menu_nutrition_link_v1(
    p_nutrition_food_id text,
    p_catalog_product_id uuid
)
returns table (
    link_id uuid,
    nutrition_food_id text,
    catalog_product_id uuid,
    status text,
    revision integer
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id text := (select auth.uid())::text;
    v_food public.nutrition_foods%rowtype;
    v_identity jsonb;
    v_now timestamptz := now();
    v_existing_id uuid;
    v_existing_deleted_at timestamptz;
begin
    if v_user_id is null then
        raise exception 'Authentication is required.' using errcode = '42501';
    end if;

    if p_catalog_product_id is null then
        raise exception 'An exact catalog_product_id is required.' using errcode = '23514';
    end if;

    select food.*
    into v_food
    from public.nutrition_foods as food
    where food.id = p_nutrition_food_id
      and food.owner_id = v_user_id
      and food.kind = 'external_menu'
      and food.source_type = 'manual_estimate'
      and food.deleted_at is null
    for update;

    if not found then
        raise exception 'The owner dining-out menu was not found.' using errcode = 'P0002';
    end if;

    begin
        v_identity := coalesce(nullif(btrim(v_food.source_reference), '')::jsonb, '{}'::jsonb);
    exception when others then
        v_identity := '{}'::jsonb;
    end;

    if coalesce(v_identity ->> 'schema_version', '') <> 'dining-out-identity.v1'
       or coalesce(v_identity ->> 'namespace', '') <> 'pricetrace'
       or coalesce(v_identity ->> 'catalog_product_id', '') <> p_catalog_product_id::text
    then
        raise exception 'The exact PriceTrace dining-out identity is required.' using errcode = '23514';
    end if;

    select link.id, link.deleted_at
    into v_existing_id, v_existing_deleted_at
    from public.product_nutrition_links as link
    where link.owner_id = v_user_id
      and link.nutrition_food_id = p_nutrition_food_id
      and link.catalog_product_id = p_catalog_product_id
      and link.status = 'approved'
    order by link.created_at desc
    limit 1;

    if v_existing_id is not null and v_existing_deleted_at is null then
        return query
        select link.id, link.nutrition_food_id, link.catalog_product_id, link.status, link.revision
        from public.product_nutrition_links as link
        where link.id = v_existing_id;
        return;
    end if;

    -- A menu publication is an explicit owner action. Keep at most one active
    -- approved catalog identity for this Nutrition food before it becomes public.
    update public.product_nutrition_links as link
    set deleted_at = v_now,
        updated_at = v_now
    where link.owner_id = v_user_id
      and link.nutrition_food_id = p_nutrition_food_id
      and link.status = 'approved'
      and link.deleted_at is null
      and link.catalog_product_id <> p_catalog_product_id;

    if v_existing_id is not null then
        update public.product_nutrition_links as link
        set deleted_at = null,
            reviewed_at = v_now,
            updated_at = v_now
        where link.id = v_existing_id
        returning link.id, link.nutrition_food_id, link.catalog_product_id, link.status, link.revision
        into link_id, nutrition_food_id, catalog_product_id, status, revision;
        return next;
        return;
    end if;

    return query
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
        v_user_id,
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
    )
    returning id, nutrition_food_id, catalog_product_id, status, revision;
end;
$$;

revoke all on function public.attach_dining_out_menu_nutrition_link_v1(text, uuid)
    from public, anon;
grant execute on function public.attach_dining_out_menu_nutrition_link_v1(text, uuid)
    to authenticated;

comment on function public.attach_dining_out_menu_nutrition_link_v1(text, uuid) is
    'Owner-only exact bridge from a published FitnessApp dining-out Nutrition food to the PT catalog product used by the public Nutrition read RPC.';

-- Repair menus published by the earlier publication migration. Only the latest
-- publication event is eligible, and existing active approved links are left
-- untouched to avoid changing a user-selected product identity.
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
)
select
    event.owner_id,
    event.nutrition_food_id,
    event.catalog_product_id,
    'approved',
    'manual_selection',
    'FitnessApp dining-out publication',
    'product-read.v1',
    1,
    now(),
    now(),
    now(),
    null
from (
    select distinct on (publication.nutrition_food_id)
        publication.owner_id,
        publication.nutrition_food_id,
        publication.catalog_product_id,
        publication.action,
        publication.created_at
    from public.nutrition_dining_out_publication_events as publication
    order by publication.nutrition_food_id, publication.created_at desc, publication.id desc
) as event
join public.nutrition_foods as food
  on food.id = event.nutrition_food_id
 and food.owner_id = event.owner_id
where event.action = 'publish'
  and event.catalog_product_id is not null
  and food.visibility = 'public'
  and food.deleted_at is null
  and not exists (
      select 1
      from public.product_nutrition_links as existing
      where existing.owner_id = event.owner_id
        and existing.nutrition_food_id = event.nutrition_food_id
        and existing.status = 'approved'
        and existing.deleted_at is null
  );
