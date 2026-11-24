import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { PGlite } from '@electric-sql/pglite';

const migration = await readFile(new URL('../supabase/migrations/20261124130719_fitness_completed_workout_sync_v1.sql', import.meta.url), 'utf8');
const a = '00000000-0000-4000-8000-00000000000a';
const b = '00000000-0000-4000-8000-00000000000b';
const id = '00000000-0000-4000-8000-000000000010';
const day = '2025-01-01';
const workout = {
  title: 'Fixture', startMillis: null, endMillis: null, durationSeconds: null, totalVolumeKg: 100,
  exercises: [{ id: 'e1', name: 'Fixture exercise', sets: [{ id: 's1', weightKg: 10, reps: 10, inputLoad: 10, inputUnit: 'kg' }] }]
};

test('two devices retry, update, conflict and delete within one owner', async () => {
  const db = await PGlite.create();
  try {
    await db.exec(`
      create role anon; create role authenticated;
      create schema auth; grant usage on schema auth to authenticated;
      create table auth.users(id uuid primary key); insert into auth.users values ('${a}'), ('${b}');
      create function auth.uid() returns uuid language sql stable as
      $$select nullif(current_setting('request.jwt.claim.sub', true),'')::uuid$$;
      create table public.nutrition_foods(id text primary key);`);
    await db.exec(migration);
    const changeUser = user => db.query("select set_config('request.jwt.claim.sub',$1,false)", [user]);
    const read = async owner => (await db.query('select public.fitness_completed_workouts_v1($1,$2) as result', [owner, day])).rows[0].result;
    const summary = async (owner, from = day, to = day) => (await db.query(
      'select public.fitness_completed_workout_summary_v1($1,$2,$3) as result', [owner, from, to])).rows[0].result;
    const write = async (owner, rev, doc, memo, sessionId = id) => (await db.query(
      'select public.fitness_save_completed_workout_v1($1,$2,$3,$4,$5::jsonb,$6) as result',
      [owner, day, sessionId, rev, doc === null ? null : JSON.stringify(doc), memo])).rows[0].result;
    await db.exec('set role anon');
    await assert.rejects(read(a), { code: '42501' });
    await assert.rejects(summary(a), { code: '42501' });
    await db.exec('set role authenticated');
    await changeUser(a);
    const initial = await write(a, 0, workout, '');
    assert.deepEqual(initial, { id, day, revision: 1, workout, memo: '' });
    assert.deepEqual(await write(a, 0, workout, ''), initial);
    assert.deepEqual(await read(a), [initial]);
    assert.deepEqual(await summary(a), { version: 1, from: day, to: day, sessions: 1, completed_sets: 1,
      volume_kg: 100, sessions_with_duration: 0, known_duration_seconds: 0 });
    await assert.rejects(summary(a, day, '2025-02-01'), { code: '22023' });
    assert.equal((await write(a, 1, workout, 'iOS edit')).revision, 2);
    await assert.rejects(write(a, 1, workout, 'concurrent edit'), { code: '40001' });
    assert.equal((await read(a))[0].memo, 'iOS edit');
    await assert.rejects(write(a, 2, { ...workout, heartRateSamples: [] }, ''), { code: '23514' });
    const invalid = structuredClone(workout);
    invalid.exercises[0].sets[0].source = 'healthkit';
    await assert.rejects(write(a, 2, invalid, ''), { code: '23514' });
    await changeUser(b);
    assert.deepEqual(await read(b), []);
    await assert.rejects(read(a), { code: '42501' });
    await assert.rejects(summary(a), { code: '42501' });
    assert.equal((await summary(b)).sessions, 0);
    await assert.rejects(write(a, 2, null, ''), { code: '42501' });
    assert.deepEqual((await db.query('select * from fitness_workout_private.completed_workouts')).rows, []);
    assert.equal((await write(b, 0, workout, 'other owner')).revision, 1);
    await changeUser(a);
    assert.equal((await write(a, 2, null, '')).revision, 3);
    assert.equal((await read(a))[0].workout, null);
    assert.equal((await summary(a)).sessions, 0);
    await assert.rejects(write(a, 0, workout, ''), { code: '40001' });
    await changeUser(b);
    assert.equal((await read(b))[0].memo, 'other owner');
  } finally { await db.close(); }
});
