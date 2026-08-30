import path from "node:path";

export const EXERCISE_SCENE_CONTRACT_TYPE = "exercise-image-orchestration.v1";
export const EXERCISE_SCENE_SCHEMA_VERSION = 1;
export const SCENE_FRAME_IDS = Object.freeze(["A", "B"]);
export const IMAGE_IDENTITY_SOURCES = Object.freeze([
  "exact_visual_variant",
  "family_default",
  "placeholder",
]);

const FINAL_FRAME_REFERENCE_PATTERN = /^(?:\.\.\/)+final\/[a-z0-9]+(?:-[a-z0-9]+)*-(?:a|b)\.png$/;

export function finalFrameFilename(slug, frameId) {
  return `${slug}-${String(frameId).toLowerCase()}.png`;
}

export function finalFrameReference(outputDirectory, finalDirectory, slug, frameId) {
  const expectedName = finalFrameFilename(slug, frameId);
  const file = path.relative(
    outputDirectory,
    path.join(finalDirectory, expectedName),
  ).replaceAll("\\", "/");
  if (!isFinalFrameReference(file, expectedName)) {
    throw new Error(
      `INVALID_FRAME_FILENAME: compiler output must reference ${expectedName} below final/; got ${file}`,
    );
  }
  return file;
}

export function isFinalFrameReference(file, expectedName = null) {
  const normalized = String(file ?? "").replaceAll("\\", "/");
  return FINAL_FRAME_REFERENCE_PATTERN.test(normalized)
    && (expectedName === null || path.posix.basename(normalized) === expectedName);
}

export function isIllustrationKey(value) {
  return typeof value === "string" && /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(value);
}

export function sceneImageSlug(scene) {
  return scene?.imageIdentity?.illustrationKey ?? scene?.slug;
}
