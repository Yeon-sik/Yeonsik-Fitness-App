# AGENTS.md

## 작업 원칙

- 현재 요청의 최소 동작 단위를 먼저 구현하고 검증한다.
- Android 기록 흐름은 입력 → repository → SQLite 순서로 추적한다.
- 로컬 기록을 원격 동기화보다 우선하며, 미검증 Supabase 동작을 완료로 표현하지 않는다.
- 테스트·빌드·실기기·운영 검증 상태를 각각 구분한다.
- main에서 직접 개발하지 않고 기능 브랜치와 작은 커밋을 사용한다.

## 프로젝트 문서와 Notion

- 프로젝트 소개·상세 문서의 생성, 감사, 갱신 요청에는 저장소 스킬
  `.agents/skills/maintain-project-docs/SKILL.md`를 먼저 사용한다.
- Git의 `docs/Project_Intro.md`와 `docs/Project_Detail.md`가 진실 원천이며
  Notion 페이지는 생성된 읽기 전용 미러다.
- 문서 변경 전후에 다음 검증을 실행한다.

```powershell
node .github/project-docs/validate-project-docs.mjs --config project-docs.config.json --require-tracked
node .github/project-docs/sync-project-docs-to-notion.mjs --config project-docs.config.json
```

- 로컬에서는 Notion 쓰기 옵션을 사용하지 않는다.
- GitHub Environment는 `notion-production`, secret은 `NOTION_TOKEN`,
  `NOTION_PAGE_IDS_JSON`이다.
- 검토된 문서 변경이 `main`에 반영되면 자동 발행한다.
- 수동 `PUBLISH` dispatch는 첫 발행과 장애 복구에만 사용한다.
