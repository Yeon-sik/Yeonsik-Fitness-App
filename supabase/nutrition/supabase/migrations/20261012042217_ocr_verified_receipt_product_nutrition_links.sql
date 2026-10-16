-- OCR review establishes the source product and nutrition facts. Standard-product
-- classification is a separate, optional action and is not a prerequisite here.
create table public.verified_receipt_product_nutrition_links (
  id uuid primary key default gen_random_uuid(),
  owner_id text not null,
  canonical_import_id uuid not null references public.nutrition_canonical_imports(id),
  nutrition_food_id text not null references public.nutrition_foods(id),
  store_product_id uuid not null,
  product_id uuid not null,
  receipt_id uuid not null,
  source_line_id text not null check (length(source_line_id) between 1 and 200),
  identity jsonb not null check (jsonb_typeof(identity) = 'object'),
  created_at timestamptz not null default now(),
  unique (owner_id, store_product_id, nutrition_food_id)
);
alter table public.verified_receipt_product_nutrition_links enable row level security;
revoke all on public.verified_receipt_product_nutrition_links from public, anon, authenticated;
grant select on public.verified_receipt_product_nutrition_links to authenticated;
create policy verified_receipt_product_nutrition_owner_read
  on public.verified_receipt_product_nutrition_links for select to authenticated
  using (owner_id = (select auth.uid())::text);

create function public.attach_verified_receipt_product_nutrition_v1(
  p_canonical_import_id uuid, p_nutrition_food_id text,
  p_pricetrace_identity jsonb, p_user_verified boolean
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_owner text := auth.uid()::text;
  v_import public.nutrition_canonical_imports%rowtype;
  v_link public.verified_receipt_product_nutrition_links%rowtype;
  v_store_product uuid;
  v_product uuid;
  v_receipt uuid;
  v_store uuid;
  v_reference jsonb;
  v_identity jsonb := p_pricetrace_identity;
begin
  if v_owner is null then raise exception 'authenticated owner required' using errcode = '42501'; end if;
  if p_user_verified is distinct from true or jsonb_typeof(v_identity) is distinct from 'object'
    or v_identity->>'schema_version' is distinct from 'verified-receipt-product.v1'
    or v_identity->>'namespace' is distinct from 'pricetrace'
    or length(coalesce(v_identity->>'source_line_id','')) not between 1 and 200
    or length(coalesce(v_identity->>'receipt_item_id','')) not between 1 and 200
    or coalesce(v_identity->>'binding_basis','') not in ('explicit_line','single_verified_product')
    or exists(select 1 from jsonb_object_keys(v_identity) k where k not in
      ('schema_version','namespace','receipt_id','store_id','product_id','store_product_id','source_line_id','receipt_item_id','binding_basis'))
  then raise exception 'verified receipt product identity required' using errcode = '23514'; end if;
  begin
    v_store_product := (v_identity->>'store_product_id')::uuid;
    v_product := (v_identity->>'product_id')::uuid;
    v_receipt := (v_identity->>'receipt_id')::uuid;
    v_store := (v_identity->>'store_id')::uuid;
  exception when invalid_text_representation then
    raise exception 'receipt product identity UUID invalid' using errcode = '22023';
  end;
  if v_store_product is null or v_product is null or v_receipt is null or v_store is null then
    raise exception 'receipt product identity incomplete' using errcode = '23514';
  end if;
  select * into v_import from public.nutrition_canonical_imports
    where id = p_canonical_import_id and owner_id = v_owner for update;
  if not found or v_import.user_verified is distinct from true
    or v_import.nutrition_food_id is distinct from p_nutrition_food_id
    or v_import.input_contract not in ('nutrition-label.v1','external-reference.v1')
    or not exists(select 1 from public.nutrition_foods f where f.id = p_nutrition_food_id
      and f.owner_id = v_owner and f.deleted_at is null)
  then raise exception 'owner verified nutrition import required' using errcode = '42501'; end if;

  insert into public.verified_receipt_product_nutrition_links
    (owner_id,canonical_import_id,nutrition_food_id,store_product_id,product_id,receipt_id,source_line_id,identity)
    values(v_owner,p_canonical_import_id,p_nutrition_food_id,v_store_product,v_product,v_receipt,v_identity->>'source_line_id',v_identity)
    on conflict (owner_id,store_product_id,nutrition_food_id) do nothing;
  select * into v_link from public.verified_receipt_product_nutrition_links
    where owner_id = v_owner and store_product_id = v_store_product and nutrition_food_id = p_nutrition_food_id;
  if v_link.identity is distinct from v_identity or v_link.canonical_import_id is distinct from p_canonical_import_id then
    raise exception 'receipt product nutrition identity conflict' using errcode = '23514';
  end if;
  -- Preserve source facts and private visibility; attach only typed PT identity.
  select case when f.source_reference is null then '{}'::jsonb
    when left(ltrim(f.source_reference),1) = '{' then f.source_reference::jsonb
    else jsonb_build_object('source_reference',f.source_reference) end into v_reference
    from public.nutrition_foods f where f.id = p_nutrition_food_id;
  if v_reference->'receipt_product_identity' is distinct from v_identity then
    if v_reference ? 'receipt_product_identity' then
      raise exception 'nutrition source product identity conflict' using errcode = '23514';
    end if;
    update public.nutrition_foods set source_reference = (v_reference || jsonb_build_object(
      'receipt_product_identity',v_identity,'source_document_ref',v_import.source_document_ref))::text,
      revision = revision + 1, updated_at = now() where id = p_nutrition_food_id and owner_id = v_owner;
  end if;
  return jsonb_build_object('id',v_link.id,'canonical_import_id',p_canonical_import_id,
    'nutrition_food_id',p_nutrition_food_id,'store_product_id',v_store_product,'product_id',v_product,
    'source_document_ref',v_import.source_document_ref,'status','linked');
end $$;

create function public.get_verified_receipt_product_nutrition_v1(p_store_product_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare v_owner text := auth.uid()::text; v_result jsonb;
begin
  if v_owner is null then raise exception 'authenticated owner required' using errcode = '42501'; end if;
  select coalesce(jsonb_agg(jsonb_build_object(
    'contract_version','nutrition-read.v1','nutrition_food_id',f.id,'name',f.name,'kind',f.kind,
    'basis_amount',f.basis_amount,'basis_unit',f.basis_unit,'prep_state',f.prep_state,
    'nutrition_values',jsonb_build_object('calories_kcal',f.calories_kcal,'protein_grams',f.protein_grams,
      'carbs_grams',f.carbs_grams,'fat_grams',f.fat_grams,'sodium_mg',f.sodium_mg,
      'saturated_fat_grams',f.saturated_fat_grams,'sugars_grams',f.sugars_grams,'fiber_grams',f.fiber_grams,
      'added_sugars_grams',f.added_sugars_grams,'trans_fat_grams',f.trans_fat_grams,'cholesterol_mg',f.cholesterol_mg),
    'micronutrients','{}'::jsonb,'source_type',f.source_type,'source_reference',f.source_reference,
    'source_revision',f.source_version,'revision',f.revision,'catalog_product_id',null
  ) order by f.updated_at desc),'[]'::jsonb) into v_result
  from public.verified_receipt_product_nutrition_links l join public.nutrition_foods f on f.id=l.nutrition_food_id
  where l.owner_id=v_owner and l.store_product_id=p_store_product_id and f.owner_id=v_owner and f.deleted_at is null;
  return v_result;
end $$;
revoke all on function public.attach_verified_receipt_product_nutrition_v1(uuid,text,jsonb,boolean) from public, anon;
revoke all on function public.get_verified_receipt_product_nutrition_v1(uuid) from public, anon;
grant execute on function public.attach_verified_receipt_product_nutrition_v1(uuid,text,jsonb,boolean) to authenticated;
grant execute on function public.get_verified_receipt_product_nutrition_v1(uuid) to authenticated;
