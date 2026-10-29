# 공개 상품 영양의 용량 환산

구현 브랜치: `feat/nutrition-public-serving-projection`.
마이그레이션: `20261029060700_public_product_nutrition_amount_projection.sql`.
운영 반영: **미적용, 별도 승인 대기**.

## 기존 호출 유지

```text
POST /rest/v1/rpc/get_public_product_nutrition_v1
{"p_namespace":"pricetrace","p_catalog_product_id":"<정확한 판매 규격 UUID>"}
```

두 매개변수와 `nutrition-read.v1`의 기존 18개 반환 열을 유지한다. AR 앱,
`get_nutrition_read_v1`의 식품 원본 조회, Food/Meal 저장 데이터는 바꾸지 않는다.
공개·발행된 식품과 승인·활성 상태의 **정확한 catalog 상품 연결**만 사용한다.
상품명이나 `standard_products`의 같은 상품군으로 다른 영양을 찾지 않는다.

## 환산 기준

- 대상 용량은 신뢰된 PT `product-read.v1`의 한 포장 `contentAmount/contentUnit`이다.
- `packageCount`는 반환 문맥이다. 묶음 개수로 영양을 곱하지 않는다.
- g/mg/kg, ml/l, 개수↔개수의 단위 변환만 허용한다. 질량↔부피, serving↔질량은 거절한다.
- 값에 `대상 기준량 / 원본 기준량`을 곱한다. NULL과 0을 구분하고 임의 반올림하지 않는다.
- 기본 영양 11항목과 미량 영양의 `amount`에 같은 계수를 적용한다. 영양 단위는 보존한다.
- 원본과 같은 양이면 원본 출처를 유지한다. 용량 비례 환산은 **추정치**이며 실제 포장 영양표를 검증했다는 뜻이 아니다.
- 용량이 없으면 기존 원본 기준량을 유지한다. 변환할 수 없는 단위 조합이나 잘못된 값이면 0건을 반환한다.

예: 승인된 원본 `210g / 315kcal`를 정확 상품의 `130g`으로 조회하면 `130g / 195kcal`이다.
이는 해당 130g 포장에 195kcal가 인쇄되어 있다는 증거가 아니다.

## 응답의 계산 근거

환산 결과는 기존 필드를 사용해 구분한다. 열을 추가해 strict reader를 깨지 않는다.

- `source_type = calculated_product_amount`: 용량 비례 추정치.
- `source_type = unit_converted_product_amount`: 양은 같고 표기 단위만 변환.
- `source_reference`: `nutrition-product-amount.v1` JSON. 원본 food ID·revision·기준량·기본 영양값,
  원본 출처 유형·revision, 계수, 대상 기준량·규격 revision, 추정 여부·안내를 담는다.
- 원래 source reference는 4KiB 이하일 때 포함한다. 긴 원문은 DB 원본에 그대로 남고,
  응답에는 원문 MD5 식별값을 제공한다. 이는 보안 서명이 아니다.
- `source_revision`: 원본 revision과 대상 규격을 반영한 파생 식별값이다.
- `revision`: 원본 food revision을 보존한다.

현재 AR reader는 `source_type`을 파싱하지만 계산 근거 안내를 화면에 표시하지 않는다.
**API에서 추정치를 식별할 수 있게 했다는 사실과 앱에서 안내가 보인다는 사실은 구분한다.**
이번 범위에서 AR UI·APK는 수정하지 않는다.

## 빈 레거시 규격 보완

햇반 130g의 기존 승인 연결에는 PT 용량 스냅샷 4필드가 모두 NULL이다.
현재 운영 공개 응답은 원본 `210g / 315kcal`다. 변경을 적용할 때 다음 둘을 함께 수행해야 한다.

1. 이 마이그레이션을 Nutrition 프로젝트에만 적용한다.
2. 신뢰된 서버가 PT의 정확한 130g `product-read.v1`을 다시 읽고,
   `register_product_nutrition_read_spec_v1(catalog UUID, PT 문서)`로 조회용 규격을 등록한다.

조회용 규격은 `product_nutrition_read_specs`에 별도로 저장한다. 식품 원본·승인 연결·발행 상태를
변경하거나 엄격한 `nutrition_product_basis_matches_v1` 발행 검사를 완화하지 않는다.
Food/link revision에 묶인 스냅샷만 사용하므로 원본 변경 시 오래된 조회용 규격은 무효가 된다.
기존에 규격이 있는 연결은 다른 revision/규격으로 덮어쓸 수 없다. 같은 revision에 다른 값을 넣는 것도 거절한다.

RLS와 ACL로 일반 사용자·익명 사용자의 테이블 접근 및 등록을 차단한다. `service_role`도 테이블에
직접 쓰지 못하고 등록 RPC를 사용한다. 등록 RPC 내부에서도 `auth.role()`을 확인한다.
추적용 원본 읽기 함수는 비공개 helper로 보존하고 공개 EXECUTE를 철회한다.
공개 해제·삭제·연결 승인 철회 시 기존 필터가 그대로 작동한다. 조회용 메타데이터 때문에
원본 링크 삭제가 막히지 않도록 FK는 CASCADE다.

**PT 바코드 연결은 별개다.** 바코드 `8801007002989`는 조사 시점 PT `not_linked`였다.
이 마이그레이션은 그 바코드를 등록하지 않으며, 바코드 스캔이 Nutrition 경로로 들어오는 것을 증명하지도 않는다.

## 검증과 반영 절차

```powershell
npm ci --prefix supabase/nutrition/integration
npm run test:product-amount --prefix supabase/nutrition/integration
node supabase/nutrition/integration/historical-replay/replay.mjs --preflight-only
```

PGlite의 일회성 PostgreSQL에서 실제 과거 공개 RPC·보호 함수와 새 migration을 실행한다.
41개 세부 검증(외부 컨테이너 포함 Node 집계 42개)이 통과했다. 원본/Meal 보존, 익명 공개 조회,
RLS·ACL, 정확한 identity, 단위, NULL, 멱등성, 승인 철회·공개 해제·원본 삭제를 확인한다.
전체 과거 migration replay에는 Docker가 필요하며 이번 환경에서는 Docker daemon이 없어 실행하지 못했다.
`--preflight-only` 성공은 전체 replay 성공이 아니다.

운영 반영 전 현재 공개 RPC 본문·반환형·보호 함수·승인 연결·migration 이력을 읽고 보관한다.
준비 당시 공개 RPC 본문 MD5는 `5a9d4cebe460d77c71f1f7dd38976038`이다.
반영 직전 달라졌으면 중단하고 변경 내용을 재검토한다. 현재 이력은 로컬 과거 목록과 일부 version 이름이 다르므로
무관한 pending migration 전체를 `db push`하지 않는다.

승인 후에는 이번 SQL과 햇반 130g 조회용 규격 등록만 트랜잭션으로 적용하고, migration 이력을 별도로 확인한다.
반영 후 익명 REST로 130g·195kcal·추정 표시 메타데이터, 기존 진라면 120g·510kcal·The미식 121g·405kcal,
권한 차단, 원본 식품/링크 보존을 재검증한다. 원격·실기기 검증은 별도 결과로 기록한다.

## 인수인계 — 2026-10-29

다음 작업 시작점은 **이 Nutrition 브랜치의 Docker 전체 migration replay**다.
현재 로컬 SQL 검증과 소스 inventory 검증이 통과했으며, 운영 migration/조회용 규격 등록은 아직 수행하지 않았다.

1. Docker daemon을 준비하고 아래 전체 replay를 실행한다. 소스 inventory 검증으로 대체하지 않는다.

   ```powershell
   node supabase/nutrition/integration/historical-replay/replay.mjs
   ```

2. 운영 공개 RPC·보호 함수·migration 이력을 다시 읽고 준비 당시 기록과 비교한다.
   Nutrition 프로젝트와 PT 프로젝트를 구분하고, 원격 반영 직전 사용자 승인을 받는다.
3. 승인된 이번 migration만 반영하고, PT에서 새로 읽은 햇반 130g의 정확한 상품 문서로 조회용 규격을 등록한다.
   바코드나 원본 영양·발행 상태는 이 작업으로 바꾸지 않는다.
4. 익명 REST에서 130g·195kcal·계산 근거, 기존 진라면·The미식 영양 보존, 비공개 helper 접근 차단을 확인한다.
5. 앱 스캔까지 시험하려면 PT 바코드 `8801007002989`의 정확한 130g 포장 연결을 별도로 확인·등록해야 한다.
   현재 AR 앱의 기존 5분 조회 캐시도 구분해서 검증한다.

검증 상태:

| 대상 | 결과 |
|---|---|
| 새 Nutrition SQL 세부 검증 | 41/41 통과 (Node 외부 컨테이너 포함 42/42) |
| migration inventory | 28개 과거 + 8개 forward 확인, 통과 |
| 저장소 문서 validator | 오류 0, 경고 0 |
| Docker 전체 replay | 미수행 — daemon 연결 불가 |
| FitnessApp `assembleDebug` | APK 조립 완료, 기기 설치 없음 |
| FitnessApp `testDebugUnitTest` | 미통과 — 115개 클래스 모두 초기 로딩 시 `ClassNotFoundException` |
| ASCII 임시 경로의 관련 Android 시험 재시도 | 클래스 JAR 파일 잠금으로 중단, 원인 확정 안 됨 |
| 운영 Nutrition·A25 기능 확인 | 미수행 |

2026-10-29 22:28 KST 재확인에서도 운영 함수 본문과 원본 210g·315kcal,
PT 대상 규격 130g을 확인했다. 새 테이블·내부 함수는 운영 DB에 아직 없다.
독립 `npm ci` 의존성으로 SQL 세부 41개 검증을 다시 통과했다.
Docker Desktop을 시작했으나 `dockerInference` 소켓 초기화 오류로 엔진이 종료되어
전체 replay는 실행하지 못했다. 이번에 시작한 Docker 프로세스만 종료했으며 데이터 초기화는 하지 않았다.
Windows 실행기의 `node.exe` 공백 경로 실패는 별도 재현했고, CLI shim만 셸을 사용하도록 수정한 뒤
같은 Node 실행 점검이 성공했다. 이 수정으로 전체 replay 성공을 주장하지 않는다.

Android 실패를 SQL 회귀 실패나 성공으로 동일시하지 않는다. 이 변경에는 Android/AR 소스 변경이 없으며,
빌드가 생성한 무관한 자산 변경은 복원했다. 임시 R 드라이브 연결도 해제했다.
검증 기록은 [`integration/product-amount-validation-2026-10-29.json`](integration/product-amount-validation-2026-10-29.json)에 있다.

작업 위치:

- Git 저장소: `Yeon-sik/Yeonsik-Fitness-App`
- 브랜치: `feat/nutrition-public-serving-projection`
- 로컬 worktree: `C:/Github/가상현실_중간/artifacts/nutrition-serving-worktree`
- AR 저장소 `feat/ui-p0-video`는 변경 없음.
- 초기 PT 별도 API 초안은 `feat/nutrition-product-basis` 로컬 worktree에 보류되어 있다.
  사용자가 선택한 구현은 **Nutrition 기존 공개 RPC 보완**이며 그 초안은 배포 대상이 아니다.
