# Canonical equipment rules

- `source/`는 제작 원본이고 실제 exercise 합성에 사용할 수 없다.
- `final/` 아래에 있고 catalog `status == "approved"`인 PNG만 canonical 자산이다.
- 승격 도구는 ID, type, viewId, anchors, status, renderClass를 입력에서 명시적으로 받아야 하며 이름이나 픽셀에서 의미를 추측하지 않는다.
- anchors는 asset-local normalized coordinates다. trim 시 픽셀 위치를 보존하도록 자동 재계산하고 범위를 벗어나면 중단한다.
- A/B는 동일한 equipment ID와 SHA-256 identity를 재사용한다. 이동은 translate/scale/rotation transform으로만 표현한다.
- 서로 다른 native resolution을 허용한다. exercise canvas 크기로 사전 확대된 자산을 요구하지 않는다.
- fixed machine front occluder는 같은 catalog identity의 명시적 front component만 사용한다.
