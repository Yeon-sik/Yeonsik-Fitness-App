create schema nutrition_v3_fix_replay;

create table nutrition_v3_fix_replay.public_columns_before_fix as
select
    relation.relname as table_name,
    attribute.attname as column_name,
    attribute.attnum as ordinal_position,
    pg_catalog.format_type(attribute.atttypid, attribute.atttypmod) as data_type,
    attribute.attnotnull as is_not_null,
    pg_catalog.pg_get_expr(default_value.adbin, default_value.adrelid) as default_expression
from pg_catalog.pg_class as relation
join pg_catalog.pg_namespace as namespace
  on namespace.oid = relation.relnamespace
join pg_catalog.pg_attribute as attribute
  on attribute.attrelid = relation.oid
left join pg_catalog.pg_attrdef as default_value
  on default_value.adrelid = relation.oid
 and default_value.adnum = attribute.attnum
where namespace.nspname = 'public'
  and relation.relkind in ('r', 'p')
  and attribute.attnum > 0
  and not attribute.attisdropped;

create table nutrition_v3_fix_replay.public_constraints_before_fix as
select
    relation.relname as table_name,
    constraint_row.conname as constraint_name,
    constraint_row.contype as constraint_type,
    constraint_row.convalidated as is_validated,
    pg_catalog.pg_get_constraintdef(constraint_row.oid, true) as definition
from pg_catalog.pg_constraint as constraint_row
join pg_catalog.pg_class as relation
  on relation.oid = constraint_row.conrelid
join pg_catalog.pg_namespace as namespace
  on namespace.oid = relation.relnamespace
where namespace.nspname = 'public';

create table nutrition_v3_fix_replay.public_functions_before_fix as
select
    procedure.oid as function_oid,
    procedure.proname as function_name,
    pg_catalog.pg_get_functiondef(procedure.oid) as definition,
    procedure.proacl::text as privileges
from pg_catalog.pg_proc as procedure
join pg_catalog.pg_namespace as namespace
  on namespace.oid = procedure.pronamespace
where namespace.nspname = 'public'
  and procedure.prokind in ('f', 'p');

create table nutrition_v3_fix_replay.public_policies_before_fix as
select
    policy.schemaname,
    policy.tablename,
    policy.policyname,
    policy.permissive,
    policy.roles::text as roles,
    policy.cmd,
    policy.qual,
    policy.with_check
from pg_catalog.pg_policies as policy
where policy.schemaname = 'public';

create table nutrition_v3_fix_replay.public_rls_before_fix as
select
    relation.relname as table_name,
    relation.relrowsecurity as rls_enabled,
    relation.relforcerowsecurity as rls_forced
from pg_catalog.pg_class as relation
join pg_catalog.pg_namespace as namespace
  on namespace.oid = relation.relnamespace
where namespace.nspname = 'public'
  and relation.relkind in ('r', 'p');

select 'PASS pre-fix public columns, constraints, RPC grants, policies, and RLS snapshot' as result;
