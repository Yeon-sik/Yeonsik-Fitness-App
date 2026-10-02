import crypto from 'node:crypto';
import { spawn } from 'node:child_process';
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const replayDir = path.dirname(fileURLToPath(import.meta.url));
const integrationDir = path.resolve(replayDir, '..');
const nutritionDir = path.resolve(replayDir, '../..');
const migrationsDir = path.join(nutritionDir, 'supabase', 'migrations');
const cli = 'supabase';
const docker = 'docker';
const remote = [
  '20260808104704', '20260808112304', '20260808112544', '20260809093000',
  '20260809100000', '20260809120000', '20260810100000', '20260810110000',
  '20260810120000', '20260813151754', '20260814065526', '20260814065823',
  '20260814161910', '20260817120000', '20260817133000', '20260818143000',
  '20260920091249', '20260920091256', '20260920091306', '20260920091315',
  '20260920091328', '20260920091342', '20260920091351', '20260924130855',
  '20260924131958', '20260924132515', '20260924132818'
];
const pending = ['20260927120000', '20261002120000', '20261002130000'];
const excluded = [
  'realtime', 'storage-api', 'imgproxy', 'studio', 'mailpit',
  'postgres-meta', 'edge-runtime', 'logflare', 'vector', 'supavisor'
];
const cliEnv = { ...process.env };
for (const key of [
  'SUPABASE_ACCESS_TOKEN', 'SUPABASE_DB_PASSWORD', 'SUPABASE_PROJECT_REF',
  'SUPABASE_WORKDIR', 'SUPABASE_DB_URL'
]) delete cliEnv[key];

function redact(value) {
  return String(value)
    .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '[redacted-jwt]')
    .replace(/((?:SERVICE_ROLE_KEY|ANON_KEY|JWT_SECRET|DB_URL|SUPABASE_ACCESS_TOKEN)\s*[:=]\s*)\S+/gi, '$1[redacted]')
    .replace(/(postgres(?:ql)?:\/\/[^:\s/@]+:)[^@\s]+@/gi, '$1[redacted]@');
}

function run(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: options.cwd,
      env: options.env || cliEnv,
      shell: process.platform === 'win32',
      stdio: ['pipe', 'pipe', 'pipe']
    });
    let stdout = '';
    let stderr = '';
    let timedOut = false;
    child.stdout.setEncoding('utf8');
    child.stderr.setEncoding('utf8');
    child.stdout.on('data', value => { stdout += value; });
    child.stderr.on('data', value => { stderr += value; });
    const timeout = options.timeout || 20 * 60 * 1000;
    const timer = setTimeout(() => {
      timedOut = true;
      child.kill();
    }, timeout);
    child.on('error', error => {
      clearTimeout(timer);
      reject(new Error('Could not start ' + command + ': ' + error.message));
    });
    child.on('close', code => {
      clearTimeout(timer);
      if (timedOut) return reject(new Error(command + ' exceeded its time limit'));
      if (code !== 0) {
        const tail = redact((stderr + '\n' + stdout).trim()).split(/\r?\n/).slice(-80).join('\n');
        return reject(new Error(command + ' exited with code ' + code + (tail ? ':\n' + tail : '')));
      }
      resolve({ stdout, stderr });
    });
    if (options.input === undefined) child.stdin.end();
    else child.stdin.end(options.input);
  });
}

function files() {
  return readdirSync(migrationsDir);
}

function migration(version) {
  const matches = files().filter(name => name.startsWith(version + '_') && name.endsWith('.sql'));
  if (matches.length !== 1) throw new Error('Expected one migration for ' + version + ', got ' + matches.length);
  return path.join(migrationsDir, matches[0]);
}

function inventory() {
  const expected = remote.concat(pending).sort();
  const actual = files().filter(name => name.endsWith('.sql')).map(name => name.slice(0, 14)).sort();
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error('Unexpected migration inventory. Expected ' + expected.join(', ') + '; got ' + actual.join(', '));
  }
  for (const version of expected) migration(version);

  const extSql = readFileSync(migration('20261002120000'), 'utf8')
    .replace(/--[^\r\n]*/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '');
  if (/\bpg_get_functiondef\s*\(|\bexecute\s+(?!on\b)/i.test(extSql)) {
    throw new Error('Dynamic function patch found; add exact unique anchor and fail-closed assertions before replay.');
  }
  const genericFixSql = readFileSync(migration('20261002130000'), 'utf8');
  if (!genericFixSql.includes('pg_get_functiondef')
      || !genericFixSql.includes('v_anchor_count <> 1')
      || !genericFixSql.includes('execute v_repaired')
      || !genericFixSql.includes('v_v3_anchor_count <> 1')
      || !genericFixSql.includes('execute v_v3_repaired')) {
    throw new Error('Both generic v3 patches must use pg_get_functiondef with exact unique anchors and fail closed.');
  }
  const oldSql = readFileSync(migration('20260814065823'), 'utf8');
  const ids = Array.from(oldSql.matchAll(/catalog_product_id\s*=\s*'([0-9a-f-]{36})'::uuid/gi), match => match[1]);
  if (ids.length !== 1) throw new Error('Expected exactly one Kaguri selector in unchanged historical SQL.');
  return ids[0];
}

async function freePort() {
  const server = net.createServer();
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
  const port = server.address().port;
  await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  return port;
}

function assertTemp(pathname) {
  const relative = path.relative(path.resolve(os.tmpdir()), path.resolve(pathname));
  if (!relative || relative.startsWith('..') || path.isAbsolute(relative)) {
    throw new Error('Refusing to remove a directory outside this replay temporary root.');
  }
}

function stage(version, destination) {
  const source = migration(version);
  const target = path.join(destination, path.basename(source));
  copyFileSync(source, target);
  if (!readFileSync(source).equals(readFileSync(target))) {
    throw new Error('Staged migration differs from source SQL: ' + path.basename(source));
  }
}

async function newProject(label) {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'nutrition-' + label + '-'));
  assertTemp(dir);
  const supabaseDir = path.join(dir, 'supabase');
  const migrationsDir = path.join(supabaseDir, 'migrations');
  mkdirSync(migrationsDir, { recursive: true });
  const id = 'nutritionreplay' + crypto.randomBytes(5).toString('hex');
  const apiPort = await freePort();
  const dbPort = await freePort();
  const shadowPort = await freePort();
  const config = [
    'project_id = "' + id + '"', '',
    '[api]', 'enabled = true', 'port = ' + apiPort,
    'schemas = ["public", "graphql_public"]',
    'extra_search_path = ["public", "extensions"]', 'max_rows = 1000', '',
    '[db]', 'port = ' + dbPort, 'shadow_port = ' + shadowPort,
    'major_version = 17', 'health_timeout = "2m"', '',
    '[db.migrations]', 'enabled = true', 'schema_paths = []', '',
    '[db.seed]', 'enabled = false', 'sql_paths = []', '',
    '[studio]', 'enabled = false', '',
    '[auth]', 'enabled = true', 'site_url = "http://127.0.0.1:' + apiPort + '"', '',
    '[auth.email]', 'enable_signup = true', ''
  ].join('\n');
  writeFileSync(path.join(supabaseDir, 'config.toml'), config, 'utf8');
  writeFileSync(path.join(dir, 'empty-integration.env'), '', 'utf8');
  for (const version of remote.slice(0, 11)) stage(version, migrationsDir);
  return { dir, migrationsDir, id };
}

async function sql(container, text) {
  const result = await run(docker, [
    'exec', '-i', container, 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
    '-U', 'postgres', '-d', 'postgres'
  ], { input: text, timeout: 3 * 60 * 1000 });
  for (const line of (result.stdout + '\n' + result.stderr).split(/\r?\n/)) {
    const trimmed = line.trim();
    if (/^(?:NOTICE:\s*)?PASS /.test(trimmed)) console.log(trimmed.replace(/^NOTICE:\s*/, ''));
  }
}

async function dbContainer(id) {
  const result = await run(docker, [
    'ps', '--filter', 'name=^/supabase_db_' + id + '$', '--format', '{{.Names}}'
  ], { timeout: 15000 });
  const name = result.stdout.split(/\r?\n/).map(line => line.trim()).find(Boolean);
  if (!name) throw new Error('Isolated local DB container was not found for ' + id);
  return name;
}

function parseStatus(text) {
  const values = {};
  for (const line of text.split(/\r?\n/)) {
    const match = /^([A-Z0-9_]+)=(.*)$/.exec(line.trim());
    if (!match) continue;
    values[match[1]] = match[2].replace(/^"(.*)"$/, '$1');
  }
  return values;
}

async function canonicalIntegration(project, status, mode) {
  const values = parseStatus(status.stdout);
  if (!values.API_URL || !values.ANON_KEY || !values.SERVICE_ROLE_KEY) {
    throw new Error('Local status did not provide the API URL and local test keys.');
  }
  if (!/^http:\/\/(127\.0\.0\.1|localhost):/.test(values.API_URL)) {
    throw new Error('Refusing to run integration tests against a non-local URL.');
  }
  const env = {
    ...cliEnv,
    NUTRITION_INTEGRATION_ALLOW_REMOTE: 'true',
    NUTRITION_INTEGRATION_ENV_FILE: path.join(project.dir, 'empty-integration.env'),
    NUTRITION_INTEGRATION_EMAIL_A: '',
    NUTRITION_INTEGRATION_PASSWORD_A: '',
    NUTRITION_INTEGRATION_EMAIL_B: '',
    NUTRITION_INTEGRATION_PASSWORD_B: '',
    NUTRITION_INTEGRATION_EMAIL: '',
    NUTRITION_INTEGRATION_PASSWORD: '',
    NUTRITION_DB_URL: values.API_URL,
    NUTRITION_DB_ANON: values.ANON_KEY,
    NUTRITION_INTEGRATION_SERVICE_ROLE_KEY: values.SERVICE_ROLE_KEY,
    NUTRITION_INTEGRATION_MODE: mode
  };
  console.log('Running ' + mode + ' authenticated integration tests locally');
  const result = await run(process.execPath, [
    path.join(integrationDir, 'canonical-import.integration.mjs')
  ], { cwd: integrationDir, env, timeout: 10 * 60 * 1000 });
  for (const line of result.stdout.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (/^(?:PASS |DIAG )/.test(trimmed)) console.log(trimmed);
  }
}

async function replay(label, final, kaguriId) {
  const project = await newProject(label);
  let startAttempted = false;
  try {
    console.log('Starting unlinked disposable Supabase project ' + project.id);
    startAttempted = true;
    await run(cli, ['start', '--exclude', excluded.join(',')], {
      cwd: project.dir, timeout: 25 * 60 * 1000
    });
    const container = await dbContainer(project.id);

    const oldSql = readFileSync(migration('20260814065823'), 'utf8');
    const ids = Array.from(oldSql.matchAll(/catalog_product_id\s*=\s*'([0-9a-f-]{36})'::uuid/gi), match => match[1]);
    if (ids.length !== 1 || ids[0] !== kaguriId) throw new Error('Historical Kaguri selector changed.');
    const fixture = readFileSync(path.join(replayDir, 'pre_20260814065823_kaguri.sql'), 'utf8')
      .replaceAll('{{KAGURI_PRODUCT_UUID_FROM_HISTORICAL_SQL}}', kaguriId);
    if (fixture.includes('{{KAGURI_PRODUCT_UUID_FROM_HISTORICAL_SQL}}')) {
      throw new Error('Kaguri fixture selector was not resolved.');
    }
    console.log('Injecting only the synthetic Kaguri precondition fixture');
    await sql(container, fixture);

    for (const version of remote.slice(11)) stage(version, project.migrationsDir);
    console.log('Applying the remaining 16 recovered migrations through migration up --local');
    await run(cli, ['migration', 'up', '--local'], { cwd: project.dir, timeout: 20 * 60 * 1000 });
    await sql(container, readFileSync(path.join(replayDir, 'assert_historical_replay.sql'), 'utf8'));
    if (!final) {
      console.log('PASS standalone 27-migration historical replay');
      return;
    }

    stage('20260927120000', project.migrationsDir);
    console.log('Applying pending OCR publication migration locally');
    await run(cli, ['migration', 'up', '--local'], { cwd: project.dir, timeout: 10 * 60 * 1000 });
    console.log('Running transaction rollback fixture');
    await sql(container, readFileSync(path.join(integrationDir, 'ocr-dining-out-publication-rollback.sql'), 'utf8'));

    await sql(container, readFileSync(path.join(replayDir, 'snapshot_before_external_reference.sql'), 'utf8'));
    stage('20261002120000', project.migrationsDir);
    console.log('Applying pending external-reference migration locally');
    await run(cli, ['migration', 'up', '--local'], { cwd: project.dir, timeout: 10 * 60 * 1000 });
    await sql(container, readFileSync(path.join(replayDir, 'assert_final_replay.sql'), 'utf8'));

    const status = await run(cli, ['status', '--output', 'env'], { cwd: project.dir, timeout: 60000 });
    await canonicalIntegration(project, status, 'generic-v3-ambiguity-repro');
    await sql(container, readFileSync(path.join(replayDir, 'reproduce_generic_v3_ambiguity.sql'), 'utf8'));
    await sql(container, readFileSync(path.join(replayDir, 'snapshot_before_generic_v3_fix.sql'), 'utf8'));
    stage('20261002130000', project.migrationsDir);
    console.log('Applying pending generic v3 ambiguity forward fix locally');
    await run(cli, ['migration', 'up', '--local'], { cwd: project.dir, timeout: 10 * 60 * 1000 });
    await sql(container, readFileSync(path.join(replayDir, 'assert_generic_v3_fix.sql'), 'utf8'));
    await canonicalIntegration(project, status, 'canonical-v3-contract-matrix');
    console.log('PASS final fresh replay (27 recovered migrations plus all three pending migrations)');
  } finally {
    if (startAttempted) {
      try {
        await run(cli, ['stop', '--no-backup'], { cwd: project.dir, timeout: 5 * 60 * 1000 });
      } catch (error) {
        console.error('Disposable Supabase stop warning: ' + redact(error.message));
      }
    }
    assertTemp(project.dir);
    rmSync(project.dir, { recursive: true, force: true });
  }
}

async function main() {
  const kaguriId = inventory();
  console.log('Verified exactly 27 remote migration files and 3 pending migration files.');
  const fixture = readFileSync(path.join(replayDir, 'pre_20260814065823_kaguri.sql'), 'utf8');
  const selectorToken = '{{KAGURI_PRODUCT_UUID_FROM_HISTORICAL_SQL}}';
  if (fixture.split(selectorToken).length !== 2) {
    throw new Error('Synthetic fixture must contain exactly one historical selector placeholder.');
  }
  console.log('Verified one historical Kaguri selector and a synthetic fixture template.');
  if (process.argv.includes('--preflight-only')) {
    console.log('PASS source-only preflight; no database was started.');
    return;
  }
  await run(docker, ['info', '--format', '{{.ServerVersion}}'], { timeout: 15000 });
  await replay('historical', false, kaguriId);
  await replay('final', true, kaguriId);
}

main().catch(error => {
  console.error('FAIL Nutrition disposable replay: ' + redact(error.stack || error.message));
  process.exitCode = 1;
});
