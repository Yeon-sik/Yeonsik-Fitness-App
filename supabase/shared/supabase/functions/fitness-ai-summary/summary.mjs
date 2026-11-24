const muscleGroups = ['chest', 'back', 'legs', 'shoulders', 'abs', 'triceps', 'biceps'];
const fields = ['id', 'date', 'total_duration_seconds', ...muscleGroups.map(group => group + '_sets')];
const json = (body, status = 200) => Response.json(body, { status, headers: { 'Cache-Control': 'no-store' } });

function epochDay(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return NaN;
  const ms = Date.parse(value + 'T00:00:00Z');
  return Number.isFinite(ms) && new Date(ms).toISOString().slice(0, 10) === value ? ms / 86400000 : NaN;
}

function emptySummary() {
  return { sessions: 0, sessions_with_duration: 0, known_duration_seconds: 0,
    sets: Object.fromEntries(muscleGroups.map(group => [group, 0])) };
}

function add(summary, row) {
  summary.sessions++;
  if (row.total_duration_seconds !== null) {
    if (!Number.isSafeInteger(row.total_duration_seconds) || row.total_duration_seconds < 0) throw Error('invalid duration');
    summary.sessions_with_duration++;
    summary.known_duration_seconds += row.total_duration_seconds;
  }
  for (const group of muscleGroups) {
    const count = row[group + '_sets'];
    if (!Number.isSafeInteger(count) || count < 0) throw Error('invalid set count');
    summary.sets[group] += count;
  }
}

export async function readFitnessSummary(request, { project, publicKey, fetcher = fetch }) {
  if (request.method !== 'POST') return json({ error: 'POST required' }, 405);
  const authorization = request.headers.get('Authorization') || '';
  if (!/^Bearer [^\s]+$/i.test(authorization)) return json({ error: 'Sign in required' }, 401);
  if (!project || !publicKey) return json({ error: 'Shared project unavailable' }, 503);
  const input = await request.json().catch(() => null);
  if (!input || typeof input !== 'object' || Object.keys(input).some(key => !['from', 'to'].includes(key))) {
    return json({ error: 'Expected from and to dates' }, 400);
  }
  const length = epochDay(input.to) - epochDay(input.from) + 1;
  if (!Number.isInteger(length) || length < 1 || length > 31) return json({ error: 'Select 1 to 31 days' }, 400);
  const headers = { apikey: publicKey, Authorization: authorization };
  try {
    const auth = await fetcher(new URL('/auth/v1/user', project), { headers, redirect: 'error' });
    if (!auth.ok) return json({ error: 'Cannot verify user' }, [401, 403].includes(auth.status) ? 401 : 502);
    const user = await auth.json();
    if (typeof user.id !== 'string' || !/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(user.id) ||
        user.is_anonymous === true || user.role !== 'authenticated') return json({ error: 'Sign in required' }, 401);
    const url = new URL('/rest/v1/fitness_summary_projections_v2', project);
    url.search = new URLSearchParams({ select: fields.join(','), user_id: 'eq.' + user.id, contract_version: 'eq.2',
      completion_status: 'eq.completed', deleted_at: 'is.null', order: 'id.asc', limit: '100' }).toString();
    url.searchParams.append('date', 'gte.' + input.from);
    url.searchParams.append('date', 'lte.' + input.to);
    const byDate = new Map();
    const total = emptySummary();
    let previous;
    for (;;) {
      const response = await fetcher(url, { headers, redirect: 'error' });
      if (!response.ok) return json({ error: 'Completed workout summaries unavailable' }, 502);
      const rows = await response.json();
      if (!Array.isArray(rows)) throw Error('invalid page');
      if (!rows.length) break;
      for (const row of rows) {
        if (typeof row.id !== 'string' || !row.id || !Number.isFinite(epochDay(row.date)) ||
            row.date < input.from || row.date > input.to) throw Error('invalid summary');
        if (!byDate.has(row.date)) byDate.set(row.date, emptySummary());
        add(byDate.get(row.date), row);
        add(total, row);
      }
      const last = rows.at(-1).id;
      if (last === previous) throw Error('invalid cursor');
      previous = last;
      url.searchParams.set('id', 'gt."' + last.replaceAll('\\', '\\\\').replaceAll('"', '\\"') + '"');
    }
    return json({ version: 1, source: 'fitness_summary_projection_v2', from: input.from, to: input.to, total,
      days: [...byDate.keys()].sort().map(date => ({ date, ...byDate.get(date) })) });
  } catch { return json({ error: 'Completed workout summaries unavailable' }, 502); }
}
