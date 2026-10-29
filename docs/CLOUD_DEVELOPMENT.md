# FitnessApp 원격 개발

기준일: 2026-10-29. 저장소 준비와 실제 서비스 실행은 별도로 기록한다.
이 문서와 스크립트의 존재만으로 Codex Cloud 또는 Claude Cloud 세션 실행을
입증하지 않는다. 계정 연결, 환경 발행과 새 원격 세션 검증은 서비스에서 수행한다.

## 공통 환경

- Linux x86_64, JDK 17 이상(기존 CI는 17), Node.js 22 이상
- Android platform 36, build-tools 36.0.0, platform-tools
- Gradle은 저장소 wrapper 9.0.0 사용
- `Fitness-Image-Pipeline` 중첩 저장소와 로컬 `model_image` 없이 빌드 가능.
  추적된 `app/src/main/assets-fallback` 이미지와 manifest를 유지한다.
- `local.properties`, `.env`, 서명 키, 사용자 DB·백업은 업로드하지 않는다.
  기본 debug 빌드에는 운영 Supabase·지도·서명 비밀값이 필요하지 않다.

Linux 셸에서 실행한다. 도구 설치에는 인터넷과 Android SDK 라이선스 동의가 필요하다.
`curl`, `unzip`, `sha256sum`, Java, Node를 환경에 먼저 준비한다.

```bash
bash scripts/cloud/setup.sh
bash scripts/cloud/verify.sh
```

setup은 SDK만 준비하고 경로를 무시된 `.cloud-env.sh`에 기록한다.
빌드는 verify에서 수행한다. 기존 SDK와 `local.properties`를 덮어쓰지 않는다.
메모리가 작은 VM을 위해 Gradle worker는 2개로 제한한다.

허용할 다운로드 호스트: `dl.google.com`, `maven.google.com`,
`repo.maven.apache.org`, `repo1.maven.org`, `plugins.gradle.org`,
`plugins-artifacts.gradle.org`, `services.gradle.org`, `downloads.gradle.org`,
`github.com`, `release-assets.githubusercontent.com`. 실제 redirect/차단 로그를
보고 필요한 호스트만 추가한다. Nutrition의 로컬 JS 테스트를 실행하면
`registry.npmjs.org`도 필요하다.

## Codex Cloud

공식 [Cloud environments](https://learn.chatgpt.com/docs/environments/cloud-environments)
절차로 이 GitHub 저장소를 선택한다. 환경 준비 대화에서 JDK 17, Node 22와 위
SDK 요구를 지정하고 Install script에 setup 명령을 기록한다. Start skill에는
verify 명령과 `AGENTS.md` 준수를 기록한다. 준비 결과를 검토하고 Publish한 뒤
새 task에서 커밋 SHA와 검증 결과를 확인한다.

저장소 refresh는 도구 설치를 다시 실행하지 않는다. 도구 버전·설치 명령이
바뀌면 환경을 Edit → Republish하고 새 task에서 검증한다. 기존 task는 자체
상태를 유지한다. 로컬 개인 스킬이 원격에 자동 복사된다고 가정하지 않는다.

## Claude Cloud

공식 [Cloud environments](https://code.claude.com/docs/en/cloud-environments)
절차로 GitHub 저장소와 환경을 연결한다. Ubuntu 24.04의 root Bash setup에서
저장소가 체크아웃된 위치로 이동한 뒤 setup 명령을 실행한다. 환경 변수는 setup
실행 후 전달되므로 필요한 SDK 경로는 setup 안에서 지정한다.

setup이 약 5분을 넘으면 환경 캐시를 만들지 않으므로 전체 Gradle 빌드는 세션에서
verify로 실행한다. 캐시는 파일과 도구를 보존하지만 실행 중인 프로세스는 보존하지
않는다. `CLAUDE.md`는 공통 규칙과 검증 명령을 안내한다. 자동 SessionStart hook은
추가하지 않아 로컬 세션에서 예기치 않은 SDK 설치를 시작하지 않는다.

## 검증 경계

`kmp-release-readiness.yml`의 Linux job은 shared 단위 테스트, Android 단위
테스트, debug APK, instrumentation APK 컴파일과 lint를 실행한다. instrumentation
APK 컴파일은 기기에서 UI 테스트를 실행했다는 뜻이 아니다. iOS framework·SwiftUI
host는 기존 macOS CI job에서 검증하며 Linux cloud에서 성공했다고 주장하지 않는다.

`nutrition-db-replay.yml`은 Docker를 쓰는 폐기 가능한 로컬 DB에 이력을 재생한다.
운영 DB 배포 workflow가 아니다. Docker를 쓸 수 없는 원격 세션은 replay를 생략했다고
명시하고 CI 결과를 확인한다. 운영 Nutrition migration과 RPC·RLS 검증은 별도 승인
작업이다. `project-docs-notion.yml`의 기존 검증·발행 경로도 유지한다.

기기 설치·초기화는 이 절차에 포함되지 않는다. UI 실기기, 계정 격리, 기존 DB 보존,
실제 두 cloud 서비스 세션 상태는 별도 결과가 없으면 미확인으로 남긴다.
