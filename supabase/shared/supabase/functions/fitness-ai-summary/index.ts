import { readFitnessSummary } from "./summary.mjs";

Deno.serve((request) => readFitnessSummary(request, {
  project: Deno.env.get("SUPABASE_URL"),
  publicKey: Deno.env.get("SUPABASE_ANON_KEY") ||
    JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS") || "{}").default,
}));
