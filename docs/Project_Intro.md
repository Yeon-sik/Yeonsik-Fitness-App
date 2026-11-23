# FitnessApp | 상세 기록을 로컬에 보존하는 운동·영양 앱

근력, 실내·야외 유산소, 식사와 발전 기록을 로컬에서 관리하는 Android 앱이다. 현재 main은 Compose 화면, Room 저장소와 Kotlin Multiplatform shared 모듈을 사용하며 Java·View 호환 경로를 유지한다.

| 항목 | 내용 |
| --- | --- |
| 문서 갱신 | 2026-11-19 (Asia/Seoul) |
| 기준 소스 | [main@0b47010c](https://github.com/Yeon-sik/Yeonsik-Fitness-App/tree/0b47010c3afbca14792dff162ddc46bf1819563b) |
| 저장소 | [Yeon-sik/Yeonsik-Fitness-App](https://github.com/Yeon-sik/Yeonsik-Fitness-App) |
| 범위 | 기준 커밋에 병합된 구현과 검증 근거. iPhone 시험판은 별도 브랜치의 검증 행에 기록한다. |

## 1. 30초 요약

근력, 실내·야외 유산소, 식사와 발전 기록을 로컬에서 관리하는 Android 앱이다. 상세 기록은 계정 범위의 로컬 DB가 우선하며 Personal OS에는 완료 운동 요약만 공유하고 Nutrition·PriceTrace와는 명시적 계약으로 연결한다.

- Android는 Room v61을 사용하며 v50→v51 인수 경로를 유지한다. 운동 catalog·검수 자산, 운동별 메모·휴식·세트와 기록·통계 조회를 제공한다.
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
| main 소스 | 저장소 검증 | `main@0b47010c`의 Compose 진입점, Room 버전, KMP 타깃과 SwiftUI framework 호스트를 대조했다. |
| main CI | 통과 | Android·공통 테스트 및 빌드, Android 에뮬레이터 instrumentation, iOS Simulator 공통 테스트 및 SwiftUI 호스트 컴파일. [실행 결과](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/42347997219) |
| iPhone 시험판 | CI 통과 및 사용자 확인 | 별도 브랜치 빌드 297의 ARM64 IPA·Simulator 운동 흐름을 CI에서 확인했다. 사용자가 iPhone에서 실제 실행, 앱 재실행과 같은 IPA 덮어 설치 후 진행 중 운동 데이터 보존을 확인했다. [실행 결과](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/44125211520) |
| 제품 검증 | 추가 확인 필요 | Android 기존 사용자 DB의 실기기 업그레이드, 공통 Compose 실기기 실행, 출시 서명, 운영 데이터 접근·동기화. |

## 5. 현재 한계와 다음 단계

- Room schema와 migration 파일 존재는 실제 사용자 DB 업그레이드 성공을 자동으로 증명하지 않는다.
- Shared/Nutrition/PriceTrace 운영 RPC·RLS와 여러 계정·기기 수렴은 프로젝트별 실환경 증거가 필요하다.
- 다음 우선 검증은 호환 서명 APK로 기존 DB를 유지한 채 운동 기록·식사/영양·브랜드 identity·알림·Personal OS 요약 publication을 실기기에서 확인하는 것이다.
- Android와 iOS의 공통 화면은 체중 등록·조회·편집·삭제와 영구 저장부터 검증한다. 이후 루틴과 운동 기록 흐름으로 확장한다. [공통 개발 기준](architecture/ANDROID_IOS_DEVELOPMENT.md)

## 6. 관련 문서

- [프로젝트 상세](./Project_Detail.md)
- [README](../README.md)

Git Markdown이 원본이며 Notion은 생성 미러다. 검토한 문서를 main에 병합하면 설정된 project-docs 정책에 따라 검증·발행한다. 개인 원본과 인증 정보는 게시하지 않는다.
