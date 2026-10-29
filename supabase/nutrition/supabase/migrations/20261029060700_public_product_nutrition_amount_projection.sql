-- Read-time projection only: keep Nutrition masters, Meal snapshots, approved
-- links and the strict publication/basis guards unchanged. No data backfill.
-- Exact catalog links authorize composition reuse; names/families do not.

create table public.product_nutrition_read_specs (
    id bigint generated always as identity primary key,
    link_id uuid not null references public.product_nutrition_links(id) on delete cascade,
    food_revision integer not null check (food_revision > 0),
    link_revision integer not null check (link_revision > 0),
    standard_product_id uuid not null,
    catalog_product_id uuid not null,
    product_revision text not null check (product_revision ~ '^sha256:[a-f0-9]{64}$'),
    content_amount numeric not null check (content_amount > 0 and content_amount::text not in ('NaN', 'Infinity', '-Infinity')),
    content_unit text not null check (content_unit in ('g', 'ml', 'each')),
    package_count integer not null check (package_count > 0),
    verified_at timestamptz not null default now(),
    unique (link_id, food_revision, link_revision, product_revision)
);
comment on table public.product_nutrition_read_specs is
    'Trusted PriceTrace product-read.v1 read snapshots. Immutable through the API; cascade removal never blocks source deletion. Not food or link mutations.';
alter table public.product_nutrition_read_specs enable row level security;
revoke all on public.product_nutrition_read_specs from public, anon, authenticated, service_role;
grant select on public.product_nutrition_read_specs to service_role;
revoke all on sequence public.product_nutrition_read_specs_id_seq from public, anon, authenticated, service_role;

create function public.nutrition_read_scale_factor_v1(
    p_source_amount numeric, p_source_unit text, p_target_amount numeric, p_target_unit text
) returns numeric language plpgsql immutable set search_path = '' as $$
declare
    v_source text := lower(btrim(p_source_unit));
    v_target text := lower(btrim(p_target_unit));
    v_source_dimension text; v_target_dimension text;
    v_source_factor numeric; v_target_factor numeric;
begin
    if p_source_amount is null or p_target_amount is null
       or p_source_amount <= 0 or p_target_amount <= 0
       or p_source_amount::text in ('NaN', 'Infinity', '-Infinity')
       or p_target_amount::text in ('NaN', 'Infinity', '-Infinity') then return null; end if;
    -- No density, serving-size, or package-mass guesses. Count only converts to count.
    select x.dimension, x.factor into v_source_dimension, v_source_factor
    from (values
        ('g','mass',1::numeric),('gram','mass',1),('grams','mass',1),
        ('mg','mass',0.001),('milligram','mass',0.001),('milligrams','mass',0.001),
        ('kg','mass',1000),('kilogram','mass',1000),('kilograms','mass',1000),
        ('ml','volume',1),('milliliter','volume',1),('milliliters','volume',1),
        ('millilitre','volume',1),('millilitres','volume',1),
        ('l','volume',1000),('liter','volume',1000),('liters','volume',1000),
        ('litre','volume',1000),('litres','volume',1000),
        ('each','count',1),('piece','count',1),('pieces','count',1),('개','count',1)
    ) x(unit,dimension,factor) where x.unit = v_source;
    -- Catalog units are canonical g/ml/each, never a caller's free text.
    select x.dimension, x.factor into v_target_dimension, v_target_factor
    from (values ('g','mass',1::numeric),('ml','volume',1),('each','count',1))
        x(unit,dimension,factor) where x.unit = v_target;
    if v_source_dimension is null or v_target_dimension is null
       or v_source_dimension <> v_target_dimension then return null; end if;
    return p_target_amount * v_target_factor / (p_source_amount * v_source_factor);
exception when numeric_value_out_of_range or division_by_zero then return null;
end;
$$;

create function public.nutrition_scale_read_values_v1(p_values jsonb, p_factor numeric, p_nested boolean default false)
returns jsonb language plpgsql immutable set search_path = '' as $$
declare v_entry record; v_value jsonb; v_scaled numeric; v_result jsonb := '{}'::jsonb;
begin
    if jsonb_typeof(p_values) is distinct from 'object' or p_factor is null or p_factor <= 0
       or p_factor::text in ('NaN', 'Infinity', '-Infinity') then return null; end if;
    for v_entry in select key, value from jsonb_each(p_values) loop
        v_value := case when p_nested then v_entry.value->'amount' else v_entry.value end;
        if not p_nested and v_value = 'null'::jsonb then
            v_result := v_result || jsonb_build_object(v_entry.key, null);
            continue;
        end if;
        if jsonb_typeof(v_value) is distinct from 'number'
           or (v_value #>> '{}')::numeric < 0 then return null; end if;
        v_scaled := (v_value #>> '{}')::numeric * p_factor;
        if p_nested then
            if jsonb_typeof(v_entry.value->'unit') is distinct from 'string'
               or nullif(btrim(v_entry.value->>'unit'), '') is null then return null; end if;
            v_result := v_result || jsonb_build_object(v_entry.key,
                jsonb_build_object('amount', v_scaled, 'unit', v_entry.value->>'unit'));
        else
            v_result := v_result || jsonb_build_object(v_entry.key, v_scaled);
        end if;
    end loop;
    return v_result;
exception when numeric_value_out_of_range then return null;
end;
$$;
revoke all on function public.nutrition_read_scale_factor_v1(numeric,text,numeric,text) from public, anon, authenticated;
revoke all on function public.nutrition_scale_read_values_v1(jsonb,numeric,boolean) from public, anon, authenticated;
grant execute on function public.nutrition_read_scale_factor_v1(numeric,text,numeric,text) to service_role;
grant execute on function public.nutrition_scale_read_values_v1(jsonb,numeric,boolean) to service_role;

-- Preserve the exact existing publication filter/query as a private source reader.
alter function public.get_public_product_nutrition_v1(text,uuid) rename to _get_public_product_nutrition_source_v1;
revoke all on function public._get_public_product_nutrition_source_v1(text,uuid) from public, anon, authenticated;
grant execute on function public._get_public_product_nutrition_source_v1(text,uuid) to service_role;

create function public.register_product_nutrition_read_spec_v1(p_catalog_product_id uuid, p_product_read jsonb)
returns bigint language plpgsql security definer set search_path = '' as $$
declare
    v_product jsonb; v_catalog jsonb; v_link public.product_nutrition_links;
    v_food_revision integer; v_standard uuid; v_revision text; v_amount numeric; v_unit text; v_count integer;
    v_existing public.product_nutrition_read_specs; v_id bigint;
begin
    if (select auth.role()) is distinct from 'service_role' then
        raise exception 'Trusted server verification required.' using errcode = '42501';
    end if;
    -- Caller is the trusted server which fetched this exact public PT document.
    -- Neither anon nor a normal signed-in owner may supply arbitrary target sizes.
    if p_product_read->>'schemaVersion' is distinct from 'product-read.v1'
       or p_product_read->>'namespace' is distinct from 'pricetrace'
       or jsonb_typeof(p_product_read->'products') is distinct from 'array' then
        raise exception 'Invalid PriceTrace contract.' using errcode = '23514';
    end if;
    if jsonb_array_length(p_product_read->'products') <> 1 then
        raise exception 'Exactly one PriceTrace product required.' using errcode = '23514';
    end if;
    v_product := p_product_read->'products'->0; v_catalog := v_product->'catalogProduct';
    if v_catalog->>'id' is distinct from p_catalog_product_id::text
       or jsonb_typeof(v_catalog->'contentAmount') is distinct from 'number'
       or jsonb_typeof(v_catalog->'packageCount') is distinct from 'number' then
        raise exception 'Exact catalog identity and numeric specification required.' using errcode = '23514';
    end if;
    v_standard := (v_product->'standardProduct'->>'id')::uuid;
    v_revision := v_product->>'revision'; v_amount := (v_catalog->>'contentAmount')::numeric;
    v_unit := v_catalog->>'contentUnit';
    if (v_catalog->>'packageCount')::numeric <> trunc((v_catalog->>'packageCount')::numeric) then
        raise exception 'Integer package count required.' using errcode = '23514';
    end if;
    v_count := (v_catalog->>'packageCount')::integer;
    if v_standard is null or v_revision is null or v_revision !~ '^sha256:[a-f0-9]{64}$'
       or v_amount <= 0 or v_amount::text in ('NaN','Infinity','-Infinity')
       or v_unit is null or v_unit not in ('g','ml','each') or v_count <= 0 then
        raise exception 'Invalid PriceTrace specification.' using errcode = '23514';
    end if;
    select link.* into v_link
    from public.product_nutrition_links link join public.nutrition_foods food on food.id = link.nutrition_food_id
    join public._get_public_product_nutrition_source_v1('pricetrace',p_catalog_product_id) source
        on source.nutrition_food_id = food.id
    where link.catalog_product_id = p_catalog_product_id and link.status = 'approved'
      and link.deleted_at is null and food.visibility = 'public' and food.published_at is not null and food.deleted_at is null
    order by food.published_at desc, link.reviewed_at desc, link.created_at desc limit 1
    for share of link, food;
    if v_link.id is null or v_link.standard_product_id is distinct from v_standard then
        raise exception 'No approved exact public product link.' using errcode = '23514';
    end if;
    select food.revision into strict v_food_revision from public.nutrition_foods food where food.id=v_link.nutrition_food_id;
    if (v_link.catalog_product_revision is not null or v_link.catalog_content_amount is not null
        or v_link.catalog_content_unit is not null or v_link.catalog_package_count is not null) and (
        v_link.catalog_product_revision is distinct from v_revision
        or v_link.catalog_content_amount is distinct from v_amount
        or v_link.catalog_content_unit is distinct from v_unit
        or v_link.catalog_package_count is distinct from v_count) then
        raise exception 'Pinned link snapshot differs; review the product link first.' using errcode = '23514';
    end if;
    insert into public.product_nutrition_read_specs(link_id,food_revision,link_revision,standard_product_id,
        catalog_product_id,product_revision,content_amount,content_unit,package_count)
    values(v_link.id,v_food_revision,v_link.revision,v_standard,p_catalog_product_id,v_revision,v_amount,v_unit,v_count)
    on conflict (link_id,food_revision,link_revision,product_revision) do nothing returning id into v_id;
    if v_id is null then
        select * into strict v_existing from public.product_nutrition_read_specs
        where link_id=v_link.id and food_revision=v_food_revision and link_revision=v_link.revision and product_revision=v_revision;
        if row(v_existing.standard_product_id,v_existing.catalog_product_id,v_existing.content_amount,v_existing.content_unit,v_existing.package_count)
           is distinct from row(v_standard,p_catalog_product_id,v_amount,v_unit,v_count) then
            raise exception 'Conflicting specification for the same product revision.' using errcode = '23514';
        end if;
        v_id := v_existing.id;
    end if;
    return v_id;
end;
$$;
revoke all on function public.register_product_nutrition_read_spec_v1(uuid,jsonb) from public, anon, authenticated;
grant execute on function public.register_product_nutrition_read_spec_v1(uuid,jsonb) to service_role;

create function public.get_public_product_nutrition_v1(p_namespace text, p_catalog_product_id uuid)
returns table (
    contract_version text,nutrition_food_id text,name text,kind text,basis_amount numeric,basis_unit text,prep_state text,
    nutrition_values jsonb,micronutrients jsonb,source_type text,source_reference text,source_revision text,revision integer,
    catalog_product_id uuid,catalog_product_revision text,catalog_content_amount numeric,catalog_content_unit text,catalog_package_count integer
) language sql stable security definer set search_path = '' as $$
    with source as (
        select * from public._get_public_product_nutrition_source_v1(p_namespace,p_catalog_product_id)
    ), target as (
        select source.*, coalesce(source.catalog_content_amount,spec.content_amount) as target_amount,
            coalesce(source.catalog_content_unit,spec.content_unit) as target_unit,
            coalesce(source.catalog_product_revision,spec.product_revision) as target_revision,
            coalesce(source.catalog_package_count,spec.package_count) as target_count, spec.verified_at as spec_verified_at
        from source left join lateral (
            select snapshot.* from public.product_nutrition_read_specs snapshot
            join public.product_nutrition_links link on link.id=snapshot.link_id
            where link.catalog_product_id=source.catalog_product_id and link.nutrition_food_id=source.nutrition_food_id
              and link.status='approved' and link.deleted_at is null
              and snapshot.catalog_product_id=source.catalog_product_id
              and snapshot.food_revision=source.revision and snapshot.link_revision=link.revision
            order by snapshot.verified_at desc,snapshot.id desc limit 1
        ) spec on source.catalog_content_amount is null
    ), factor as (
        select target.*, case when target_amount is null then 1::numeric
            else public.nutrition_read_scale_factor_v1(basis_amount,basis_unit,target_amount,target_unit) end as scale_factor
        from target
    ), projected as (
        select factor.*, public.nutrition_scale_read_values_v1(nutrition_values,scale_factor,false) as scaled_values,
            public.nutrition_scale_read_values_v1(micronutrients,scale_factor,true) as scaled_micro,
            target_amount is not null and (basis_amount is distinct from target_amount or basis_unit is distinct from target_unit) as changed
        from factor
    )
    select contract_version,nutrition_food_id,name,kind,coalesce(target_amount,basis_amount),coalesce(target_unit,basis_unit),prep_state,
        scaled_values,scaled_micro,
        case when changed and scale_factor<>1 then 'calculated_product_amount'
             when changed then 'unit_converted_product_amount' else source_type end,
        case when changed then jsonb_build_object(
            'contractVersion','nutrition-product-amount.v1','kind',case when scale_factor<>1 then 'proportional_estimate' else 'unit_conversion' end,
            'isEstimate',scale_factor<>1,'notice',case when scale_factor<>1 then '용량 비례 추정치 · 해당 포장의 영양표와 다를 수 있음' else null end,
            'sourceFoodId',nutrition_food_id,'sourceFoodRevision',revision,'sourceType',source_type,'sourceRevision',source_revision,
            'sourceBasis',jsonb_build_object('amount',basis_amount,'unit',basis_unit),'sourceNutritionValues',nutrition_values,
            'originalSourceReference',case when octet_length(source_reference)<=4096 then source_reference else null end,
            'originalSourceReferenceMd5',md5(source_reference),'factor',scale_factor,
            'targetBasis',jsonb_build_object('amount',target_amount,'unit',target_unit,'scope','per_package'),
            'catalogProductRevision',target_revision,'specVerifiedAt',spec_verified_at
        )::text else source_reference end,
        case when changed then 'nutrition-product-amount.v1:'||md5(concat_ws('|',nutrition_food_id,revision::text,source_revision,
            target_revision,target_amount::text,target_unit,target_count::text)) else source_revision end,
        revision,catalog_product_id,target_revision,target_amount,target_unit,target_count
    from projected where scale_factor is not null and scaled_values is not null and scaled_micro is not null;
$$;
comment on function public.get_public_product_nutrition_v1(text,uuid) is
    'nutrition-read.v1; approved exact product nutrition at trusted per-package catalog amount. Proportional results are estimates with original basis/values and factor in source_reference. Never mutates source foods or Meal history.';
revoke all on function public.get_public_product_nutrition_v1(text,uuid) from public;
grant execute on function public.get_public_product_nutrition_v1(text,uuid) to anon,authenticated,service_role;
notify pgrst, 'reload schema';
