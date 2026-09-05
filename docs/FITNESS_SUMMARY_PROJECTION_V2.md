# Fitness Summary Projection v2

FitnessApp remains the owner of the detailed workout session, exercise master,
set rows, and meal snapshots. Personal OS receives only the completed-session
projection published through `upsert_fitness_summary_projection_v2`.

The payload contains:

- `source_fitness_session_id`
- `date`
- `completion_status` (`completed` only)
- `chest_sets`, `back_sets`, `legs_sets`, `shoulders_sets`, `abs_sets`,
  `triceps_sets`, `biceps_sets`
- `total_duration_seconds`
- `cardio_duration_seconds` when the session is cardio
- audit fields and `contract_version: 2`

It deliberately excludes exercise IDs/names, family or variant identity,
weight, reps, RPE/RIR, per-set data, and exercise metadata. The existing
`sync_fitness_data_v1` and shared `workout_records` reconciliation remain in
place solely for legacy compatibility.
