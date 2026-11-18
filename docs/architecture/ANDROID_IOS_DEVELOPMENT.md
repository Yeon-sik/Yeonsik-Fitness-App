# FitnessApp Android·iOS 공통 개발 기준

2026-11-19 갱신. 적용 범위는 두 플랫폼에서 제공하는 UI·사용 흐름과 공통 업무 코드다. 기존 Android 저장소와 외부 계약에는 [정돈 리팩터링 규칙](../FitnessApp_정돈_리팩터링_규칙.md)을 함께 적용한다.

## 현재 상태와 목표

| 구분 | 근거와 범위 |
| --- | --- |
| Android와 KMP | [main@0b47010c](https://github.com/Yeon-sik/Yeonsik-Fitness-App/tree/0b47010c3afbca14792dff162ddc46bf1819563b)에는 Android Compose·Room, `:shared`의 공통 모델·계산·저장 계약, `YeonsikShared`를 쓰는 SwiftUI 검증 호스트가 있다. 호스트의 체중 저장은 메모리 어댑터를 사용한다. [main CI](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/42347997219)는 Android·공통 테스트, Android 에뮬레이터 instrumentation, iOS Simulator의 공통 테스트와 SwiftUI 호스트 컴파일을 검증했다. |
| iPhone 빌드 297 | [사이드로드 브랜치@c2b997b4](https://github.com/Yeon-sik/Yeonsik-Fitness-App/tree/c2b997b4916c0c22b2d943305173b1b5361537d2)는 SwiftUI 운동 화면과 iOS 로컬 저장을 제공한다. [CI](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/44125211520)의 ARM64 IPA와 Simulator 검증에 더해, 사용자가 iPhone 실행, 진행 중 운동의 강제 종료·재실행과 같은 IPA 덮어 설치 후 세트 값 유지를 확인했다. [설치 안내](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/c2b997b4916c0c22b2d943305173b1b5361537d2/iosApp/README.md)를 참고한다. |
| 목표 | 앱 안의 화면·컴포넌트·동작을 한 번 구현하고 양쪽 앱에서 실행한다. 공통 Compose 화면, iOS 체중 영구 저장, Android 기록의 기기 간 이관은 이후 구현·검증 대상이다. |

설계의 근거는 [Android·iOS 1차 아키텍처 탐사 보고서](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/afa0e85edb3918100da3f359fb45634751834666/docs/architecture/FitnessApp_Android-iOS_1%EC%B0%A8_%EC%95%84%ED%82%A4%ED%85%8D%EC%B2%98_%ED%83%90%EC%82%AC_%EB%B3%B4%EA%B3%A0%EC%84%9C.md)다. 보고서의 소스 관찰은 그 기준 커밋에 적용한다. 빌드 297의 구현·실기기 근거는 위 사이드로드 브랜치에 해당한다.

## 공통 화면과 동작의 소유권

| 경계 | 책임 |
| --- | --- |
| 공통 Compose UI | 화면 레이아웃, 탭과 앱 내부 이동, 버튼·입력·확인창, 색·글꼴·간격·아이콘, 로딩·오류·빈 상태 표현. 화면은 불변 상태와 사용자 동작 콜백을 입력받는다. |
| 공통 화면 상태와 업무 규칙 | 입력 초안, 저장 가능 여부, 유효성 검증, 저장·취소·뒤로 가기 후의 상태와 이동 결정. 영구 데이터는 기존 KMP 모델·서비스·저장 계약을 사용한다. |
| Android 연결부 | Android 호스트에서 공통 화면을 표시하고 시스템 뒤로 가기 이벤트를 전달한다. 해당 기능의 기존 Room 저장소와 권한·파일·GPS·기기 API를 연결한다. |
| iOS 연결부 | iOS 호스트에 공통 화면을 표시하고 뒤로 가기 제스처와 시스템 이벤트를 전달한다. iOS 영구 저장과 파일·위치·건강 등 플랫폼 API를 연결한다. |

기존 Android의 메인·피트니스·기록·설정과 기능 진입점을 화면 기준으로 사용한다. 공통으로 옮기는 기능은 기존 진입 위치, 용어, 강도·단위, 편집과 복귀의 결과를 확인한다. [디자인 시스템](../UI_DESIGN_SYSTEM.md)의 브랜드 기준을 적용하고 기존 컴포넌트의 호출부와 동작을 보존하며 공통 구현을 하나로 모은다. 리퀴드 글래스 명세는 현재 `main` 기준으로 요소별 검증 후 채택한다.

- **버튼**. 공통 폼의 저장 버튼에서 발생한 동작을 화면 상태가 처리한다. 검증과 저장 완료 후의 상태 전환은 양쪽에서 같은 코드로 실행한다. 저장 실패 시 초안과 오류를 보존하고, 연속 탭이 중복 쓰기를 일으키지 않게 한다.
- **뒤로 가기**. 공통 화면의 뒤로 가기 버튼, Android 시스템 뒤로 가기와 iOS 호스트의 앱 내부 복귀 제스처를 같은 요청으로 전달한다. 초안이 있는 경우 확인 여부와 이동 결과는 공통 화면 상태가 결정한다. 플랫폼이 화면을 먼저 닫아 요청을 우회하는 경우와 바깥 호스트로 복귀하는 경계까지 실기기에서 확인한다.
- **저장**. 공통 화면 상태와 업무 서비스가 저장 계약을 호출하고 플랫폼 연결부가 구현체를 제공한다. 렌더링은 상태를 표시하며 쓰기는 사용자 동작이나 명시적인 초기화에서 실행한다. 동기 I/O는 메인 스레드 밖에서 실행하고 쓰기 순서와 작업 시작 시점의 소유자 범위를 유지한다.
- **플랫폼 기능**. 공통 화면이 필요로 하는 입력·결과만 계약으로 정의한다. OS 권한 창, 키보드와 포커스, 안전 영역, 파일 선택, GPS·알림·건강 기기의 수명주기는 호스트와 플랫폼 연결부에서 다룬다. 기존 컴포넌트와 계약으로 해결되는 기능은 그대로 재사용한다.

새 공통 화면은 같은 화면과 액션을 두 호스트가 렌더링하는 방식으로 추가한다. 화면별 검증·저장·복귀 정책을 Android와 iOS 호스트에 다시 구현하는 경우에는 공용화가 완료된 것으로 판단할 수 없다.

## 작은 단위로 적용하는 순서

1. **기준 고정.** `main`의 체중 기록 진입점, 폼, 저장 결과를 확인하고 공통 수용 테스트 데이터를 정한다. kg/lb·날짜·정렬·같은 날 재등록과 날짜 충돌·삭제·두 소유자 범위를 기록한다.
2. **체중 기능 검증.** 기존 [Body 공통 API](../../shared/src/commonMain/kotlin/com/yeonsik/fitness/shared/feature/body/api/BodyMetricsRepositoryApi.kt)와 [서비스](../../shared/src/commonMain/kotlin/com/yeonsik/fitness/shared/feature/body/application/BodyMetricsApplicationService.kt)를 재사용한다. Android의 실제 Room 경로를 연결하고 iOS 영구 저장을 구현한다. 이 기능 하나를 기존 `:shared`에서 공통 Compose 화면으로 검증하는 구성을 먼저 시험한다.
3. **양쪽 호스트에서 실행.** Android는 기존 진입점에서 공통 화면을 호출한다. iOS는 SwiftUI 호스트 안에 공통 Compose 화면을 연결하는 방식을 검증한다. Gradle/Kotlin·Compose 호환성, 리소스, iOS Simulator와 `iosArm64` Xcode 링크, 최소 iOS 버전과 `iosX64` 지원 여부는 이 단계에서 확인한다. [SwiftUI와 Compose 통합 공식 문서](https://kotlinlang.org/docs/multiplatform/compose-swiftui-integration.html)를 참고한다. 검증 결과를 바탕으로 제품용 모듈 경계를 결정한다.
4. **운동 기능 확장.** 기존 Android의 루틴·종목 선택과 중량·횟수·RIR 입력부터 공통화한다. 진행 중 복귀, 완료와 기록 조회를 하나의 사용자 흐름으로 검증한다. 빌드 297에서 생긴 iOS 데이터가 있는 경우 호환되는 앱 ID와 저장 형식 또는 검증된 이행 경로를 확보한 뒤 저장 구현을 전환한다.
5. **기록 이관과 추가 기능.** 기존 Android 기록 접근이 필요한 범위를 정하고, 합성 데이터로 식별자·소유자·중복·오류 복구를 확인한다. 운동 전송 형식이나 앱 간 요약은 각 계약의 범위에 맞게 사용한다. 식사·영양·GPS·건강 데이터는 기능별 소유권과 저장 의미가 확인된 뒤 같은 절차로 이식한다.

Android 호환 테스트, iOS 저장 어댑터, 공통 화면 호스트 실험은 체중 계약과 비교할 데이터가 정해지면 분리해 진행할 수 있다. 공통 UI·상태 변경은 한 구현을 양쪽에 연결해 검증한다. 저장 계약이 변경되는 기능은 플랫폼 어댑터와 회귀 테스트를 함께 맞춘다.

## 완료 기준과 결과 기록

| 확인 | 통과 기준 |
| --- | --- |
| 공통 동작 | 같은 입력에서 같은 화면 상태, 검증 메시지, 저장 결과, 뒤로 가기 결과를 확인한다. 저장 실패, 취소, 빠른 연속 동작, 계정 전환을 포함한다. |
| Android | 기존 진입점·기록·DB 업그레이드 호환성을 보존한다. `.\gradlew.bat testDebugUnitTest assembleDebug`와 관련 instrumentation을 실행하고 기존 설치를 업데이트할 때는 호환 서명과 데이터 보존을 확인한다. |
| iOS | Simulator에서 공통 화면 상호작용과 재실행을 확인한다. GitHub Actions macOS에서 `iosArm64` IPA를 만들고, iPhone에 설치한 뒤 실행과 기록 보존을 직접 확인한다. 재서명과 덮어 설치 결과는 실제 수행 조건과 함께 기록한다. |
| 체중 첫 검증 | 두 실기기에서 등록·조회·수정 및 삭제, 같은 날짜 재등록 시 ID 보존, 앱 종료 후 재조회, 계정별 격리를 확인한다. |
| 운동 UX | 두 실기기에서 루틴 선택 → 중량·횟수·RIR 입력 → 앱 재실행 후 재개 → 완료 → 기록 조회를 같은 앱 내부 흐름으로 수행한다. 각 화면의 글자 크기·대비·VoiceOver/TalkBack, 키보드와 뒤로 가기를 확인한다. |

결과에는 소스 커밋, CI 링크, 실행 기기 및 실제 시나리오를 기록한다. IPA 생성, Simulator 실행, iPhone 실행, 업데이트·재서명 뒤 기록 보존과 운영 동기화는 각 근거로 판정한다. 서명 정보, Apple 계정 정보와 사용자 원본 기록은 저장소와 CI에 올리지 않는다.
