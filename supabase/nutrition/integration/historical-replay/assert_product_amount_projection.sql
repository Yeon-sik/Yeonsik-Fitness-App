-- Full-history replay smoke checks, after all existing import/rollback tests.
do $$
begin
    if not exists(select 1 from pg_class where oid='public.product_nutrition_read_specs'::regclass and relrowsecurity) then
        raise exception 'Read spec RLS disabled.';
    end if;
    if has_table_privilege('anon','public.product_nutrition_read_specs','SELECT')
       or has_table_privilege('authenticated','public.product_nutrition_read_specs','INSERT')
       or has_function_privilege('anon','public.register_product_nutrition_read_spec_v1(uuid,jsonb)','EXECUTE')
       or has_function_privilege('anon','public._get_public_product_nutrition_source_v1(text,uuid)','EXECUTE') then
        raise exception 'Internal read spec/source access exposed.';
    end if;
    if not has_function_privilege('anon','public.get_public_product_nutrition_v1(text,uuid)','EXECUTE')
       or not has_function_privilege('authenticated','public.get_public_product_nutrition_v1(text,uuid)','EXECUTE') then
        raise exception 'Public nutrition read compatibility lost.';
    end if;
    if public.nutrition_product_basis_matches_v1(210,'g',130,'g') then
        raise exception 'Strict publication basis check weakened.';
    end if;
    if abs(public.nutrition_read_scale_factor_v1(210,'g',130,'g')*315-195)>0.000000001 then
        raise exception 'Read-time 130g projection is incorrect.';
    end if;
end;
$$;
