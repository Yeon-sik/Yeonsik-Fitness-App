# Supabase layout

FitnessApp has two independent remote database targets:

- `shared/supabase/`: configuration boundary for the Personal OS project used
  by CashOS, FitnessApp, and PersonalOSApp. FitnessApp does not own or deploy
  shared-project migrations from this directory.
- `nutrition/supabase/`: migrations for the FitnessApp-only Nutrition project.
  It contains food, recipe, and nutrient catalog tables only.
- `.env`: ignored build configuration containing both independent URL/key pairs.

Never link `supabase/nutrition` to the Personal OS shared project. The first
Nutrition migration intentionally fails if it detects workout, body, or meal
record tables, preventing the catalog schema from being deployed to the wrong
project.

Verify and deploy the Nutrition project:

```powershell
supabase link --project-ref <nutrition-project-ref> --workdir supabase/nutrition
supabase migration list --linked --workdir supabase/nutrition
supabase db push --linked --dry-run --workdir supabase/nutrition
supabase db push --linked --yes --workdir supabase/nutrition
supabase db lint --linked --workdir supabase/nutrition
```

Verify the shared project reference without applying Nutrition migrations:

```powershell
supabase link --project-ref <shared-project-ref> --workdir supabase/shared
supabase migration list --linked --workdir supabase/shared
```

An earlier local branch revision deployed Nutrition tables to the shared remote
project. The corrected app never reads or writes those shared-project tables.
Dropping remote tables is destructive and must be handled later as a separately
reviewed cleanup after confirming that they contain no required rows.
