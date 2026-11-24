import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

export async function checkCompletedWorkoutRoundTrip(user, other, rpc) {
  const day = '2025-01-01';
  const first = randomUUID();
  const second = randomUUID();
  const document = id => ({ title: 'Same routine', startMillis: null, endMillis: null, durationSeconds: 120,
    totalVolumeKg: 453.59237, exercises: [{ id: id + '-exercise', name: 'Fixture exercise',
      sets: [{ id: id + '-set', weightKg: 45.359237, inputLoad: 100, inputUnit: 'lb', reps: 10, rir: 2 }] }] });
  const list = (as, expected = as) => rpc(as, 'fitness_completed_workouts_v1', { p_expected_owner: expected.userId, p_day: day });
  const save = (as, id, revision, workout, memo, expected = as) => rpc(as, 'fitness_save_completed_workout_v1', {
    p_expected_owner: expected.userId, p_day: day, p_id: id, p_revision: revision, p_workout: workout, p_memo: memo
  });
  for (const id of [first, second, first]) assert.equal((await save(user, id, 0, document(id), '')).body.revision, 1);
  const initial = await list(user);
  assert.equal(initial.status, 200);
  assert.equal(initial.body.length, 2);
  assert.deepEqual(initial.body.find(row => row.id === first).workout, document(first));
  const summary = await rpc(user, 'fitness_completed_workout_summary_v1', { p_expected_owner: user.userId, p_from: day, p_to: day });
  assert.deepEqual(summary.body, { version: 1, from: day, to: day, sessions: 2, completed_sets: 2, volume_kg: 907.18474,
    sessions_with_duration: 2, known_duration_seconds: 240 });
  assert.equal((await save(user, first, 1, document(first), 'From second device')).body.revision, 2);
  assert.equal((await save(user, first, 1, document(first), 'Stale')).body.code, '40001');
  assert.deepEqual((await list(other)).body, []);
  assert.equal((await list(other, user)).body.code, '42501');
  assert.equal((await save(other, first, 2, null, '', user)).body.code, '42501');
  assert.ok([401, 403].includes((await list(null, user)).status));
  assert.equal((await save(user, first, 2, null, '')).body.workout, null);
  assert.equal((await save(user, first, 1, document(first), '')).body.code, '40001');
  console.log('PASS authenticated completed workout round trip, retry, ownership and tombstones');
}
