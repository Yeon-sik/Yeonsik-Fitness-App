# 운동 해부학 이미지 제작 표준

이 문서는 Yeonsik Fitness App의 운동 이미지 생성, 기존 에셋 재사용, 근육 활성도 표현, 프레임 애니메이션, 앱 연결을 위한 개별 자산 규범이다. 여러 운동의 원형 분류, 시각 별칭, 전체 커버리지와 배치 제작은 [EXERCISE_IMAGE_CATALOG_STANDARD.md](EXERCISE_IMAGE_CATALOG_STANDARD.md)를 함께 따른다. 다른 채팅이나 다른 에이전트도 두 문서를 동일한 제품 계약으로 사용한다.

- 문서 성격: 향후 작업에 적용되는 규범과 현재 구현 설명
- 근거 경계: `main` commit `757aac88fc7551d0c931fbc318a5b7534ae21d54`, 2026-08-25
- 검증 등급: 저장소 구현 확인. 이 문서 자체는 모든 기존 이미지의 해부학적 정확성을 새로 인증하지 않는다.

## 1. 목표

사용자는 이미지만 보고 다음 세 가지를 즉시 이해할 수 있어야 한다.

1. 어떤 자세와 궤도로 운동하는가.
2. 어떤 근육이 주로 활성화되는가.
3. 어떤 기구를 어디에 접촉하고 어떻게 움직이는가.

일러스트의 우선순위는 `동작 판독성 → 해부학 정확성 → 프레임 일관성 → 미적 디테일` 순이다. 디테일이 많아도 관절이나 기구 위치가 틀리면 실패다.

## 2. 소스 오브 트루스

아래 순서가 충돌 해결 우선순위다.

| 우선순위 | 파일 | 역할 |
| --- | --- | --- |
| 1 | `Fitness_Weight.json` | 제품의 운동 ID, 주·보조 부위, 편측성, 운동 패턴 |
| 2 | `style-4/muscle-layers.json` | 부위 그룹을 실제 근육 레이어로 연결하는 분류 체계와 색상 |
| 3 | `style-4/source/front-master.png`, `back-master.png` | 캐릭터 비율과 근육 경계의 기준 원본 |
| 4 | `equipment/equipment-catalog.json` | 재사용 기구 ID, 시점, 캔버스, 앵커 |
| 5 | `exercise-images/scenes/*.scene.json` | 운동별 프레임, 시간, 고정점, 기구, 앱 표시 높이 |
| 6 | 신뢰 가능한 외부 자료 | 근육 위치·형태와 운동역학 교차 검증 |

외부 자료는 로컬 제품 분류를 몰래 바꾸는 근거가 아니다. 분류 오류를 발견하면 이미지에서 임의로 보정하지 말고 데이터 변경으로 분리해 보고한다.

## 3. 폴더 계약

| 경로 | 저장 내용 | 편집 규칙 |
| --- | --- | --- |
| `style-4/source/raw/` | 생성 도구의 원본 출력 | 보존용. 앱에서 직접 사용하지 않음 |
| `style-4/source/` | 투명 배경 앞·뒤 마스터 | 캐릭터 기준 변경 때만 수정 |
| `style-4/layers/` | 근육별 투명 PNG | `muscle-layers.json`과 생성기로 관리 |
| `style-4/canvas/` | 레이어 그룹이 분리된 SVG | 근육 ID를 `<g id>`와 일치시킴 |
| `equipment/source/` | 기구 생성 원본 | 체크무늬가 포함될 수 있음 |
| `equipment/final/` | 투명 합성용 기구 PNG | scene 제작에서 재사용 |
| `exercise-images/source/` | 운동 생성·편집 원본 | 재생성 근거로 보존 |
| `exercise-images/final/` | 투명·정규화된 앱 원본 | scene manifest가 참조 |
| `exercise-images/scenes/` | 운동별 manifest | 앱 자산 생성의 소스 오브 트루스 |
| `exercise-images/archive/` | 폐기하지 않을 이전 시안 | 제품 scene에서는 참조하지 않음 |

모델 자산은 kebab-case 파일명을 사용한다. Android 리소스명과 Java 카탈로그는 Gradle이 scene 파일명으로 자동 생성하므로 수동으로 만들지 않는다.

## 4. 캐릭터 시각 규격

### 4.1 고정 스타일

- 성인형 의료용 운동 마네킹으로 표현한다.
- 몸은 불투명한 흰색 해부학 셸이다.
- 외곽선은 검정, 근육 경계는 얇은 짙은 회색 선이다.
- 머리는 민머리의 단순한 타원형이며 눈·코·입·표정을 묘사하지 않는다.
- 골반은 중립적인 불투명 셸로 처리하고 사실적인 신체 특징을 묘사하지 않는다.
- 피부 질감, 털, 핏줄, 상처, 땀, 의복, 장신구를 넣지 않는다.
- 텍스트, 숫자, 화살표, 설명선, 워터마크, 로고, 바닥 그림자를 넣지 않는다.
- 최종 배경은 실제 알파 투명이다. 생성기의 체크무늬는 최종 배경이 아니다.

### 4.2 색상 규칙

`muscle-layers.json`의 기준 색상은 다음과 같다.

| 의미 | 표현 |
| --- | --- |
| 주 활성 표층 근육 | `#ef4444` 계열, 72% 이상 불투명도, 경계선 유지 |
| 보조 활성 표층 근육 | 같은 빨강 계열, 약 35~50% 불투명도 |
| 비활성 근육 | 흰색, 기존 경계선만 유지 |
| 기능 구획 | 주황 `#f97316` 계열 점선. 독립 근육처럼 명명하지 않음 |
| 심층 투영 | 빨강 30% 수준, 긴 점선 또는 해칭 |
| 건·인대·기준점 | 주황 저불투명도와 짧은 점선. 활성 근육 집계에서 제외 |

주 활성과 보조 활성 그룹이 겹치면 주 활성이 우선한다. 넓은 `overall_*` 그룹 때문에 같은 근육이 보조색으로 덮이지 않도록 집합 차집합을 적용한다.

## 5. 근육 활성도 결정 절차

### 5.1 제품 데이터 해석

새 운동의 정확한 `exerciseId`를 `Fitness_Weight.json`에서 찾고 다음 필드를 기록한다.

- `primarySubPart`: 주 활성 부위 그룹
- `secondarySubParts`: 보조 활성 부위 그룹
- `laterality`: 양측 또는 편측
- `movementPattern`, `motionType`: 자세와 궤도 분류
- `resistanceType`: 프리웨이트, 고정 궤도, 맨몸 등

그룹명은 `style-4/muscle-layers.json`의 `exerciseGroups`를 통해 레이어 ID로 확장한다.

예시:

```text
legs_barbell_back_squat
primary: quads
secondary: glutes, hamstrings, overall_legs

primary layers = quads
secondary layers = union(glutes, hamstrings, overall_legs) - primary layers
```

### 5.2 해부학 검수

한 장의 이미지나 생성 모델의 지식을 근거로 근육 위치를 결정하지 않는다. 신규 장면은 최소한 다음 두 종류의 자료로 교차 검증한다.

1. 해부학 아틀라스, 의과대학, 의료기관 등에서 근육의 기시·정지·표층 노출 위치를 확인한다.
2. 운동역학 또는 공인 운동 지도 자료에서 관절 동작, 주동근, 보조근, 안정근 역할을 확인한다.

검색 결과 이미지를 그대로 복제하지 않는다. 위치와 기능만 검증하고 앱 고유 캐릭터로 다시 표현한다. 검수 근거는 신규 scene에 선택적으로 다음 메타데이터로 남긴다.

```json
"anatomyReview": {
  "reviewedAt": "YYYY-MM-DD",
  "primaryGroups": ["lats"],
  "secondaryGroups": ["biceps", "mid_back"],
  "sources": [
    { "title": "anatomy source", "url": "https://..." },
    { "title": "biomechanics source", "url": "https://..." }
  ],
  "notes": "bar path and visible surface-muscle decisions"
}
```

### 5.3 표시 제한

- 심층 근육은 표층에서 완전히 드러난 빨간 근육처럼 그리지 않는다.
- 장경인대, 아킬레스건 같은 구조는 활성 근육으로 칠하지 않는다.
- 안정근을 모두 빨갛게 칠해 핵심 부위가 흐려지게 하지 않는다.
- 편측 운동은 실제 작업 측을 명확히 정하고 반대쪽을 같은 강도로 칠하지 않는다.
- 근육 강조가 관절·기구·그립을 가리지 않게 한다.

## 6. 기존 에셋 재사용

### 6.1 기구 선택

이미지를 생성하기 전에 `equipment-catalog.json`을 검색한다.

1. `type`이 같은 자산을 찾는다.
2. 카메라와 `viewId`가 같은지 확인한다.
3. 손·좌석·패드·바닥 접점을 `anchors`에 맞춘다.
4. scene의 `equipment` 배열에 정확한 ID를 기록한다.

동일 기구·동일 시점이 있으면 새 기구를 생성하지 않는다. 기존 PNG를 동일한 강체 변환으로 합성한다. 프레임마다 바벨 길이, 원판 크기, 덤벨 헤드, 벤치 패드, 머신 프레임을 다시 그리는 것은 금지한다.

다른 시점이 꼭 필요하면 기존 PNG를 억지로 2D 회전하지 않는다. 같은 `type`의 새 `viewId` 자산을 한 번 만들고 `source/`, `final/`, catalog에 등록한다.

### 6.2 현재 렌더링 경계

현재 Android 앱은 scene의 완성된 프레임 PNG를 표시한다. scene의 앵커와 `renderPolicy`는 생성·검수 메타데이터이며 런타임 스켈레톤이나 3D 리깅 엔진이 아니다.

따라서 기구 재사용은 제작 단계에서 수행하고 최종 프레임으로 평탄화한다. 2프레임 이상에서는 다음 방식이 우선이다.

1. 고정 머신·벤치·배경은 같은 PNG와 같은 좌표로 합성한다.
2. 이동하는 바벨·덤벨은 같은 원본 PNG에 프레임별 강체 변환만 적용한다.
3. 캐릭터만 관절 자세에 맞춰 편집한다.
4. 손과 기구의 접촉부를 마지막에 마스킹·정리한다.

생성 도구가 캐릭터와 기구를 한 번에 다시 그린 결과는 초안으로만 본다.

## 7. 정적 이미지와 동적 이미지
### 7.0 고정 A/B 슬롯
모든 등록 운동 scene은 정확히 두 이미지 슬롯을 순서대로 가진다. 기존의 1프레임·3프레임 선택 기준은 이 슬롯 계약보다 우선하지 않는다.
- `A`: 목록의 64dp 정적 대표 이미지이자 승인 기준 프레임이다.
- `B`: 상세 화면에서 A 다음에 표시하는 보조 동작·자세 이미지다.
- manifest의 `frames`는 반드시 `A`, `B` 순서와 ID를 유지한다. 파일명·배열 위치를 추측하지 않는다.
- B는 A를 편집 입력으로 만들어야 하며, 카메라·고정 기구·고정 앵커를 유지해야 한다.
- A/B 일관성 기준을 통과하지 못하면 scene에 불완전한 슬롯을 등록하지 말고, B를 검수해 교체한 뒤 등록한다.

### 7.1 선택 기준

- 1프레임: 자세 하나만으로 운동을 충분히 이해하거나 프레임 일관성을 확보하지 못한 경우
- 2프레임 이상: 시작·수축 위치가 모두 필요하고 고정 요소를 동일하게 유지할 수 있는 경우

앱은 첫 프레임을 운동 목록의 64dp 대표 이미지로 사용한다. 첫 프레임만 보아도 운동 종류가 식별되어야 한다.

### 7.2 프레임 생성 방식

첫 프레임을 승인한 후 두 번째 프레임을 새 이미지로 독립 생성하지 않는다. 첫 프레임을 편집 입력의 첫 번째 레퍼런스로 사용한다.

프레임별로 다음을 구분한다.

- `renderPolicy.locked`: 캔버스, 카메라, 고정 기구, 머리·몸통·골반 또는 발 접점
- `renderPolicy.animated`: 실제로 움직이는 관절, 사지, 케이블, 바, 웨이트 스택
- `renderPolicy.lockedAnchors`: 모든 프레임에서 같은 화면 좌표를 유지할 점
- `renderPolicy.anchorTolerancePixels`: `lockedAnchors`와 고정 기구에 적용할 허용 오차
- `renderPolicy.lockedEquipment`: A/B에서 identity와 transform을 고정할 placement `instanceId` 목록
- `joints`: 프레임별 관절 좌표
- `movingEquipment`: 이동 기구의 중심과 회전

### 7.3 일관성 합격 기준

신규 제작물에 적용하는 목표값이다.

- 모든 프레임의 캔버스 크기와 카메라 시점이 동일하다.
- 고정 앵커 오차는 긴 변 기준 0.5% 이내다. 1536px 캔버스에서는 약 8px이다.
- 고정 기구 크기 변화는 1% 이내, 회전 변화는 0.5도 이내다.
- 발·좌석·등·손 등 고정 접점이 프레임 사이에서 미끄러지지 않는다.
- 캐릭터의 머리 크기와 골격 비율이 변하지 않는다.
- 움직이는 기구도 같은 제품 형상과 원판 수를 유지한다.
- 프레임 전환 시 확대·축소·좌우 점프처럼 보여서는 안 된다.

기준을 만족하지 못하면 재생성보다 먼저 합성 레이어와 앵커를 수정한다. 그래도 해결되지 않으면 1프레임으로 배포한다.

## 8. 프롬프트 표준

### 8.1 레퍼런스 역할

가능하면 레퍼런스를 세 역할로 제한한다.

1. `front-master` 또는 `back-master`: 캐릭터와 근육 경계
2. 승인된 가장 가까운 운동 final 이미지: 앱 일러스트 톤
3. catalog의 정확한 equipment final 이미지: 기구 형상

레퍼런스가 서로 다른 카메라 시점을 강요하면 하나를 빼거나 맞는 `viewId`를 먼저 제작한다.

### 8.2 기준 프레임 프롬프트

아래 템플릿의 대괄호를 실제 값으로 바꾼다.

```text
Create one scientific exercise-instruction bitmap for [EXERCISE NAME], [FRAME POSITION].

Reference roles:
- Use the anatomy master only for the established white segmented medical-mannequin proportions and muscle boundaries.
- Use the approved exercise image only for the app's line weight and red-highlight language.
- Reuse the equipment reference as the exact [EQUIPMENT ID] design; do not redesign its proportions or parts.

Subject:
A non-sexual adult medical exercise mannequin with an opaque seamless white anatomical shell, black outer contour, thin charcoal muscle-boundary lines, a blank featureless head, and a neutral opaque pelvis shell. No realistic skin or intimate detail.

Pose and camera:
[VIEW ID AND CAMERA]. Full [BODY/SCENE] visible. [PRECISE JOINT ANGLES, CONTACT POINTS, GRIP, BAR OR CABLE PATH]. Use mechanically plausible bilateral joints and a neutral spine unless the exercise requires otherwise.

Muscle overlay:
- Primary: [PRIMARY LAYER IDS], vivid #ef4444 red.
- Secondary: [SECONDARY LAYER IDS AFTER PRIMARY SUBTRACTION], muted pale red.
- Keep every muscle boundary readable. Do not render deep muscles as exposed surface anatomy.

Composition lock:
[CANVAS] canvas, fixed camera, fixed visual scale, generous margins, no crop, no zoom, no floor shadow. True transparent background. Keep [LOCKED EQUIPMENT AND CONTACTS] unchanged for later frames.

Do not add text, arrows, numbers, labels, watermark, facial features, clothing, extra equipment, extra limbs, or checkerboard pixels in the final asset.
```

### 8.3 후속 프레임 편집 프롬프트

```text
Precise pose edit for frame [N] of [EXERCISE NAME].

Preserve the exact canvas, camera projection, mannequin identity, head size, torso proportions, anatomical linework, muscle colors, lighting, margins, and every item in renderPolicy.locked from the reference frame.

Keep these anchors at the same pixel positions: [LOCKED ANCHORS].
Reuse the exact equipment geometry from [EQUIPMENT ID]. Do not redraw, resize, change plate count, or change pad/frame proportions.

Change only: [ANIMATED JOINTS AND MOVING EQUIPMENT].
Target pose: [PRECISE END POSITION, JOINT ACTION, GRIP, BAR/CABLE PATH].

No camera movement, zoom, body translation, changed proportions, extra limbs, text, labels, shadow, or crop. If a locked element cannot be preserved, keep the original element rather than inventing a replacement.
```

### 8.4 기구 신규 시점 프롬프트

```text
Create one reusable isolated equipment asset for [TYPE], viewId [VIEW ID].
Match the established black-line white-equipment technical illustration style.
Show the complete equipment with no crop and no user, labels, logo, room, shadow, or extra plates.
Preserve mechanically plausible joints, cable routing, pad placement, and bilateral symmetry.
Use a transparent background and a stable [CANVAS] canvas.
This is a canonical reusable asset, not an exercise scene.
```

### 8.5 골든 프레임과 좌표 락

스타일 문장만으로는 같은 결과를 재현할 수 없다. 신규 운동은 다음 순서를 하나의 생성 계약으로 사용한다.

1. `Fitness_Weight.json`과 `muscle-layers.json`에서 주·보조 레이어를 확정한다.
2. anatomy master, 가장 가까운 승인 운동 이미지, 정확한 기구 PNG의 세 레퍼런스만 사용해 첫 프레임을 만든다.
3. 첫 프레임을 승인한 즉시 머리·흉골·골반·양발과 운동 중 움직이면 안 되는 관절을 픽셀 좌표로 측정해 `renderPolicy.lockedAnchors`에 기록한다.
4. 후속 프레임의 첫 번째 입력은 반드시 승인 첫 프레임이다. 별도의 텍스트 생성이나 병렬 생성 결과를 후속 프레임으로 사용하지 않는다.
5. 후속 편집 프롬프트에 고정 좌표와 허용 오차를 숫자로 다시 적는다. movable equipment는 compiler가 계산한 `invisibleGripTargets` 픽셀 좌표도 프롬프트에 전달한다. `같은 자세`, `고정` 같은 추상 표현만 사용하지 않는다.
6. 한 번에 하나의 문제만 수정한다. 예를 들어 팔꿈치가 이동했다면 카메라·색·기구·다른 관절을 동시에 다시 요청하지 않는다.

compiler가 출력하는 scene에는 `generationContract`를 필수로 기록한다. 기존 정적 manifest의 `visualContract`와 확장 필드는 별도 legacy coverage 문서로만 유지하며 compiler contract에 섞지 않는다.

- `visualContract`: 시점, 주·보조 레이어, 금지 강조 부위, 자세 규칙
- `generationContract.baseFrame`: 골든 첫 프레임 ID
- `generationContract.derivedFrames`: 각 후속 프레임이 어느 승인 프레임의 편집인지 기록
- `generationContract.promptSpec`: 승인된 레퍼런스 순서·좌표·프롬프트를 기록한 저장소 상대 경로
- `generationContract.referenceAssets`: anatomy, 승인 스타일, 기구 레퍼런스의 저장소 상대 경로
- `generationContract.sourceFiles`: 재검수할 수 있는 생성 원본 경로

후속 프레임에서 고정 앵커가 8px 이상 이동하거나 고정 분절 길이·머리 크기·몸통 폭이 달라지면 실패다. 프롬프트가 규칙을 언급했는지는 합격 근거가 아니며 실제 출력 좌표와 육안 전환을 검수한다.

## 9. 후처리와 scene manifest

### 9.1 후처리

1. 생성 원본을 `exercise-images/source/` 또는 `equipment/source/`에 보존한다.
2. 체크무늬가 픽셀로 구워졌다면 연결된 밝은 배경 추출 도구로 제거한다.
3. 모든 프레임을 같은 캔버스와 같은 정규화 변환으로 맞춘다.
4. `inspect-image-assets.mjs`로 크기, 알파 경계, 보이는 픽셀을 비교한다.
5. 육안으로 고정 앵커와 기구 형상을 비교한다.

체크무늬 제거 도구가 흰색 인체나 기구 내부를 지웠다면 자동 결과를 승인하지 않는다. 알파 경계가 끊긴 부분을 확인하고 다시 추출한다.

### 9.2 scene 필수 필드

```json
{
  "contractType": "exercise-image-orchestration.v1",
  "schemaVersion": 1,
  "exerciseId": "exact_id_from_Fitness_Weight",
  "slug": "exercise-slug",
  "archetypeId": "reviewed-archetype",
  "renderClass": "bodyweight",
  "canvas": { "width": 1536, "height": 1024 },
  "camera": { "viewId": "front" },
  "exerciseMetadata": {
    "movementPattern": "<resolved>", "motionType": "<resolved>", "supportMode": "<resolved>",
    "bodyOrientation": "<resolved>", "equipmentKinematics": "<resolved>", "laterality": "<resolved>",
    "gripVariant": "<resolved>", "equipmentType": "bodyweight"
  },
  "metadataResolution": {
    "fields": {
      "supportMode": "archetype_default", "bodyOrientation": "archetype_default",
      "equipmentKinematics": "archetype_default", "gripVariant": "archetype_default",
      "canonicalView": "archetype_camera"
    },
    "deterministicRule": null, "canonicalViewSource": "archetype_camera"
  },
  "muscleMapping": { "primaryGroup": "<group>", "primaryLayers": ["<layer>"], "secondaryGroups": [], "secondaryLayers": [] },
  "equipment": [],
  "frames": [
    { "id": "A", "file": "../../final/exercise-slug-a.png", "mannequinFile": "exercise-slug-a-mannequin.png", "pose": { "state": "A" }, "equipmentPlacements": [], "invisibleGripTargets": [] },
    { "id": "B", "file": "../../final/exercise-slug-b.png", "mannequinFile": "exercise-slug-b-mannequin.png", "pose": { "state": "B" }, "equipmentPlacements": [], "invisibleGripTargets": [] }
  ],
  "renderPolicy": {
    "lockedJoints": ["shoulders"],
    "animatedJoints": ["elbows"],
    "lockedAnchors": { "pelvis": [768, 486] },
    "anchorTolerancePixels": 8,
    "lockedEquipment": []
  },
  "generationContract": {
    "baseFrame": "A", "derivedFrames": { "B": "edit_from_A" },
    "referenceScene": "../../archetypes/reference.scene.json",
    "equipmentAnchorStrategy": { "type": "none" },
    "prompts": { "A": "prompt-a.md", "B": "prompt-b-edit.md" },
    "renderSteps": ["generate_A_mannequin", "edit_A_into_B"]
  }
}
```

`frame.file`은 scene에서 `final/`로 향하는 상대 경로여야 하며 파일명은 `<slug>-a.png`, `<slug>-b.png`로 고정한다. 맨몸 운동은 `equipment`와 두 frame의 `equipmentPlacements`를 빈 배열로 둔다. `lockedAnchors`, `anchorTolerancePixels`, `lockedEquipment`는 반드시 `renderPolicy` 아래에 둔다.

## 10. 앱 연결 방식

`gradle/exercise-illustration-assets.gradle`이 scene manifest를 읽어 다음을 생성한다.

- 최대 변 768px, 최대 256색의 배포 PNG
- scene 파일명 기반 Android drawable 이름
- 운동 ID별 drawable, frame duration, 표시 높이를 제공하는 `ExerciseIllustrationCatalog`

앱 동작은 다음과 같다.

- 운동 목록: 첫 프레임만 정적 미리보기로 사용
- 운동 상세: 2프레임 이상이면 manifest의 `durationMs`로 반복 재생
- 접근성: 시스템 애니메이션이 꺼지면 정지하고, 사용자가 탭해 재생·일시정지 가능
- 프레임 표시: 각 프레임을 따로 확대하지 않고 전체 프레임의 알파 경계 합집합으로 공통 crop을 사용

그러므로 `app/src/main/res/drawable-nodpi`나 생성 Java 파일을 직접 수정하면 안 된다. 변경은 final PNG와 scene에서 시작한다.

### 10.1 원본과 배포 용량 규격

`exercise-images/final/`에는 투명 원본을 보존한다. 원본을 앱 용량에 맞춰 수동 축소하거나 팔레트를 줄이지 않는다. Gradle 생성 단계가 다음 배포 최적화를 적용한다.

- 긴 변을 최대 768px로 축소
- 알파를 포함한 최대 256색 인덱스 PNG로 변환
- 목록에서는 첫 프레임만 64dp로 표시
- 목록 비트맵은 목표 크기의 2배 수준으로 샘플링하고 8MB LRU 캐시로 재사용

따라서 다른 작업 환경에서도 최종 원본과 scene만 동일하게 유지하면 같은 배포 규격이 재현된다. 생성된 Android 리소스를 별도 원본처럼 복사하거나 커밋하지 않는다.

### 10.2 다른 작업 디렉터리에서 실행

이 표준은 저장소의 절대 위치와 무관하다. 저장소 전체를 다른 디렉터리에 clone하거나 Git worktree로 체크아웃한 뒤 **저장소 루트에서** 명령을 실행하면 같은 자산 구조, 검증, 앱 배포 규격을 사용할 수 있다.

단, `IMAGE_GENERATION_STANDARD.md` 파일 하나만 다른 디렉터리로 복사하는 방식은 지원하지 않는다. 다음 입력과 도구가 함께 있어야 한다.

- `Fitness_Weight.json`
- `model_image/style-4/` 마스터, 레이어 명세와 생성 도구
- `model_image/equipment/` 승인 기구 자산, catalog와 검증 도구
- `model_image/exercise-images/` 승인 이미지, source, final과 scene
- `gradle/exercise-illustration-assets.gradle`과 Android 프로젝트
- 실행 환경에 맞는 Node.js, `sharp`, Java 17, Android SDK

이식할 때는 아래 원칙을 지킨다.

1. 문서나 manifest에 사용자 홈, 드라이브 문자, 특정 worktree의 절대 경로를 기록하지 않는다.
2. 저장소 내부 파일 참조는 scene 위치 기준 상대 경로와 catalog ID를 사용한다.
3. Node.js와 `sharp`의 절대 경로는 각 실행 환경에서 찾되 저장소에는 저장하지 않는다.
4. 기존 승인 캐릭터·기구·첫 프레임은 파일을 그대로 재사용한다. 생성 모델에게 동일한 대상을 다시 그리게 하지 않는다.
5. 새 프레임은 승인 첫 프레임을 편집 입력으로 사용하고 scene의 앵커와 캔버스를 유지한다.
6. 작업 전후에 자산 manifest 검증과 Android 빌드를 실행한다.

이 방식이 보장하는 것은 동일한 폴더 계약, ID, 해부학 색상 규칙, 기구 재사용, 프레임 안정성 검사와 배포 변환이다. 생성형 이미지 모델의 비결정성 때문에 새 이미지를 픽셀 단위로 완전히 동일하게 재생성하는 것까지 보장하지는 않는다. 픽셀 일치가 필요한 요소는 반드시 기존 승인 PNG를 재사용한다.

## 11. 검증 명령

아래 예시의 `<node>`와 `<sharp-module>`은 실행 환경에서 확인한 절대 경로로 바꾼다. 사용자 홈 경로를 문서나 manifest에 저장하지 않는다.

```powershell
$taskNode = '<node>'
$taskSharp = '<sharp-module>'

# 근육 레이어 명세를 바꾼 경우
& $taskNode model_image/style-4/tools/build-layer-assets.mjs $taskSharp

# 이미지 크기·알파 경계 검사
& $taskNode model_image/equipment/tools/inspect-image-assets.mjs `
  $taskSharp `
  model_image/exercise-images/final/example-start.png `
  model_image/exercise-images/final/example-end.png

# 기구 catalog와 모든 scene 검사
$taskScenes = Get-ChildItem model_image/exercise-images/generated/*/scene.json |
  Select-Object -ExpandProperty FullName
& $taskNode model_image/equipment/tools/validate-asset-manifests.mjs `
  $taskSharp `
  model_image/equipment/equipment-catalog.json `
  @taskScenes

# 생성 카탈로그, Android 리소스, 단위 테스트와 빌드
.\gradlew.bat testDebugUnitTest assembleDebug
```

실기기 검증은 빌드 성공과 별개다. 새 이미지를 실제 루틴에 넣어 다음을 확인한다.

- 목록 첫 프레임이 64dp에서도 식별되는가.
- 상세 이미지가 잘리지 않는가.
- 1초 전환에서 확대·축소·기구 변형이 보이지 않는가.
- 주 활성 부위가 작은 화면에서도 보조 부위보다 우선적으로 보이는가.
- 애니메이션 정지 설정과 탭 일시정지가 동작하는가.

## 12. Definition of Done

다음 항목이 모두 충족되어야 완료다.

- [ ] 정확한 `exerciseId`와 주·보조 그룹을 기록했다.
- [ ] 그룹을 실제 근육 레이어로 확장하고 primary 중복을 제거했다.
- [ ] 해부학 위치와 운동역학을 복수 자료로 검수했다.
- [ ] 기존 기구와 같은 `viewId`를 우선 재사용했다.
- [ ] source 원본과 투명 final PNG를 모두 보존했다.
- [ ] 고정 앵커·기구 크기·카메라가 프레임 사이에서 유지된다.
- [ ] 기준을 못 맞춘 동적 이미지는 정적 이미지로 낮췄다.
- [ ] scene manifest에 프레임, duration, 표시 높이, 기구를 기록했다.
- [ ] 자산 검사와 manifest 검증을 통과했다.
- [ ] `testDebugUnitTest assembleDebug`를 통과했다.
- [ ] 실기기 검증 여부와 미검증 범위를 별도로 보고했다.

## 13. 금지 사례

- 기존 바벨이 있는데 프레임마다 다른 바벨을 AI로 다시 생성
- 시작 프레임은 후면 3/4, 종료 프레임은 정후면처럼 카메라 변경
- 주동근보다 안정근을 더 진하게 표시
- 능형근·복횡근 같은 심층 근육을 표층에 노출된 것처럼 채색
- 랫 풀다운 바를 목 뒤로 통과시키거나 케이블 연결을 끊어 표현
- 벤치 패드와 캐릭터 등이 프레임마다 확대·축소
- 생성기의 체크무늬를 투명 배경이라고 간주
- 앱 drawable만 추가하고 source, final, scene을 남기지 않음
- 테스트 성공을 실기기 검증 완료로 표현
