# Dumbbell Pullover — 재생성 프롬프트 및 합성 명세

## 제품 계약

- `exerciseId`: `chest_dumbbell_pullover`
- 기준 자세: 승인된 `barbell-flat-bench-press-mid.png`의 높은 정면 3/4 카메라와 벤치 지지
- 주 활성: `overall_chest`
- 보조 활성: `lats` — 현재 정면 3/4 시점에서 등 표면을 억지로 노출하지 않음
- 기구: `flat_bench_three_quarter_v1`, `dumbbell_adjustable_three_quarter_v1`
- 프레임: `top`을 골든 프레임으로 만들고 `bottom`은 `top`에서 파생 편집

## 레퍼런스 역할

1. `../final/barbell-flat-bench-press-mid.png`: 첫 프레임 편집 대상. 카메라, 몸통, 골반, 다리, 발, 벤치 지지를 고정한다.
2. `../../style-4/source/front-master.png`: 흰색 의료용 마네킹과 근육 경계 스타일만 참조한다.
3. `../../equipment/final/dumbbell_adjustable_three_quarter.png`: 덤벨의 원판 수, 손잡이, 칼라, 비율을 결정하는 승인 원본이다.

## 골든 `top` 프롬프트

```text
Precisely edit the approved flat-bench press scene into the top/start position
of a dumbbell pullover. Preserve its mannequin identity, head, torso, pelvis,
legs, feet, bench, camera, scale, margins, linework, and pectoralis highlight.
Remove the barbell. Raise both arms above the sternum with elbows softly bent
10–15 degrees. Hold exactly one vertically oriented adjustable dumbbell using
both hands around the same upper plate. Fingers and thumbs must visibly contact
the weight; no floating equipment or separate dumbbells. Keep vivid red only
on pectoralis major and return shoulders and arms to the white anatomical shell.
No text, labels, clothing, facial features, extra limbs, shadow, crop, or zoom.
```

## 파생 `bottom` 프롬프트

```text
Edit only both arms, hands, and the same dumbbell from the approved top frame.
Keep the canvas, camera, head, torso, pelvis, legs, feet, bench, body scale,
linework, and muscle colors unchanged. Sweep both arms together behind the head
in a controlled pullover arc. Upper arms finish near the ears; elbows remain
symmetrically bent 15–20 degrees. Both hands continue supporting the same upper
plate of one vertical dumbbell. The weight moves with the hands and remains clear
of the bench and floor. No torso shift, spine-arch change, shoulder lift, camera
movement, second dumbbell, extra limbs, text, shadow, crop, or zoom.
```

## 생성 기구 제거 프롬프트

두 프레임에서 모델이 그린 덤벨만 제거하고 손·손가락·손목·팔의 좌표를 유지한다.
손은 빈 상단 플레이트를 감싸는 형태로 남겨 정확한 catalog PNG가 뒤에서 합성될 수
있어야 한다. 생성 원본은 다음 경로에 보존한다.

- `../source/dumbbell-pullover-top-generated.png`
- `../source/dumbbell-pullover-bottom-generated.png`

체크무늬는 `extract-connected-light-background.mjs`로 제거한다. `top` 원본의
1537×1023 출력은 1536×1024로 정규화한다. `bottom`은 고정 영역 비교 결과에 따라
캔버스 중심 기준 0.97배, 최종 배치 `left: 27`, `top: 23`으로 정규화한다.

## 정확한 기구 합성

`compose-anchored-equipment.mjs`가 `dumbbell-pullover.composite.json`의 프레임별
배치를 읽는다. 두 프레임 모두 같은 승인 덤벨 PNG를 0.22배, 시계 방향 90°로
강체 변환하고 인체 레이어 뒤에 합성한다.

| frame | catalog anchor | target | scale | rotation |
| --- | --- | ---: | ---: | ---: |
| `top` | `mass_left` | `[697, 72]` | `0.22` | `90°` |
| `bottom` | `mass_left` | `[407, 190]` | `0.22` | `90°` |

`mass_left`는 회전 후 양손이 감싸는 상단 플레이트가 된다. 인체 레이어가 마지막에
합성되므로 손가락이 플레이트를 자연스럽게 가리고, 덤벨은 손과 분리돼 보이지 않는다.

## 검수

- [ ] 한 덤벨만 있으며 두 손이 같은 상단 플레이트에 접촉한다.
- [ ] top은 가슴 위, bottom은 머리 뒤의 명확히 다른 자세다.
- [ ] 머리·몸통·골반·다리·발·벤치·카메라는 고정돼 보인다.
- [ ] 가슴만 선명한 빨강이고 팔·어깨에 잘못된 강조가 없다.
- [ ] 두 프레임의 덤벨 형상·크기·회전은 동일하다.
- [ ] final은 1536×1024 실제 알파 PNG이며 체크무늬 픽셀이 없다.
- [ ] Android drawable과 Java catalog는 Gradle로만 생성한다.
