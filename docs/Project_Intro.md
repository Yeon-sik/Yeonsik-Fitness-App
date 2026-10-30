# FitnessApp | 상세 기록을 로컬에 보존하는 운동·영양 앱

근력, 실내·야외 유산소, 식사와 발전 기록을 로컬에서 관리하는 Android 앱이다. 현재 main은 Compose 화면, Room 저장소와 Kotlin Multiplatform shared 모듈을 사용하며 Java·View 호환 경로를 유지한다.

| 항목 | 내용 |
| --- | --- |
| 문서 갱신 | 2026-10-30 (Asia/Seoul) |
| 기준 소스 | [main@9d05980](https://github.com/Yeon-sik/Yeonsik-Fitness-App/tree/9d05980b243552c4503743bb2306d510cea03ca7) |
| 저장소 | [Yeon-sik/Yeonsik-Fitness-App](https://github.com/Yeon-sik/Yeonsik-Fitness-App) |
| 범위 | 2026-10-30 감사 시점에 병합된 main source와 저장소에 남은 검증 근거. 개발 branch·미커밋 작업은 제외. |

## 1. 30초 요약

근력, 실내·야외 유산소, 식사와 발전 기록을 로컬에서 관리하는 Android 앱이다. 상세 기록은 계정 범위의 로컬 DB가 우선하며 Personal OS에는 완료 운동 요약만 공유하고 Nutrition·PriceTrace와는 명시적 계약으로 연결한다.

- 10월 이후 main에는 Room schema 54~57, 운동 catalog·검수 자산 확대, 운동별 메모·휴식·세트 구조와 기록/홈/통계 UX 보강이 반영됐다.
- 식사 영역에는 restaurant component·dining proposal·수동 음식/브랜드·영양 publication 흐름과 PriceTrace 브랜드 identity 연결이 추가됐다.
- Personal OS 완료 운동 summary의 incremental publication, 설정·인증·수동 sync 경계, 클라우드 개발/검증 준비가 보강됐다.

## 2. 문제와 해결

**문제**: 네트워크나 다른 앱의 schema에 상세 기록을 종속시키면 입력과 기존 데이터가 흔들린다. 상품명·운동명 추정 연결은 identity와 장기 통계를 깨뜨릴 수 있다.

**해결**: 상세 기록을 계정 범위의 로컬 DB에 먼저 저장한다. 화면·application·repository를 분리하고 exercise/product/restaurant identity는 canonical ID와 migration으로 연결한다. Personal OS에는 완료 운동 요약만 공유한다.

## 3. 핵심 기능과 결과

| 영역 | 현재 source에서 확인한 범위 |
| --- | --- |
| 근력 | 루틴·자유 운동, 확장된 운동 catalog, 운동별 메모·휴식·세트 구조, RIR·볼륨·완료 요약과 진행 운동 취소. |
| 유산소 | 걷기·달리기·사이클의 거리·시간·경로, 실내 유산소·activity 입력, 검색 종목과 추적 알림. |
| 식사·영양 | 재료·레시피·포장 상품·외식·보충제, 라벨·추정·external reference provenance, restaurant component와 영양 publication. |
| identity | 수동 운동의 canonical link, PriceTrace 브랜드 identity, owner-scoped 외식 제안과 명시적 공개 동의. |
| 조회·발전 | 월간 기록, 체중·목표·발전 분석, 차트 포인트 상세, 기록/홈/통계 usability 경로. |
| 호환·공유 | Android 상세 데이터·legacy v1 sync, KMP shared API와 SwiftUI iOS host, Personal OS 완료 운동 summary v2 incremental publication. |

## 4. 검증 현황

| 항목 | 상태 | 근거와 한계 |
| --- | --- | --- |
| 최신 main source | 저장소 검증 | `main@9d05980`과 10월 이후 migration·Room schema·테스트·CI 설정을 대조했다. 코드·테스트 존재는 runtime 성공과 동일하지 않다. |
| 이전 KMP CI 기록 | 통과 기록 | [KMP release readiness](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/37213991994): Android/shared tests·APK/test APK·lint와 macOS Simulator tests·framework·SwiftUI host build. 최신 HEAD 전체를 새로 실행한 증거로 확대하지 않는다. |
| 실사용 환경 | 미검증 | 최신 HEAD의 실기기 instrumentation, interactive iOS 화면, release 서명·운영 RLS·교차 앱 수렴은 이번 문서 갱신에서 실행하지 않았다. |

## 5. 현재 한계와 다음 단계

- Room schema와 migration 파일 존재는 실제 사용자 DB 업그레이드 성공을 자동으로 증명하지 않는다.
- Shared/Nutrition/PriceTrace 운영 RPC·RLS와 여러 계정·기기 수렴은 프로젝트별 실환경 증거가 필요하다.
- 다음 우선 검증은 호환 서명 APK로 기존 DB를 유지한 채 운동 기록·식사/영양·브랜드 identity·알림·Personal OS 요약 publication을 실기기에서 확인하는 것이다.

## 6. 관련 문서

- [프로젝트 상세](./Project_Detail.md)
- [README](../README.md)

Git Markdown이 원본이며 Notion은 생성 미러다. 검토한 문서를 main에 병합하면 설정된 project-docs 정책에 따라 검증·발행한다. 개인 원본과 인증 정보는 게시하지 않는다.
