# A mannequin generation contract

Generate only the anatomical mannequin for `{{exerciseNameKo}}` (`{{exerciseId}}`).

Use the exact camera and A pose from the compiled contract below. Do not infer omitted values.

- camera/view: `{{cameraJson}}`
- A pose: `{{poseAJson}}`
- locked joints: `{{lockedJointsJson}}`
- animated joints: `{{animatedJointsJson}}`
- primary muscle layers: `{{primaryLayersJson}}`
- secondary muscle layers: `{{secondaryLayersJson}}`

Do not draw or recreate canonical dumbbells, barbells, benches, machines, cable attachments, or other equipment. For movable free weights, shape the hands around the compiled invisible grip coordinates only. Canonical equipment is composited later from approved `equipment/final` PNG files.

Keep a true transparent background. Do not add text, arrows, labels, watermarks, shadows, clothing, facial features, extra limbs, or unrequested objects.
