import { readFileSync } from "node:fs";
import { resolve, join } from "node:path";
import { pathToFileURL } from "node:url";
import { verifyInformationInbox } from "./verify-fitness-information-inbox.mjs";

// Local-only optional PGlite runtime. No remote service, real credentials or production rows.
const runtime = process.argv[2];
if (!runtime) throw new Error("Usage: node scripts/test-fitness-information-sql.mjs <@electric-sql/pglite directory>");
const { PGlite } = await import(pathToFileURL(join(resolve(runtime), "dist/index.js")).href);
const { pgcrypto } = await import(pathToFileURL(join(resolve(runtime), "dist/contrib/pgcrypto.js")).href);
const db = new PGlite({ extensions: { pgcrypto } });
const root = resolve(import.meta.dirname, "..");
try {
  await db.exec(`
    create role anon; create role authenticated;
    create schema auth; create schema extensions;
    create extension pgcrypto with schema extensions;
    create table auth.users(id uuid primary key);
    create function auth.uid() returns uuid language sql stable as $$
      select nullif(current_setting('request.jwt.claims', true)::jsonb->>'sub', '')::uuid $$;
    grant usage on schema auth, extensions to anon, authenticated;
    grant execute on all functions in schema auth, extensions to anon, authenticated;
    insert into auth.users values ('00000000-0000-4000-8000-000000000011'), ('00000000-0000-4000-8000-000000000022');
    create table public.weight_records(id text); create table public.meal_records(id text); create table public.workout_records(id text);
    insert into public.weight_records values ('preserved-weight');
    insert into public.meal_records values ('preserved-meal');
    insert into public.workout_records values ('preserved-workout');
  `);
  await db.exec(readFileSync(join(root, "supabase/nutrition/supabase/migrations/20261106135251_fitness_information_inbox.sql"), "utf8"));
  await verifyInformationInbox(db, root);
} finally { await db.close(); }
