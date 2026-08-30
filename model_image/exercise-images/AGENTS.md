# Exercise image automation rules

이 디렉터리의 compiler, audit, render, prompt, scene 작업은 상위 `model_image/AGENTS.md`에 더해 아래 규칙을 강제한다.

- `Fitness_Weight.json`에 없는 support mode, body orientation, grip, canonical view, archetype, camera, A/B pose, equipment view/anchor/transform을 추측하지 않는다.
- metadata는 `exercise override → archetype default → deterministic mapping → MISSING_*` 순서로 해소한다. `equipmentKinematics`가 source의 명시적 `resistanceType`인 경우에는 source-owned base field로 보존하며, exercise override는 예외 운동에만 기록한다.
- 미결정 값은 `data/exercise-overrides.json`, `archetypes/archetype-registry.json`, `archetypes/deterministic-mapping.json`에 사용자가 명시할 때까지 `null`과 `MISSING_*`로 유지한다.
- `equipmentPlacements.A/B`는 archetype의 placement recipe/default가 기본이며, exercise override는 sparse exception patch만 제공한다.
- canonical dumbbell, barbell, bench, machine, attachment를 이미지 모델이 그리거나 재설계하게 하지 않는다. 모델은 anatomical mannequin pose와 검수된 근육 표시만 생성한다.
- movable free weight는 invisible grip 좌표로 손을 만들고 승인 장비는 사후 합성한다.
- B는 승인 A를 첫 편집 입력으로 사용하고 `animatedJoints`만 바꾼다. locked joints, camera, canvas, body proportions, anchors는 유지한다.
- A/B는 같은 approved equipment ID와 SHA-256 identity를 사용한다. frame별 transform만 달라질 수 있다.
- 정식 catalog는 `equipment/equipment-catalog.json`이며 다른 파일명을 자동 fallback으로 선택하지 않는다.
- 실제 합성은 `final/` 파일이며 `status == "approved"`인 자산만 허용한다.
- generation adapter가 없으면 `MISSING_GENERATION_ADAPTER`로 중단하고 임의 API나 가짜 PNG를 만들지 않는다.
- compiler와 validator의 scene contract에서 `frame.file`은 `final/<slug>-a|b.png`의 상대 경로이고, `lockedAnchors`, `anchorTolerancePixels`, `lockedEquipment`는 `renderPolicy` 아래에 둔다.

renderClass 순서는 다음과 같다.

- `bodyweight`: mannequin
- `movable_free_weight`: mannequin → canonical equipment
- `fixed_support`: support equipment → mannequin
- `fixed_machine`: machine back → mannequin → machine front occluder
- `cable_machine`: machine → mannequin → canonical attachment → vector cable → front occluder
