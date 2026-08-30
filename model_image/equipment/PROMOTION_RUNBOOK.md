# Canonical equipment promotion

`promote-equipment.mjs`는 `equipment/source/` PNG를 검증해 `equipment/final/` canonical PNG와 schema v2 catalog entry를 함께 만든다. ID, type, view, anchors, status, renderClass는 픽셀이나 파일명에서 추론하지 않으며 CLI에서 모두 명시해야 한다.

```powershell
$taskSharp = '<absolute-path-to-sharp-package>'

node model_image/equipment/tools/promote-equipment.mjs `
  $taskSharp `
  model_image/equipment/equipment-catalog.json `
  model_image/equipment/source/<source>.png `
  --id <reviewed_equipment_id> `
  --type <reviewed_type> `
  --view-id <reviewed_view_id> `
  --file final/<canonical-kebab-name>.png `
  --anchors <reviewed-anchors.json> `
  --status draft `
  --render-class <reviewed_render_class> `
  --trim
```

anchors JSON은 asset-local normalized 좌표다.

```json
{
  "grip_center": [0.5, 0.5]
}
```

`--trim`은 원본 alpha bounding box로 자르고 anchor의 원본 픽셀 위치를 새 native canvas에 맞춰 재계산한다. anchor가 잘린 영역 밖이면 `ANCHOR_OUTSIDE_TRIMMED_ASSET`로 중단한다.

기존 ID를 검수 후 갱신할 때만 `--replace`를 사용한다. `approved` 자산만 compositor가 읽으며 `draft`와 `deprecated`는 실제 운동 생성에 사용할 수 없다.

compositor placement 형식은 다음과 같다.

```json
{
  "equipmentId": "<approved-id>",
  "viewId": "<exact-view>",
  "anchor": "grip_center",
  "target": [640, 360],
  "scale": 0.5,
  "rotationDegrees": 0,
  "z": 10
}
```

`z < 0`은 mannequin 뒤, `z > 0`은 mannequin 앞이며 `z == 0`은 mannequin 전용이다. fixed machine의 승인 front occluder가 catalog에 있을 때만 `includeFrontOccluder: true`와 양수 `frontZ`를 사용한다.
