# Dumbbell curl generation prompt

이 파일은 `dumbbell-curl.scene.json`의 생성 계약이다. 공통 스타일 설명만 복사하지 말고 아래 레퍼런스 순서와 좌표를 함께 사용한다.

## 레퍼런스 순서

### 기준 프레임

1. `../../style-4/source/front-master.png`: 해부학 비율과 근육 경계
2. `../final/dumbbell-shoulder-press-bottom.png`: 승인된 앱 선화와 채색 언어
3. `../../equipment/final/dumbbell_adjustable_three_quarter.png`: 덤벨 형상

### 수축 프레임

1. `../source/dumbbell-curl-bottom-generated.png`: 반드시 첫 번째인 편집 대상
2. `../../equipment/final/dumbbell_adjustable_three_quarter.png`: 덤벨 형상
3. `../../style-4/source/front-master.png`: 해부학 보조 기준

## 기준 프레임 프롬프트

```text
Use case: scientific-educational
Asset type: Yeonsik Fitness App exercise motion frame, frame 1 of 2
Primary request: Create the starting position of a bilateral standing dumbbell curl as a transparent scientific anatomy exercise illustration.

Use the anatomy master for the established adult medical-mannequin proportions, blank head, white anatomical shell, and muscle boundaries. Match the approved exercise image's thin black contour, fine charcoal hatching, flat red overlay, and transparent cutout style. Reuse matching copies of the canonical adjustable plate-and-collar dumbbell; preserve its plate count, collars, handle texture, proportions, and line style.

Show one full-body mannequin on a 1536x1024 landscape canvas from a near-frontal, slightly elevated view. Stand tall with feet hip-width and planted, pelvis neutral, spine upright, shoulders down, and upper arms vertical beside the rib cage. Keep elbows almost extended but not locked, forearms supinated, palms forward, wrists neutral, and dumbbells beside the outer thighs.

Primary vivid #ef4444: bilateral biceps_brachii_long_head, biceps_brachii_short_head, and brachialis. Secondary pale red: bilateral forearm_flexors and brachioradialis. Keep all other muscles white, including shoulders, chest, triceps, torso, and legs.

Use a genuinely transparent background, equal margins, and no crop. No text, arrows, labels, watermark, facial features, clothing, realistic skin, shadow, extra equipment, extra plates, or extra limbs.
```

## 수축 프레임 프롬프트

```text
Use case: precise-object-edit
Asset type: frame 2 of the same bilateral standing dumbbell curl
Primary request: Edit the approved bottom frame into the contracted position. Never generate this frame independently.

Preserve the exact 1536x1024 canvas, camera, head center (756,85), sternum center (768,253), pelvis center (768,486), left foot (670,944), right foot (834,944), body scale, torso, legs, linework, muscle colors, margins, and transparent background.

Keep the upper arms vertical against the rib cage. Keep elbow centers at left (628,395) and right (884,395), within 8 pixels. Change only both forearms, wrists, hands, and dumbbells. Flex the elbows to about 115 degrees. Put wrist centers near left (610,275) and right (908,275), with supinated forearms and neutral wrists. Put matching dumbbell centers near left (585,263) and right (935,263). Preserve identical dumbbell scale, plate count, collars, handles, and line style; only rigid translation or rotation is allowed.

Primary vivid #ef4444 remains only on bilateral biceps brachii and brachialis. Secondary pale red remains only on forearm flexors and brachioradialis. Do not add red to shoulders, chest, triceps, torso, or legs.

No torso lean, shoulder lift, elbow drift, shortened upper arm, wrist bend, dumbbells beside the ears, camera change, zoom, crop, extra limbs, text, labels, logo, watermark, or shadow.
```

## 승인 메모

- 독립적으로 만든 수축 프레임은 사용하지 않는다.
- 첫 수축 시안은 팔꿈치가 위로 이동해 상완이 짧아졌으므로 폐기했다.
- 최종 수축 프레임은 팔꿈치를 scene 좌표로 되돌리는 단일 교정 후 승인했다.
- 생성 결과의 체크무늬는 원본에만 보존하고 `extract-connected-light-background.mjs`로 실제 알파 final PNG를 만든다.
