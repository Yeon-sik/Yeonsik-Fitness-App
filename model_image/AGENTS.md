# Model image agent rules

이 지침은 `model_image/` 전체에 적용된다. 이미지 생성·편집을 시작하기 전에 [IMAGE_GENERATION_STANDARD.md](IMAGE_GENERATION_STANDARD.md)를 끝까지 읽는다. 운동을 여러 개 만들거나 전체 커버리지·우선순위·재사용 여부를 판단할 때는 [EXERCISE_IMAGE_CATALOG_STANDARD.md](EXERCISE_IMAGE_CATALOG_STANDARD.md)도 끝까지 읽는다.

## 필수 순서

1. `Fitness_Weight.json`에서 정확한 `exerciseId`, `primarySubPart`, `secondarySubParts`, `laterality`, `motionType`을 찾는다.
2. `style-4/muscle-layers.json`에서 운동 부위 그룹을 실제 근육 레이어 ID로 해석한다.
3. 해부학 위치와 운동 동작은 서로 독립적인 신뢰 가능한 외부 자료로 교차 검증한다. 한 장의 참고 이미지나 생성 모델의 기억만 사용하지 않는다.
4. `equipment/equipment-catalog.json`에서 같은 기구와 `viewId`가 있는지 확인한다.
5. 가장 가까운 승인 자산을 시각 레퍼런스로 사용해 첫 프레임을 만든다.
6. 첫 프레임 승인 직후 머리·흉골·골반·발과 운동별 고정 관절의 픽셀 좌표를 scene의 `lockedAnchors`에 먼저 기록한다.
7. 추가 프레임은 첫 프레임을 첫 번째 편집 대상으로 만들고 프롬프트에 고정 좌표를 수치로 반복한다. 두 프레임을 독립 생성하지 않는다.
8. 후속 프레임에서 고정 관절이 8px 이상 이동하거나 상완·대퇴처럼 고정할 분절 길이가 달라지면 승인하지 않고 한 항목만 교정한다.
9. 최종 PNG, scene manifest, 자산 검증, Android 빌드를 함께 완료한다.

## 작업 위치와 이식성

- 저장소 전체를 clone하거나 worktree로 체크아웃한 디렉터리의 루트에서 작업한다.
- 이 지침 파일만 복사해서 작업하지 않는다. `Fitness_Weight.json`, style-4 마스터·레이어, 기구 catalog·승인 PNG, 운동 scene·final PNG와 Gradle 생성 파이프라인이 함께 있어야 한다.
- scene과 catalog에는 저장소 상대 경로와 ID만 기록한다. 사용자 홈이나 특정 worktree의 절대 경로를 커밋하지 않는다.
- 픽셀 일치가 필요한 캐릭터와 기구는 승인 PNG를 그대로 재사용한다. 새 프레임 생성은 승인 첫 프레임을 편집 입력으로 사용한다.

## 절대 규칙

- 캐릭터는 불투명한 흰색 의료용 해부학 마네킹, 검정 외곽선, 얇은 근육 경계선, 무표정·무묘사 얼굴을 유지한다.
- 사실적인 피부·털·혈관·표정·의복·성적 특징·문자·화살표·워터마크·그림자를 넣지 않는다.
- 주 활성 근육은 선명한 빨강, 보조 근육은 같은 계열의 옅은 빨강, 비활성 부위는 흰색으로 표현한다.
- 심층 근육과 건·인대는 표층 근육처럼 채우지 않는다. `kind`에 맞는 심층 투영 또는 기준점 표현을 사용한다.
- 기존 기구 PNG의 비율·원판 수·패드 모양·프레임 폭을 프레임마다 다시 생성하지 않는다.
- 최종 파일에는 체크무늬 배경 픽셀이 남아 있으면 안 된다. 알파가 있는 실제 투명 PNG여야 한다.
- 카메라·캔버스·고정 접점이 흔들리는 2프레임은 정적 1프레임보다 나쁘다. 실패한 동적 이미지는 앱에 연결하지 않는다.
- `app/src/main/res/`에 운동 이미지를 수동 복사하지 않는다. scene manifest에서 Gradle 생성 파이프라인으로 배포 자산을 만든다.

## 자동 생성 Fail-Closed 규칙

- 운동 메타데이터, 근육 매핑, archetype, 카메라, A/B 자세, 기준 scene, 기구 view, anchor 또는 placement를 임의로 추측하거나 기본값으로 채우지 않는다.
- 필수 정보가 없으면 해당 단계에서 중단하고 `MISSING_*` 오류와 누락 필드를 출력한다. 미완성 scene, prompt 또는 이미지를 READY로 취급하지 않는다.
- 합성에는 `equipment/final/` 아래의 `status === "approved"` canonical PNG만 사용한다. `source/`, draft, deprecated 자산은 생성 입력이나 합성 입력이 아니다.
- 이미지 생성 모델은 anatomical mannequin pose만 생성한다. canonical 장비를 다시 그리지 않으며, 장비는 asset-local anchor와 명시적 transform으로 compositor가 합성한다.
- B는 승인된 A를 첫 편집 입력으로 사용하고 animated joint만 변경한다. locked joint, camera, body proportions와 canonical equipment identity는 유지한다.

## 에이전트 작업 보고

- 사용한 운동 ID와 주·보조 근육 그룹
- 재사용한 기구 ID와 `viewId`
- 생성/편집한 source·final·scene 파일
- 해부학 및 동작 검수에서 확인한 핵심 항목
- 실행한 자산 검사·테스트·빌드와 실패 또는 미검증 항목

이미지 생성 도구가 제공되면 해당 도구의 스킬 지침도 먼저 읽는다. 도구가 만든 이미지는 초안이며, 이 저장소의 해부학·기구·프레임 기준을 통과해야 제품 자산이 된다.
