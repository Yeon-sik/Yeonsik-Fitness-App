# AGENTS.md

## 작업 원칙

- 현재 요청의 최소 동작 단위를 먼저 구현하고 검증한다.
- Android 기록 흐름은 입력 → repository → SQLite 순서로 추적한다.
- 로컬 기록을 원격 동기화보다 우선하며, 미검증 Supabase 동작을 완료로 표현하지 않는다.
- 테스트·빌드·실기기·운영 검증 상태를 각각 구분한다.
- `main`에서 직접 개발하지 않고 기능 브랜치와 작은 커밋을 사용한다.

## 프로젝트 경계

- 이 저장소는 Java 17, Android View, SQLiteOpenHelper 기반 Fitness App이다.
- Kotlin·Compose·Room 전면 재작성은 명시적 요청과 별도 마이그레이션 계획 없이 진행하지 않는다.
- 상세 운동·종목·세트는 Fitness App이 소유하고 Personal OS에는 완료 요약만 공유한다.
- `.understand-anything/`와 생성 그래프 산출물은 제품 코드 근거에서 제외한다.

## 검증

기능 변경 후 최소 검증은 다음과 같다.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

release 완료를 주장하려면 서명, 운영 RLS, 두 계정 격리, 실기기 검증을 별도로 수행한다.

## 프로젝트 문서와 Notion

- 프로젝트 소개·상세 문서의 생성, 감사, 갱신 요청에는 설치된 `maintain-project-docs` 스킬을 사용한다.
- Git의 `docs/Project_Intro.md`와 `docs/Project_Detail.md`가 원본이며 Notion은 생성 미러다.
- 문서 변경 후 아래 명령을 순서대로 실행한다.

```powershell
node .github/project-docs/validate-project-docs.mjs --config project-docs.config.json --require-tracked
node .github/project-docs/sync-project-docs-to-notion.mjs --config project-docs.config.json
```

- 로컬에서는 Notion 쓰기 옵션을 사용하지 않는다.
- GitHub Environment 이름은 `notion-production`이며 secret은 `NOTION_TOKEN`, `NOTION_PAGE_IDS_JSON` 두 개다.
- `main`에 검토된 문서 변경이 반영되면 GitHub Actions가 자동 발행한다.
- `PUBLISH` workflow dispatch는 첫 발행과 장애 복구에만 사용한다.
- token과 page ID는 저장소, 문서, 로그에 기록하지 않는다.
