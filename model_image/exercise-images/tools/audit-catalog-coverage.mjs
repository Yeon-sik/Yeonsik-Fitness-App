import fs from "node:fs/promises";
import path from "node:path";

const args = process.argv.slice(2);
const requireCompleteIndex = args.indexOf("--require-complete");
const requireComplete = requireCompleteIndex >= 0;
if (requireComplete) args.splice(requireCompleteIndex, 1);

const [fitnessPath, muscleLayersPath, equipmentCatalogPath, scenesDirectory] = args;
if (!fitnessPath || !muscleLayersPath || !equipmentCatalogPath || !scenesDirectory) {
  throw new Error(
    "Usage: node audit-catalog-coverage.mjs <Fitness_Weight.json> "
      + "<muscle-layers.json> <equipment-catalog.json> <scenes-directory> "
      + "[--require-complete]",
  );
}

const [fitness, muscleLayers, equipmentCatalog] = await Promise.all([
  readJson(fitnessPath),
  readJson(muscleLayersPath),
  readJson(equipmentCatalogPath),
]);

if (!Array.isArray(fitness.exercises)) {
  throw new Error(`${fitnessPath} does not contain an exercises array`);
}

const errors = [];
const warnings = [];
const exerciseById = uniqueMap(fitness.exercises, "id", "exercise", errors);
const muscleGroupIds = new Set(Object.keys(muscleLayers.exerciseGroups ?? {}));
const muscleLayerIds = new Set((muscleLayers.layers ?? []).map((layer) => layer.id));
const equipmentIds = new Set((equipmentCatalog.assets ?? []).map((asset) => asset.id));

for (const exercise of fitness.exercises) {
  for (const groupId of [exercise.primarySubPart, ...(exercise.secondarySubParts ?? [])]) {
    if (!muscleGroupIds.has(groupId)) {
      errors.push(`${exercise.id} references unknown muscle group: ${groupId}`);
    }
  }
}

const sceneNames = (await fs.readdir(scenesDirectory))
  .filter((name) => name.endsWith(".scene.json"))
  .sort();
const scenes = [];
const sceneExerciseIds = new Set();
let frameCount = 0;
let fullySpecifiedSceneCount = 0;

for (const sceneName of sceneNames) {
  const scenePath = path.resolve(scenesDirectory, sceneName);
  const scene = await readJson(scenePath);
  scenes.push(scene);

  if (sceneExerciseIds.has(scene.exerciseId)) {
    errors.push(`Duplicate scene exerciseId: ${scene.exerciseId}`);
  }
  sceneExerciseIds.add(scene.exerciseId);

  const exercise = exerciseById.get(scene.exerciseId);
  if (!exercise) {
    errors.push(`${sceneName} references unknown exerciseId: ${scene.exerciseId}`);
  }
  if (!Array.isArray(scene.frames) || scene.frames.length === 0) {
    errors.push(`${sceneName} has no frames`);
    continue;
  }

  const frameIds = new Set();
  validateFixedImageSlots(sceneName, scene.frames, errors);
  for (const frame of scene.frames) {
    frameCount += 1;
    if (!frame.id || frameIds.has(frame.id)) {
      errors.push(`${sceneName} has missing or duplicate frame id: ${frame.id}`);
    }
    frameIds.add(frame.id);
    if (!frame.file || !(await exists(path.resolve(scenesDirectory, frame.file)))) {
      errors.push(`${sceneName} references missing frame file: ${frame.file}`);
    }
  }

  for (const equipmentId of scene.equipment ?? []) {
    if (!equipmentIds.has(equipmentId)) {
      errors.push(`${sceneName} references unknown equipment asset: ${equipmentId}`);
    }
  }

  validateVisualContract(
    sceneName,
    scene.visualContract,
    exercise,
    muscleLayers.exerciseGroups ?? {},
    muscleGroupIds,
    muscleLayerIds,
    errors,
    warnings,
  );
  await validateGenerationContract(
    sceneName,
    scene.generationContract,
    scene.frames,
    scenesDirectory,
    errors,
    warnings,
  );

  if (!scene.catalogArchetypeId) {
    warnings.push(`${sceneName} has no catalogArchetypeId`);
  }
  if (!Array.isArray(scene.variantTags)) {
    warnings.push(`${sceneName} has no variantTags array`);
  }
  validateAnatomyReview(sceneName, scene.anatomyReview, errors, warnings);

  if (
    scene.catalogArchetypeId
    && Array.isArray(scene.variantTags)
    && scene.visualContract
    && scene.generationContract
    && scene.anatomyReview
  ) {
    fullySpecifiedSceneCount += 1;
  }
}

const missingExerciseIds = fitness.exercises
  .filter((exercise) => !sceneExerciseIds.has(exercise.id))
  .map((exercise) => exercise.id);
if (missingExerciseIds.length > 0) {
  warnings.push(`${missingExerciseIds.length} exercises do not have a scene manifest`);
}
if (requireComplete && missingExerciseIds.length > 0) {
  errors.push(`Catalog is incomplete: ${missingExerciseIds.length} exercise scenes are missing`);
}

const report = {
  valid: errors.length === 0,
  requireComplete,
  inventory: {
    exercises: fitness.exercises.length,
    uiParts: countUnique(fitness.exercises, "uiPart"),
    movementPatterns: countUnique(fitness.exercises, "movementPattern"),
    motionTypes: countUnique(fitness.exercises, "motionType"),
    equipmentCategories: countUnique(fitness.exercises, "equipment"),
    primaryMuscleGroups: countUnique(fitness.exercises, "primarySubPart"),
    usedMuscleGroups: new Set(
      fitness.exercises.flatMap((exercise) => [
        exercise.primarySubPart,
        ...(exercise.secondarySubParts ?? []),
      ]),
    ).size,
  },
  catalog: {
    scenes: scenes.length,
    frames: frameCount,
    fullySpecifiedScenes: fullySpecifiedSceneCount,
    equipmentAssets: equipmentIds.size,
    coveredExercises: sceneExerciseIds.size,
    missingExercises: missingExerciseIds.length,
    coveragePercent: Number(
      ((sceneExerciseIds.size / fitness.exercises.length) * 100).toFixed(2),
    ),
  },
  coverageByMotionType: coverageRows(
    fitness.exercises,
    sceneExerciseIds,
    "motionType",
  ),
  coverageByEquipment: coverageRows(
    fitness.exercises,
    sceneExerciseIds,
    "equipment",
  ),
  coverageByUiPart: coverageRows(
    fitness.exercises,
    sceneExerciseIds,
    "uiPart",
  ),
  warnings,
  errors,
};

console.log(JSON.stringify(report, null, 2));
if (errors.length > 0) process.exitCode = 1;

async function readJson(filePath) {
  return JSON.parse(await fs.readFile(filePath, "utf8"));
}

async function exists(filePath) {
  try {
    await fs.access(filePath);
    return true;
  } catch {
    return false;
  }
}

function uniqueMap(items, key, label, errorList) {
  const result = new Map();
  for (const item of items) {
    const value = item[key];
    if (!value) {
      errorList.push(`${label} has no ${key}`);
      continue;
    }
    if (result.has(value)) errorList.push(`Duplicate ${label} ${key}: ${value}`);
    result.set(value, item);
  }
  return result;
}

function countUnique(items, field) {
  return new Set(items.map((item) => item[field])).size;
}

function coverageRows(exercises, coveredIds, field) {
  const rows = new Map();
  for (const exercise of exercises) {
    const id = exercise[field] ?? "<missing>";
    if (!rows.has(id)) rows.set(id, { id, total: 0, covered: 0 });
    const row = rows.get(id);
    row.total += 1;
    if (coveredIds.has(exercise.id)) row.covered += 1;
  }
  return [...rows.values()]
    .map((row) => ({ ...row, missing: row.total - row.covered }))
    .sort((left, right) => right.total - left.total || left.id.localeCompare(right.id));
}

function validateFixedImageSlots(sceneName, frames, errorList) {
  const requiredSlots = ["A", "B"];
  if (frames.length !== requiredSlots.length) {
    errorList.push(`${sceneName} must contain exactly A and B image slots`);
    return;
  }
  for (const [index, requiredSlot] of requiredSlots.entries()) {
    if (frames[index]?.id !== requiredSlot) {
      errorList.push(
        `${sceneName} image slots must be ordered A then B: ${frames[index]?.id ?? "<missing>"}`,
      );
    }
  }
}

function validateVisualContract(
  sceneName,
  visualContract,
  exercise,
  exerciseGroups,
  validGroupIds,
  validLayerIds,
  errorList,
  warningList,
) {
  if (!visualContract) {
    warningList.push(`${sceneName} has no visualContract`);
    return;
  }
  for (const groupId of [
    ...(visualContract.primaryGroups ?? []),
    ...(visualContract.secondaryGroups ?? []),
  ]) {
    if (!validGroupIds.has(groupId)) {
      errorList.push(`${sceneName} visualContract has unknown group: ${groupId}`);
    }
  }
  for (const groupId of visualContract.forbiddenHighlights ?? []) {
    if (!validGroupIds.has(groupId)) {
      errorList.push(`${sceneName} visualContract has unknown forbidden group: ${groupId}`);
    }
  }
  for (const layerId of [
    ...(visualContract.primaryLayers ?? []),
    ...(visualContract.secondaryLayers ?? []),
  ]) {
    if (!validLayerIds.has(layerId)) {
      errorList.push(`${sceneName} visualContract has unknown layer: ${layerId}`);
    }
  }
  if (exercise && !(visualContract.primaryGroups ?? []).includes(exercise.primarySubPart)) {
    errorList.push(
      `${sceneName} visualContract omits primary group: ${exercise.primarySubPart}`,
    );
  }
  if (exercise) {
    const visualSecondary = new Set(visualContract.secondaryGroups ?? []);
    for (const groupId of exercise.secondarySubParts ?? []) {
      if (!visualSecondary.has(groupId)) {
        warningList.push(`${sceneName} visualContract omits secondary group: ${groupId}`);
      }
    }
    const validWorkingSides = exercise.laterality === "bilateral"
      ? new Set(["both"])
      : new Set(["character_right", "character_left"]);
    if (!validWorkingSides.has(visualContract.workingSide)) {
      errorList.push(
        `${sceneName} has invalid workingSide for ${exercise.laterality}: `
          + `${visualContract.workingSide}`,
      );
    }

    const expectedPrimary = new Set(exerciseGroups[exercise.primarySubPart] ?? []);
    const expectedSecondary = new Set(
      (exercise.secondarySubParts ?? [])
        .flatMap((groupId) => exerciseGroups[groupId] ?? [])
        .filter((layerId) => !expectedPrimary.has(layerId)),
    );
    const actualPrimary = new Set(visualContract.primaryLayers ?? []);
    const actualSecondary = new Set(visualContract.secondaryLayers ?? []);
    const omitted = new Set();
    for (const omission of visualContract.omittedLayers ?? []) {
      if (!omission?.id || !omission?.reason) {
        errorList.push(`${sceneName} has an invalid omittedLayers entry`);
        continue;
      }
      if (!validLayerIds.has(omission.id)) {
        errorList.push(`${sceneName} omits unknown layer: ${omission.id}`);
      }
      if (!expectedPrimary.has(omission.id) && !expectedSecondary.has(omission.id)) {
        errorList.push(`${sceneName} omits a layer outside its product groups: ${omission.id}`);
      }
      omitted.add(omission.id);
    }
    validateLayerSet(
      sceneName,
      "primary",
      expectedPrimary,
      actualPrimary,
      omitted,
      errorList,
    );
    validateLayerSet(
      sceneName,
      "secondary",
      expectedSecondary,
      actualSecondary,
      omitted,
      errorList,
    );
  }
}

function validateLayerSet(
  sceneName,
  role,
  expected,
  actual,
  omitted,
  errorList,
) {
  for (const layerId of actual) {
    if (!expected.has(layerId)) {
      errorList.push(`${sceneName} has unexpected ${role} layer: ${layerId}`);
    }
  }
  for (const layerId of expected) {
    if (!actual.has(layerId) && !omitted.has(layerId)) {
      errorList.push(`${sceneName} is missing ${role} layer or omission: ${layerId}`);
    }
  }
}

function validateAnatomyReview(sceneName, review, errorList, warningList) {
  if (!review) {
    warningList.push(`${sceneName} has no anatomyReview`);
    return;
  }
  if (!Array.isArray(review.sources) || review.sources.length < 2) {
    errorList.push(`${sceneName} anatomyReview must contain at least two sources`);
    return;
  }
  for (const source of review.sources) {
    if (!source?.title || !/^https:\/\//.test(source?.url ?? "")) {
      errorList.push(`${sceneName} anatomyReview has an invalid source`);
    }
  }
}

async function validateGenerationContract(
  sceneName,
  generationContract,
  frames,
  scenesDirectory,
  errorList,
  warningList,
) {
  if (!generationContract) {
    warningList.push(`${sceneName} has no generationContract`);
    return;
  }
  const frameIds = new Set(frames.map((frame) => frame.id));
  if (!frameIds.has(generationContract.baseFrame)) {
    errorList.push(`${sceneName} generationContract has unknown baseFrame`);
  }
  for (const frameId of Object.keys(generationContract.derivedFrames ?? {})) {
    if (!frameIds.has(frameId)) {
      errorList.push(`${sceneName} generationContract has unknown derived frame: ${frameId}`);
    }
  }
  const referencedFiles = [
    generationContract.promptSpec,
    ...Object.values(generationContract.referenceAssets ?? {}),
    ...Object.values(generationContract.sourceFiles ?? {}),
  ].filter(Boolean);
  for (const relativePath of referencedFiles) {
    if (!(await exists(path.resolve(scenesDirectory, relativePath)))) {
      errorList.push(`${sceneName} generationContract file is missing: ${relativePath}`);
    }
  }
}
