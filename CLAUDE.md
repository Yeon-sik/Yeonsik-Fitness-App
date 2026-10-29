# FitnessApp

Read `AGENTS.md` for repository rules and `docs/CLOUD_DEVELOPMENT.md` for cloud setup.
Use a feature branch. Preserve local SQLite/Room records, IDs, ownership, snapshots,
and migration history. Android and Nutrition use separate authentication domains.

Linux verification: `bash scripts/cloud/verify.sh`. Run setup first when the SDK
is missing. Treat builds, emulator tests, physical-device checks, iOS/macOS gates,
and production RPC verification as separate evidence. Never deploy a migration,
change a remote database, upload private credentials, or install/clear the user's
app as part of a cloud build task.
