# FitnessApp Phase 3 MVP

Standalone Android MVP for manual fitness logging.

## Scope

- Owns detailed fitness records: workout sessions, exercises, sets, body metrics, and text meals.
- Keeps `created_from_quick_record_id` nullable on domain rows for later quick_records linking.
- Does not modify the existing Personal OS or MemoNote app.
- Excludes AI parsing, image meal attachments, Health API integration, automation, and machine-specific weight correction.

## Run

```powershell
cd C:\Github\personal-os\FitnessApp
C:\Users\nwhck\.gradle\wrapper\dists\gradle-8.14.3-bin\cv11ve7ro1n3o1j4so8xd9n66\gradle-8.14.3\bin\gradle.bat :app:assembleDebug
```
