# B edit contract

Edit the approved A mannequin for `{{exerciseNameKo}}` (`{{exerciseId}}`). A is the first and authoritative edit input; B must not be independently generated.

- camera/view to preserve: `{{cameraJson}}`
- B pose: `{{poseBJson}}`
- locked joints to preserve exactly: `{{lockedJointsJson}}`
- only animated joints may change: `{{animatedJointsJson}}`
- locked anchors and tolerance: `{{lockedAnchorsJson}}`

Preserve mannequin identity, body proportions, head size, camera, canvas, linework, muscle colors, and all locked joints. Do not draw or recreate canonical equipment. Use only the compiled invisible grip coordinates for the hand pose; the same canonical equipment identity used for A will be composited later.

If a required value is absent or a locked element cannot be preserved, stop with the matching `MISSING_*` result instead of inventing a replacement.
