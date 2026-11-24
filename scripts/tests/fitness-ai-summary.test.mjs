import assert from 'node:assert/strict';
import test from 'node:test';
import { readFitnessSummary } from '../../supabase/shared/supabase/functions/fitness-ai-summary/summary.mjs';

const owner = '11111111-1111-4111-8111-111111111111';
const dates = { from: '2025-01-01', to: '2025-01-07' };
const request = (data, headers = { Authorization: 'Bearer fixture' }) => new Request('https://fixture.invalid', {
  method: 'POST', headers, body: JSON.stringify(data)
});
const env = { project: 'https://shared.invalid', publicKey: 'fixture' };

test('authenticated summary reads only the owner and approved fields, across short pages', async () => {
  let pages = 0;
  const ids = ['session-1 (, & "detail")', 'session-2'];
  const result = await readFitnessSummary(request(dates), { ...env, fetcher: async (url, options) => {
    assert.equal(options.headers.Authorization, 'Bearer fixture');
    if (url.pathname === '/auth/v1/user') return Response.json({ id: owner, role: 'authenticated' });
    assert.equal(url.pathname, '/rest/v1/fitness_summary_projections_v2');
    assert.equal(url.searchParams.get('user_id'), 'eq.' + owner);
    assert.equal(url.searchParams.get('deleted_at'), 'is.null');
    assert.deepEqual(url.searchParams.getAll('date'), ['gte.2025-01-01', 'lte.2025-01-07']);
    assert.doesNotMatch(url.searchParams.get('select'), /memo|heart|weight|name|user_id/);
    assert.equal(new URL(url.href).searchParams.get('id'), pages ? 'gt.' + ids[pages - 1] : null);
    if (pages === 2) return Response.json([]);
    const count = ++pages;
    const row = { id: ids[count - 1], date: dates.from, total_duration_seconds: count === 1 ? 60 : null,
      chest_sets: 2, back_sets: 0, legs_sets: 0, shoulders_sets: 0, abs_sets: 0, triceps_sets: 0, biceps_sets: 0 };
    return Response.json([row]);
  } });
  assert.equal(result.status, 200);
  const data = await result.json();
  assert.equal(data.total.sessions, 2);
  assert.equal(data.total.sessions_with_duration, 1);
  assert.equal(data.total.known_duration_seconds, 60);
  assert.equal(data.total.sets.chest, 4);
  assert.equal(data.days.length, 1);
  assert.doesNotMatch(JSON.stringify(data), /session-1|11111111/);
});

test('rejects arbitrary owners, invalid ranges and anonymous or expired authentication', async () => {
  const noFetch = { ...env, fetcher: () => assert.fail('Unexpected request') };
  assert.equal((await readFitnessSummary(request(dates, {}), noFetch)).status, 401);
  for (const data of [{ ...dates, user_id: owner }, { from: '2025-02-30', to: '2025-03-01' },
    { from: '2025-01-01', to: '2025-02-01' }]) {
    assert.equal((await readFitnessSummary(request(data), noFetch)).status, 400);
  }
  for (const response of [new Response(null, { status: 401 }), Response.json({ id: owner, role: 'authenticated', is_anonymous: true })]) {
    assert.equal((await readFitnessSummary(request(dates), { ...env, fetcher: async () => response })).status, 401);
  }
});
