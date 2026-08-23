# Style 4 muscle model

이 폴더는 운동 부위 강조에 사용할 앞·뒤 근육 모델 원본과 해부학 레이어를 보관한다. 현재 명세는 47개 레이어로 구성되며, 표층 근육·기능 구획·심층 투영·해부학 기준점을 서로 다른 표현으로 렌더링한다.

## 구조

- `source/front-master.png`, `source/back-master.png`: 투명 배경 최종 원본
- `source/raw/`: 이미지 생성 결과 보존본
- `canvas/front-layered.svg`, `canvas/back-layered.svg`: `base`와 근육별 `<g>`가 분리된 편집 캔버스
- `layers/front/`, `layers/back/`: 앱에서 원본 위에 합성할 근육별 빨간 PNG 레이어
- `previews/`: 레이어 위치 검수용 색상 지도
- `muscle-layers.json`: 해부학 레이어와 `Fitness_Weight.json` 운동 부위 ID의 연결

## 레이어 원칙

해부학적 근육 레이어와 운동 앱의 기능적 부위 그룹을 구분한다. 예를 들어 `upper_chest`는 독립 근육이 아니라 대흉근 쇄골부에 연결되고, `overall_chest`는 대흉근 관련 레이어의 합집합이다.

레이어 `kind`에 따른 표시 규칙은 다음과 같다.

- `anatomical`, `anatomical_region`, `anatomical_group`: 표층에서 보이는 근육 또는 근육군. 빨간 실선 면으로 표시한다.
- `functional_region`: 운동 목적의 기능 구획. 주황색 점선으로 표시하며 독립 근육명처럼 노출하지 않는다.
- `deep_projection`: 표층에서 직접 보이지 않는 근육. 옅은 해칭과 긴 점선으로 표시하고 UI에 `심층` 배지를 붙인다.
- `anatomical_landmark`: 장경인대·아킬레스건처럼 위치 이해에 필요한 비근육 구조. 운동 활성 근육 집계에서는 제외한다.

복횡근·내복사근·능형근·중간광근처럼 표층에서 직접 보이지 않는 근육은 `deep_projection`으로 분리한다. 이 레이어들은 표층 원본에는 그려 넣지 않고 사용자가 해당 부위를 선택했을 때만 위치 투영으로 표시한다.

## 해부학적 주의사항

- `pectoralis_major_sternocostal_upper/lower`는 별개의 근육이 아니라 대흉근 흉늑부를 운동 앱에서 나눈 기능 구획이다.
- `rear_delt_linked`는 후면 삼각근만 가리킨다. 극하근과 소원근은 `external_rotation` 그룹으로 분리한다.
- `tensor_fasciae_latae`는 골반 가까이의 근복만 표시하고, 대퇴 외측으로 길게 내려가는 구조는 `iliotibial_tract` 기준점으로 분리한다.
- `soleus`는 근복까지만 표시한다. 원위부의 `achilles_tendon`은 근육 활성 레이어에 포함하지 않는다.
- `mid_back`, `upper_back`, `core_stability`는 여러 깊이의 근육을 합친 운동 부위 그룹이므로 단일 근육명으로 표시하지 않는다.

SVG의 근육 그룹은 기본적으로 숨겨져 있다. 디자인 도구에서 원하는 `<g id="...">`를 표시하거나, 앱에서는 동일 ID의 PNG를 원본 위에 합성한다.

자산 생성기는 중복 ID, 빈 경로, 존재하지 않는 운동 그룹 참조를 검사한 뒤 SVG·PNG를 생성한다. 명세에서 제거된 레이어 PNG는 재생성 시 정리한다.
