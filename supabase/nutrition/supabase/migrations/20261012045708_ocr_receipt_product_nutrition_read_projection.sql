-- Reuse the Fitness read contract so source micronutrients are never omitted.
create or replace function public.get_verified_receipt_product_nutrition_v1(p_store_product_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare v_owner text := auth.uid()::text; v_result jsonb;
begin
  if v_owner is null then raise exception 'authenticated owner required' using errcode = '42501'; end if;
  select coalesce(jsonb_agg(public.nutrition_read_v1_row(f, null::uuid) order by f.updated_at desc),'[]'::jsonb)
    into v_result
    from public.verified_receipt_product_nutrition_links l join public.nutrition_foods f on f.id=l.nutrition_food_id
    where l.owner_id=v_owner and l.store_product_id=p_store_product_id and f.owner_id=v_owner and f.deleted_at is null;
  return v_result;
end $$;
revoke all on function public.get_verified_receipt_product_nutrition_v1(uuid) from public, anon;
grant execute on function public.get_verified_receipt_product_nutrition_v1(uuid) to authenticated;
