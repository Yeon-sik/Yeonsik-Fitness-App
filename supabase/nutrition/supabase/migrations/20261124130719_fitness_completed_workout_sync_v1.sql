begin;

do $$
begin
  if to_regclass('public.nutrition_foods') is null or to_regclass('public.workout_records') is not null then
    raise exception 'Expected the Fitness-owned project';
  end if;
end $$;

create schema if not exists fitness_workout_private;
revoke all on schema fitness_workout_private from public, anon;
grant usage on schema fitness_workout_private to authenticated;

create function fitness_workout_private.valid_document(doc jsonb) returns boolean
language plpgsql immutable strict set search_path = '' as $$
declare
  exercise jsonb;
  performed jsonb;
  exercise_ids text[] := '{}';
  set_ids text[] := '{}';
  key text;
begin
  if jsonb_typeof(doc) <> 'object' or octet_length(doc::text) > 524288 or
    not (doc ?& array['title','startMillis','endMillis','durationSeconds','totalVolumeKg','exercises']) or
    doc - array['title','startMillis','endMillis','durationSeconds','totalVolumeKg','exercises'] <> '{}' or
    jsonb_typeof(doc->'title') <> 'string' or length(btrim(doc->>'title')) not between 1 and 1024 or
    jsonb_typeof(doc->'totalVolumeKg') <> 'number' or (doc->>'totalVolumeKg')::numeric < 0 or
    jsonb_typeof(doc->'exercises') <> 'array' then return false;
  end if;
  foreach key in array array['startMillis','endMillis','durationSeconds'] loop
    if jsonb_typeof(doc->key) not in ('null','number') then return false; end if;
    if jsonb_typeof(doc->key) = 'number' and
      ((doc->>key) !~ '^-?[0-9]+$' or (doc->>key)::numeric not between -9223372036854775808 and 9223372036854775807) then return false; end if;
  end loop;
  if (doc->>'durationSeconds')::numeric not between 0 and 2147483647 then return false; end if;
  for exercise in select value from jsonb_array_elements(doc->'exercises') loop
    if jsonb_typeof(exercise) <> 'object' or not (exercise ?& array['id','name','sets']) or
      exercise - array['id','name','sets'] <> '{}' or jsonb_typeof(exercise->'id') <> 'string' or
      length(exercise->>'id') not between 1 and 128 or exercise->>'id' = any(exercise_ids) or
      jsonb_typeof(exercise->'name') <> 'string' or length(btrim(exercise->>'name')) not between 1 and 1024 or
      jsonb_typeof(exercise->'sets') <> 'array' then return false;
    end if;
    exercise_ids := array_append(exercise_ids, exercise->>'id');
    for performed in select value from jsonb_array_elements(exercise->'sets') loop
      if jsonb_typeof(performed) <> 'object' or not (performed ? 'id') or
        performed - array['id','weightKg','reps','rir','rpe','durationSeconds','distanceMeters','loadState','setType','inputLoad','inputUnit'] <> '{}' or
        jsonb_typeof(performed->'id') <> 'string' or length(performed->>'id') not between 1 and 128 or
        performed->>'id' = any(set_ids) then return false;
      end if;
      foreach key in array array['weightKg','reps','rir','rpe','durationSeconds','distanceMeters','inputLoad'] loop
        if performed ? key and jsonb_typeof(performed->key) not in ('number','null') then return false; end if;
        if (performed->>key)::numeric < 0 then return false; end if;
      end loop;
      foreach key in array array['reps','rir','durationSeconds'] loop
        if (performed->>key) !~ '^[0-9]+$' or (performed->>key)::numeric > 2147483647 then return false; end if;
      end loop;
      foreach key in array array['loadState','setType','inputUnit'] loop
        if performed ? key and (jsonb_typeof(performed->key) not in ('string','null') or length(performed->>key) > 64) then return false; end if;
      end loop;
      if performed->>'inputUnit' not in ('kg','lb') then return false; end if;
      set_ids := array_append(set_ids, performed->>'id');
    end loop;
  end loop;
  return true;
end $$;

create table fitness_workout_private.completed_workouts (
  owner_id uuid not null references auth.users(id) on delete cascade,
  id uuid not null,
  day date not null,
  revision bigint not null check (revision > 0),
  workout jsonb check (fitness_workout_private.valid_document(workout)),
  memo text not null check (length(memo) <= 1000),
  primary key (owner_id, id)
);
create index completed_workouts_day on fitness_workout_private.completed_workouts(owner_id, day, id);
alter table fitness_workout_private.completed_workouts enable row level security;
create policy own_workouts on fitness_workout_private.completed_workouts to authenticated
using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
grant select, insert, update on fitness_workout_private.completed_workouts to authenticated;

create function public.fitness_completed_workouts_v1(p_expected_owner uuid, p_day date, p_after uuid default null)
returns jsonb language plpgsql stable security invoker set search_path = '' as $$
begin
  if auth.uid() is null or auth.uid() is distinct from p_expected_owner then
    raise exception using errcode = '42501', message = 'Account changed';
  end if;
  return (select coalesce(jsonb_agg(to_jsonb(page) order by id), '[]'::jsonb)
    from (select id, day, revision, workout, memo from fitness_workout_private.completed_workouts
      where owner_id = p_expected_owner and day = p_day and (p_after is null or id > p_after)
      order by id limit 32) page);
end $$;

create function public.fitness_save_completed_workout_v1(
  p_expected_owner uuid, p_day date, p_id uuid, p_revision bigint, p_workout jsonb, p_memo text)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare
  stored fitness_workout_private.completed_workouts;
begin
  if auth.uid() is null or auth.uid() is distinct from p_expected_owner then
    raise exception using errcode = '42501', message = 'Account changed';
  end if;
  if p_revision is null or p_revision < 0 or p_day is null or p_id is null or p_memo is null or
    (p_workout is null and p_memo <> '') then raise exception using errcode = '22023', message = 'Invalid workout'; end if;
  if p_revision = 0 and p_workout is not null then
    insert into fitness_workout_private.completed_workouts(owner_id,id,day,revision,workout,memo)
    values (p_expected_owner,p_id,p_day,1,p_workout,p_memo)
    on conflict (owner_id,id) do nothing;
  end if;
  select * into stored from fitness_workout_private.completed_workouts
    where owner_id = p_expected_owner and id = p_id for update;
  if not found or stored.day <> p_day then
    raise exception using errcode = '40001', message = 'Workout revision conflict';
  end if;
  if stored.workout is distinct from p_workout or stored.memo <> p_memo then
    if stored.revision <> p_revision or stored.workout is null then
      raise exception using errcode = '40001', message = 'Workout revision conflict';
    end if;
    update fitness_workout_private.completed_workouts set revision = revision + 1, workout = p_workout, memo = p_memo
      where owner_id = p_expected_owner and id = p_id returning * into stored;
  end if;
  return to_jsonb(stored) - 'owner_id';
end $$;

create function public.fitness_completed_workout_summary_v1(p_expected_owner uuid, p_from date, p_to date)
returns jsonb language plpgsql stable security invoker set search_path = '' as $$
begin
  if auth.uid() is null or auth.uid() is distinct from p_expected_owner then
    raise exception using errcode = '42501', message = 'Account changed';
  end if;
  if p_from is null or p_to is null or p_to - p_from not between 0 and 30 then
    raise exception using errcode = '22023', message = 'Select 1 to 31 days';
  end if;
  return (select jsonb_build_object(
    'version',1,'from',p_from,'to',p_to,'sessions',count(*),
    'completed_sets',coalesce(sum((select sum(jsonb_array_length(exercise->'sets'))
      from jsonb_array_elements(workout->'exercises') exercise)),0),
    'volume_kg',coalesce(sum((workout->>'totalVolumeKg')::numeric),0),
    'sessions_with_duration',count(workout->>'durationSeconds'),
    'known_duration_seconds',coalesce(sum((workout->>'durationSeconds')::bigint),0))
    from fitness_workout_private.completed_workouts
    where owner_id = p_expected_owner and day between p_from and p_to and workout is not null);
end $$;

revoke all on function fitness_workout_private.valid_document(jsonb),
  public.fitness_completed_workouts_v1(uuid,date,uuid),
  public.fitness_completed_workout_summary_v1(uuid,date,date),
  public.fitness_save_completed_workout_v1(uuid,date,uuid,bigint,jsonb,text) from public, anon;
grant execute on function fitness_workout_private.valid_document(jsonb),
  public.fitness_completed_workouts_v1(uuid,date,uuid),
  public.fitness_completed_workout_summary_v1(uuid,date,date),
  public.fitness_save_completed_workout_v1(uuid,date,uuid,bigint,jsonb,text) to authenticated;
commit;
