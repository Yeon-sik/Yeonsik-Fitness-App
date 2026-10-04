# FitnessApp | 상세 기록을 로컬에 보존하는 운동·영양 앱

근력, 실내·야외 유산소, 식사와 발전 기록을 로컬에서 관리하는 Android 앱이다. 현재 main은 Compose 화면, Room 저장소와 Kotlin Multiplatform shared 모듈을 사용하며 Java·View 호환 경로를 유지한다.

| 항목 | 내용 |
| --- | --- |
| 문서 갱신 | 2026-10-05 (Asia/Seoul) |
| 기준 소스 | [main@290f4b0](https://github.com/Yeon-sik/Yeonsik-Fitness-App/tree/290f4b01f17cea884573614e21cd7f0e954428ca) |
| 저장소 | [Yeon-sik/Yeonsik-Fitness-App](https://github.com/Yeon-sik/Yeonsik-Fitness-App) |
| 범위 | 병합된 main의 source와 명시한 검증 근거. 개발 branch·미커밋 작업은 제외. |

## 1. 30초 요약

근력, 실내·야외 유산소, 식사와 발전 기록을 로컬에서 관리하는 Android 앱이다. 현재 main은 Compose 화면, Room 저장소와 Kotlin Multiplatform shared 모듈을 사용하며 Java·View 호환 경로를 유지한다.

- 최근 main에는 실내 유산소·검색 종목, 운동 취소 toolbar, 차트 포인트 상세, 추적 알림 lifecycle/thread 안정화와 canonical Nutrition 연계 보강이 포함된다.

## 2. 문제와 해결

**문제**: 네트워크나 다른 앱의 schema에 상세 기록을 종속시키면 입력과 기존 데이터가 흔들린다. 상품명 추정 연결은 영양 identity를 깨뜨린다.

**해결**: 상세 기록을 계정 범위의 로컬 DB에 먼저 저장한다. 화면·application·repository를 분리하고 Personal OS에는 완료 운동 요약만 공유하며 외부 Nutrition·PriceTrace를 정확한 계약으로 연결한다.

## 3. 핵심 기능과 결과

| 영역 | 현재 source에서 확인한 범위 |
| --- | --- |
| 근력 | 루틴·자유 운동, family/preset/visual variant, LoadState별 세트·RIR·휴식·볼륨·완료 요약, 진행 운동 취소. |
| 유산소 | 걷기·달리기·사이클의 거리·시간·경로, 실내 유산소·activity 입력, 검색 종목과 추적 알림. |
| 식사·영양 | 재료·레시피·포장 상품·외식·보충제, 라벨·추정·external reference의 별도 provenance와 exact ID. |
| 조회·발전 | 월간 기록, 체중·목표·발전 분석, 차트 포인트 상세와 근거 수준에 따른 조언 상태. |
| 호환·공유 | Android 상세 데이터·legacy v1 sync, KMP shared API와 SwiftUI iOS host, 별도 완료 운동 summary v2. |

## 4. 검증 현황

| 항목 | 상태 | 근거와 한계 |
| --- | --- | --- |
| 현재 main KMP CI | 통과 | [KMP release readiness](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/37203344821): Android/shared tests·APK/test APK·lint와 macOS Simulator tests·framework·SwiftUI host build. |
| 실사용 환경 | 이번 갱신에서 미실행 | 실기기 instrumentation, interactive iOS 화면, release 서명·운영 RLS·교차 앱 수렴은 CI와 별도다. |

위 결과는 연결한 기준 source revision의 증거다. 이번 변경은 문서·게시 설정만 갱신하며 제품 runtime을 새로 검증한 작업으로 설명하지 않는다. 문서 validator, tracked path·link 검사와 Notion render-only dry run을 수행한다. 병합 뒤 반영은 별도 게시 workflow와 source fingerprint로 확인한다.

## 5. 현재 한계와 다음 단계

- iOS shared tests·host build를 Android 전체의 iOS 기능 동등성이나 interactive runtime 증거로 확대하지 않는다.
- Shared/Nutrition/PriceTrace 운영 RPC·RLS와 여러 계정·기기 수렴은 프로젝트별 증거가 필요하다.
- 다음 우선 작업은 호환 서명 APK로 기존 DB를 유지하며 실내 유산소·알림·운동 취소를 실기기에서 확인하는 것이다.

## 6. 관련 문서

- [프로젝트 상세](./Project_Detail.md)
- [README](../README.md)

Git Markdown이 원본이며 Notion은 생성 미러다. 검토한 문서를 main에 병합하면 on-main-push workflow가 발행한다. 개인 원본과 인증 정보는 게시하지 않는다.
