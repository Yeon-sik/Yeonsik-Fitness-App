# Fitness-Image-3D pipeline runbook

현재 구현 범위는 운동 이름에서 생성/합성/검수/파일명/ZIP 단계가 어떤 입력과 순서로 실행되어야 하는지를 결정하는 orchestration contract다. 실제 image generation adapter는 저장소에 없으며, 없는 상태에서 이미지를 만들었다고 가장하지 않는다.

## Source hierarchy

1. `Fitness_Weight.json`: 운동 ID, 이름, 운동/근육/저항 분류
2. `data/exercise-overrides.json`: 예외 운동에만 적용하는 reviewed override
3. `archetypes/archetype-registry.json`: 공통 metadata, camera, A/B pose, placement recipe, locked/animated joints, renderClass, reference scene
4. `archetypes/deterministic-mapping.json`: 이름 추론이 아닌 명시적 deterministic mapping
5. `style-4/muscle-layers.json`: 근육 그룹을 실제 layer ID로 확장
6. `equipment/equipment-catalog.json`: 승인 canonical equipment identity, native size, SHA-256, asset-local anchors

metadata resolution 순서는 `exercise override → archetype default → deterministic mapping → MISSING_*`다. 단, `equipmentKinematics`가 `Fitness_Weight.json`의 명시적인 `resistanceType`에서 온 경우에는 source-owned base field로 보존한다. `exercise-overrides.json`은 전체 운동 목록이 아니라 예외 목록이며, 일반 운동의 `equipmentPlacements.A/B`는 archetype의 placement recipe/default에서 온다. `exercise-catalog.json`의 `null`은 미결정 상태다. builder, compiler, audit는 이를 이름이나 `motionType`으로 추론하지 않는다.

## Commands

```powershell
node model_image/data/build-exercise-catalog.mjs

node model_image/exercise-images/tools/audit-autogen-readiness.mjs --table

node model_image/exercise-images/tools/compile-exercise.mjs "운동 이름"

node model_image/exercise-images/tools/render-exercise.mjs "운동 이름"

node model_image/exercise-images/tools/render-batch.mjs exercise-names.txt
```

기존 기구 catalog를 옮길 때는 final PNG를 복사하지 않고 같은 디렉터리의 v2 catalog에 등록한다. dimensions와 SHA-256은 파일에서 계산하며, `status`와 `renderClass`는 결정 파일이 없으면 `null`이다.

```powershell
node model_image/equipment/tools/migrate-equipment-catalog-v1-to-v2.mjs `
  --input "model_image/equipment/equipment-catalog(2).json" `
  --output model_image/equipment/equipment-catalog.json

node model_image/equipment/tools/migrate-equipment-catalog-v1-to-v2.mjs --init
```

사용자가 정식 catalog 파일명을 정리하기 전 임시 catalog를 검사할 때만 경로를 명시한다. 도구가 임시 이름을 자동 선택하지 않는다.

```powershell
node model_image/exercise-images/tools/audit-autogen-readiness.mjs `
  --equipment-catalog "model_image/equipment/equipment-catalog(2).json" `
  --table
```

## Compiler outputs

```text
exercise-images/generated/<slug>/
  scene.json
  prompt-a.md
  prompt-b-edit.md
  placements-a.json
  placements-b.json
  render-run.json        # render command를 실행한 경우
```

- A prompt는 anatomical mannequin만 생성한다.
- B prompt는 승인된 A를 첫 edit input으로 고정한다.
- A/B placement는 같은 approved equipment identity를 사용하고 transform만 달라질 수 있다.
- 최종 파일명은 `<slug>-a.png`, `<slug>-b.png`다.
- scene의 `frame.file`은 scene 위치에서 `../final/` 또는 `../../final/` 아래의 최종 PNG를 상대 참조한다.
- `lockedAnchors`, `anchorTolerancePixels`, `lockedEquipment`는 모두 scene의 `renderPolicy` 아래에 둔다.
- batch ZIP은 모든 운동의 A/B alpha, 파일명, equipment identity, anchor drift 검증이 끝난 뒤에만 생성한다. 하나라도 BLOCKED면 ZIP을 만들지 않는다.

## Exception-only override shape

일반 운동에는 override entry가 필요하지 않다. 다음은 archetype 기본값으로 설명되지 않는 예외에만 사용하는 형식 예시이며 실제 운동 값이 아니다.

```json
{
  "kind": "exception",
  "required": true,
  "gripVariant": "<reviewed-exception>",
  "equipmentPlacements": {
    "B": [{ "instanceId": "<archetype-instance>", "target": [640, 360] }]
  }
}
```

placement override는 archetype recipe에 대한 sparse patch다. 전체 운동에 A/B 배열을 반복하지 않으며, 맨몸 운동은 recipe 없이 빈 placement로 컴파일할 수 있다.

## Generation adapter boundary

`render-exercise.mjs`와 `render-batch.mjs`는 현재 `implementation: "scaffold"`인 orchestration scaffold다. adapter가 없으면 compile 결과와 `render-run.json`을 남기고 `MISSING_GENERATION_ADAPTER`로 중단한다. adapter 모듈을 추가할 때는 최소 `generateA()`와 `editBFromApprovedA()`를 export해야 하며, B 함수의 첫 입력은 validator가 승인한 A여야 한다. HTTP endpoint, model name, credential, retry policy는 이 저장소에서 확인되지 않았으므로 generation API를 임의 구현하거나 계약에 하드코딩하지 않는다.
