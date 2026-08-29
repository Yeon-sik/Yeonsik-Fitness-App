# Fitness-Image-3D pipeline runbook

현재 구현 범위는 운동 이름에서 생성/합성/검수/파일명/ZIP 단계가 어떤 입력과 순서로 실행되어야 하는지를 결정하는 orchestration contract다. 실제 image generation adapter는 저장소에 없으며, 없는 상태에서 이미지를 만들었다고 가장하지 않는다.

## Source hierarchy

1. `Fitness_Weight.json`: 운동 ID, 이름, 운동/근육/저항 분류
2. `data/exercise-overrides.json`: 사용자가 검토한 support/orientation/grip/view/archetype/placement/anchor
3. `archetypes/archetype-registry.json`: 공통 camera, A/B pose, locked/animated joints, renderClass, reference scene
4. `style-4/muscle-layers.json`: 근육 그룹을 실제 layer ID로 확장
5. `equipment/equipment-catalog.json`: 승인 canonical equipment identity, native size, SHA-256, asset-local anchors

`exercise-catalog.json`의 `null`은 미결정 상태다. builder, compiler, audit는 이를 이름이나 `motionType`으로 추론하지 않는다.

## Commands

```powershell
node model_image/data/build-exercise-catalog.mjs

node model_image/exercise-images/tools/audit-autogen-readiness.mjs --table

node model_image/exercise-images/tools/compile-exercise.mjs "운동 이름"

node model_image/exercise-images/tools/render-exercise.mjs "운동 이름"

node model_image/exercise-images/tools/render-batch.mjs exercise-names.txt
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
- batch ZIP은 모든 운동의 A/B alpha, 파일명, equipment identity, anchor drift 검증이 끝난 뒤에만 생성한다. 하나라도 BLOCKED면 ZIP을 만들지 않는다.

## Required reviewed override shape

다음은 형식 예시이며 실제 운동 값이 아니다. 괄호 값을 복사해서 제품 데이터로 쓰지 않는다.

```json
{
  "supportMode": "<reviewed>",
  "bodyOrientation": "<reviewed>",
  "equipmentKinematics": "<reviewed>",
  "gripVariant": "<reviewed-or-not_applicable>",
  "canonicalView": "<reviewed>",
  "archetypeId": "<reviewed-archetype-id>",
  "anchorTolerancePixels": 8,
  "lockedAnchors": {},
  "equipmentPlacements": {
    "A": [],
    "B": []
  }
}
```

맨몸이어도 `equipmentPlacements.A/B`를 빈 배열로 명시한다. 배열 누락은 기구 없음인지 정보 누락인지 구분할 수 없으므로 `MISSING_OVERRIDE`다.

## Generation adapter boundary

`render-exercise.mjs`는 adapter가 없으면 compile 결과와 `render-run.json`을 남기고 `MISSING_GENERATION_ADAPTER`로 중단한다. adapter 모듈을 추가할 때는 최소 `generateA()`와 `editBFromApprovedA()`를 export해야 하며, B 함수의 첫 입력은 validator가 승인한 A여야 한다. HTTP endpoint, model name, credential, retry policy는 이 저장소에서 확인되지 않았으므로 현재 계약에 하드코딩하지 않는다.
