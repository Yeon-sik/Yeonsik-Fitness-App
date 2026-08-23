# Reusable equipment assets

이 폴더는 운동 장면마다 기구를 다시 생성하지 않도록 사용하는 투명 기구 원본 모음이다.

- `source/`: 이미지 생성기의 원본 출력. 투명 체크무늬가 픽셀로 포함될 수 있다.
- `final/`: 배경을 제거한 실제 합성용 PNG.
- `equipment-catalog.json`: 기구 ID, 카메라 시점, 캔버스 크기, 합성 앵커.
- `tools/inspect-image-assets.mjs`: 캔버스, 알파 경계, 색상 통계를 검사한다.

앵커 좌표는 각 이미지 캔버스의 왼쪽 위를 `(0, 0)`, 오른쪽 아래를 `(1, 1)`로 본다. 예를 들어 바벨을 캐릭터 손에 연결할 때 `grip_left`와 `grip_right`가 손목 앵커에 일치하도록 하나의 강체 변환을 적용한다. 바벨 자체의 길이와 원판 크기는 프레임별로 바꾸지 않는다.

카메라 시점이 다른 운동에서는 기존 PNG를 억지로 회전하지 않고 같은 기구 ID의 새로운 `viewId` 에셋을 추가한다. 예: `barbell_loaded_side_v1`, `barbell_loaded_front_v1`.
