-- Repair the existing Kaguri link from the exact PriceTrace product-read.v1
-- response. The row is made private before its approved-link metadata changes,
-- then published again through the same audited state boundary.

begin;

do $$
declare
    v_link public.product_nutrition_links%rowtype;
    v_food public.nutrition_foods%rowtype;
    v_now timestamptz := now();
    v_snapshot jsonb;
begin
    select link.*
    into v_link
    from public.product_nutrition_links link
    where link.catalog_product_id = '96eed0f6-1cfa-401a-850f-670d71c44d6f'::uuid
      and link.status = 'approved'
      and link.deleted_at is null
    order by link.updated_at desc
    limit 1
    for update;

    if not found then
        raise exception 'The existing Kaguri approved link was not found.';
    end if;

    select food.*
    into v_food
    from public.nutrition_foods food
    where food.id = v_link.nutrition_food_id
      and food.deleted_at is null
    for update;

    if not found then
        raise exception 'The Nutrition row for the existing Kaguri link was not found.';
    end if;

    update public.nutrition_foods
    set visibility = 'private',
        publication_revision = publication_revision + 1,
        published_at = null,
        published_by = null,
        updated_at = v_now
    where id = v_food.id;

    v_snapshot := jsonb_build_object(
        'contract_version', 'nutrition-read.v1',
        'nutrition_food_id', v_food.id,
        'name', v_food.name,
        'kind', v_food.kind,
        'basis_amount', v_food.basis_amount,
        'basis_unit', v_food.basis_unit,
        'prep_state', v_food.prep_state,
        'source_type', v_food.source_type,
        'source_reference', v_food.source_reference,
        'source_revision', v_food.source_version,
        'revision', v_food.revision,
        'catalog_product_id', v_link.catalog_product_id,
        'catalog_product_revision', v_link.catalog_product_revision,
        'catalog_content_amount', v_link.catalog_content_amount,
        'catalog_content_unit', v_link.catalog_content_unit,
        'catalog_package_count', v_link.catalog_package_count
    );

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
        v_link.owner_id,
        v_link.catalog_product_id,
        'unpublish',
        v_food.revision,
        v_food.publication_revision,
        v_snapshot
    );

    update public.product_nutrition_links
    set catalog_product_revision = 'sha256:4a0e24d2150802a2a85d228f35104e56ec5ff75c3366f1e17414f63963a6864b',
        catalog_content_amount = 103,
        catalog_content_unit = 'g',
        catalog_package_count = 1,
        updated_at = v_now
    where id = v_link.id;

    update public.nutrition_foods
    set visibility = 'public',
        publication_revision = publication_revision + 1,
        published_at = v_now,
        published_by = v_food.owner_id,
        updated_at = v_now
    where id = v_food.id;

    select jsonb_build_object(
        'contract_version', 'nutrition-read.v1',
        'nutrition_food_id', food.id,
        'name', food.name,
        'kind', food.kind,
        'basis_amount', food.basis_amount,
        'basis_unit', food.basis_unit,
        'prep_state', food.prep_state,
        'source_type', food.source_type,
        'source_reference', food.source_reference,
        'source_revision', food.source_version,
        'revision', food.revision,
        'catalog_product_id', link.catalog_product_id,
        'catalog_product_revision', link.catalog_product_revision,
        'catalog_content_amount', link.catalog_content_amount,
        'catalog_content_unit', link.catalog_content_unit,
        'catalog_package_count', link.catalog_package_count
    )
    into v_snapshot
    from public.nutrition_foods food
    join public.product_nutrition_links link
      on link.nutrition_food_id = food.id
    where food.id = v_food.id
      and link.id = v_link.id;

    insert into public.nutrition_food_publication_events (
        nutrition_food_id,
        product_nutrition_link_id,
        owner_id,
        catalog_product_id,
        action,
        food_revision,
        publication_revision,
        nutrition_snapshot
    )
    select
        food.id,
        link.id,
        link.owner_id,
        link.catalog_product_id,
        'publish',
        food.revision,
        food.publication_revision,
        v_snapshot
    from public.nutrition_foods food
    join public.product_nutrition_links link
      on link.nutrition_food_id = food.id
    where food.id = v_food.id
      and link.id = v_link.id;
end;
$$;

commit;
