# Dumbbell Pullover — 통합 합성 프롬프트 명세

## 목적

`chest_dumbbell_pullover`의 두 프레임을 같은 인체·카메라·벤치 계약으로 제작한다.
제품 분류의 1차 부위는 `overall_chest`, 2차 부위는 `lats`이며, 정면 3/4 시점에서
가슴의 활성 부위를 명확히 보여주고 등 쪽 2차 부위를 인위적으로 노출하지 않는다.

## 고정 참조와 산출물

- 인체/스타일 기준: `../../style-4/source/front-master.png`
- 승인된 장면 스타일 기준: `../final/barbell-flat-bench-press.png`
- 승인된 벤치: `../../equipment/final/flat_bench_three_quarter.png`
- 승인된 덤벨: `../../equipment/final/dumbbell_adjustable_three_quarter.png`
- 원본 생성 프레임:
  - `../source/dumbbell-pullover-top-generated.png`
  - `../source/dumbbell-pullover-bottom-generated.png`
- 최종 합성 프레임:
  - `../final/dumbbell-pullover-top.png`
  - `../final/dumbbell-pullover-bottom.png`

원본 생성기는 운동 자세와 인체만 생성한다. 기구는 이미지 생성기가 새로 그리지
않고 `compose-approved-equipment.mjs`가 equipment catalog의 승인된 PNG를
`dumbbell-pullover.composite.json` 좌표로 합성한다. 따라서 덤벨 모양, 벤치, 카메라,
캔버스는 catalog 자산과 장면 계약에서 단일하게 관리된다.

## 공통 생성 프롬프트 — top

```text
Use the supplied approved front anatomical mannequin and flat-bench exercise reference
as the exact visual language. Create a clean medical exercise illustration on a
1536x1024 transparent canvas: one front three-quarter anatomical mannequin lying
supine on a flat weight bench, head toward the rear of the bench and feet fixed on
the floor. Show the contracted/top position of a dumbbell pullover: both arms are
raised above the chest, elbows remain softly bent, both hands meet around one
imaginary dumbbell grip, and the shoulders are not shrugged. Keep head, torso,
pelvis, legs, feet, bench geometry, camera elevation, body scale, line weight,
white-and-light-gray anatomy rendering, and muscle coloration consistent with the
approved flat-bench reference. Highlight the pectoralis major regions in the same
red convention; do not add labels, arrows, text, extra equipment, background, or a
second dumbbell. Leave the dumbbell itself for the deterministic catalog overlay.
Preserve transparent alpha; do not render a checkerboard into the pixels.
```

## 파생 편집 프롬프트 — bottom

```text
Edit the approved top-position figure only. Keep the exact same 1536x1024 canvas,
camera, head center, torso, pelvis, legs, feet, bench alignment, anatomy style,
muscle colors, body proportions, and line weight. Move both slightly bent arms in
one controlled shoulder-extension arc behind the head toward the rear of the bench;
the hands remain together around the same single imaginary dumbbell grip. Do not
change the bench, camera, body scale, or muscle highlight. Do not invent or redraw
any equipment: the approved dumbbell will be composited later. No labels, arrows,
text, extra limbs, extra weights, or background. Preserve transparent alpha and
return a clean 1536x1024 figure-only layer.
```

## 합성 계약

| frame | approved dumbbell `grip_center` target | movement | expected state |
| --- | ---: | --- | --- |
| `top` | `[650, 105]` | arms above sternum | shortened/contracted |
| `bottom` | `[335, 435]` | arms behind head | lengthened/stretched |

The dumbbell is one object held with both hands. The overlay is placed behind the
figure so the hands occlude the grip. The exact catalog anchor and all fixed bench
anchors are recorded in `dumbbell-pullover.scene.json`; no per-frame manual crop or
Android drawable edit is allowed.

## 검수 체크리스트

- [ ] `exerciseId` is `chest_dumbbell_pullover`.
- [ ] Both frames are 1536x1024 and use the same fixed canvas/camera.
- [ ] The bottom frame is an edit derived from the top frame.
- [ ] Exactly one catalog-approved dumbbell is visible in each frame.
- [ ] The hands visibly meet the dumbbell grip in both frames.
- [ ] Bench, head, pelvis, feet, and body scale remain locked.
- [ ] No checkerboard pixels, labels, arrows, text, or extra equipment remain.
- [ ] Final files are transparent PNGs; generated Android resources are produced by
      the repository pipeline and are not edited by hand.
