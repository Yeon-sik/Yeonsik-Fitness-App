#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";
import {
  ERROR_CODES,
  fail,
  isDirectRun,
  missingFields,
  normalizeExerciseName,
  pathExists,
  PipelineError,
  printPipelineError,
  readJson,
  writeJson,
} from "../../lib/pipeline-contract.mjs";
import {
  findDeterministicMapping,
  resolveArchetypeId,
  resolveEquipmentPlacements,
  resolveExerciseMetadata,
} from "../../lib/metadata-resolution.mjs";
import {
  EXERCISE_SCENE_CONTRACT_TYPE,
  EXERCISE_SCENE_SCHEMA_VERSION,
  finalFrameReference,
} from "../../lib/scene-contract.mjs";

const REQUIRED_METADATA_FIELDS = [
  "id", "nameKo", "slug", "movementPattern", "motionType", "equipmentKinematics",
  "laterality", "primarySubPart", "secondarySubParts", "equipmentType",
];
const RENDER_CLASSES = new Set([
  "bodyweight", "movable_free_weight", "fixed_support", "fixed_machine", "cable_machine",
]);

export function defaultCompilePaths(repositoryRoot) {
  const modelImage = path.join(repositoryRoot, "model_image");
  return {
    exerciseCatalog: path.join(modelImage, "data", "exercise-catalog.json"),
    nameIndex: path.join(modelImage, "data", "exercise-name-index.json"),
    overrides: path.join(modelImage, "data", "exercise-overrides.json"),
    archetypes: path.join(modelImage, "archetypes", "archetype-registry.json"),
    deterministicMapping: path.join(modelImage, "archetypes", "deterministic-mapping.json"),
    equipmentCatalog: path.join(modelImage, "equipment", "equipment-catalog.json"),
    muscleLayers: path.join(modelImage, "style-4", "muscle-layers.json"),
    templateA: path.join(modelImage, "exercise-images", "templates", "mannequin-a.prompt.md"),
    templateB: path.join(modelImage, "exercise-images", "templates", "mannequin-b-edit.prompt.md"),
    outputRoot: path.join(modelImage, "exercise-images", "generated"),
    finalDirectory: path.join(modelImage, "exercise-images", "final"),
  };
}

export async function loadCompileContext(paths) {
  const [exerciseCatalog, nameIndex, overrides, archetypes, muscleLayers] = await Promise.all([
    readJson(paths.exerciseCatalog, ERROR_CODES.MISSING_EXERCISE_METADATA),
    readJson(paths.nameIndex, ERROR_CODES.MISSING_EXERCISE_METADATA),
    readJson(paths.overrides, ERROR_CODES.MISSING_OVERRIDE),
    readJson(paths.archetypes, ERROR_CODES.MISSING_ARCHETYPE),
    readJson(paths.muscleLayers, ERROR_CODES.MISSING_MUSCLE_MAPPING),
  ]);
  const deterministicMapping = paths.deterministicMapping && await pathExists(paths.deterministicMapping)
    ? await readJson(paths.deterministicMapping, ERROR_CODES.MISSING_DETERMINISTIC_MAPPING)
    : { schemaVersion: 1, candidateKeyFields: [], rules: [] };
  if (!(await pathExists(paths.equipmentCatalog))) {
    fail(ERROR_CODES.MISSING_EQUIPMENT_CATALOG, "Canonical equipment catalog is missing", {
      expected: paths.equipmentCatalog,
      policy: "No fallback catalog filename is selected automatically",
    });
  }
  const equipmentCatalog = await readJson(paths.equipmentCatalog, ERROR_CODES.MISSING_EQUIPMENT_CATALOG);
  return { exerciseCatalog, nameIndex, overrides, archetypes, deterministicMapping, equipmentCatalog, muscleLayers, paths };
}

export function resolveExerciseId(exerciseName, nameIndex) {
  const normalized = normalizeExerciseName(exerciseName);
  const matches = nameIndex.aliases?.[normalized] ?? [];
  if (matches.length !== 1) {
    fail(ERROR_CODES.MISSING_EXERCISE_METADATA, matches.length === 0
      ? `Exercise name is not registered: ${exerciseName}`
      : `Exercise name is ambiguous: ${exerciseName}`, { normalized, matches });
  }
  return matches[0];
}

export async function inspectExerciseReadiness(exerciseId, context) {
  const exercise = context.exerciseCatalog.exercises?.find((item) => item.id === exerciseId);
  if (!exercise) return issue(ERROR_CODES.MISSING_EXERCISE_METADATA, { exerciseId });
  const metadataMissing = missingFields(exercise, REQUIRED_METADATA_FIELDS);
  if (!Array.isArray(exercise.secondarySubParts)) metadataMissing.push("secondarySubParts");
  if (metadataMissing.length > 0) {
    return issue(ERROR_CODES.MISSING_EXERCISE_METADATA, { exerciseId, fields: [...new Set(metadataMissing)] });
  }

  const override = context.overrides?.exercises?.[exerciseId] ?? {};
  const deterministic = findDeterministicMapping(
    exercise,
    context.deterministicMapping ?? context.deterministicMappings ?? {},
  );
  const archetypeId = resolveArchetypeId({ exercise, override, deterministic });
  const archetype = context.archetypes.archetypes?.[archetypeId];
  if (!archetypeId || !archetype) {
    return issue(ERROR_CODES.MISSING_ARCHETYPE, {
      exerciseId,
      archetypeId: archetypeId ?? null,
      deterministicRule: deterministic.rule?.id ?? null,
    });
  }

  const metadataResolution = resolveExerciseMetadata({ exercise, override, archetype, deterministic });
  if (metadataResolution.missingFields.length > 0 || metadataResolution.conflicts.length > 0) {
    return issue(ERROR_CODES.MISSING_EXERCISE_METADATA, {
      exerciseId,
      fields: [...new Set([...metadataResolution.missingFields, ...metadataResolution.conflicts])],
      sources: metadataResolution.sources,
    });
  }

  const missingArchetype = missingFields(archetype, [
    "camera", "poses", "lockedJoints", "animatedJoints", "renderClass",
    "canonicalReferenceScene", "equipmentAnchorStrategy", "canvas",
  ]);
  if (missingArchetype.length > 0 || !archetype.poses?.A || !archetype.poses?.B || !RENDER_CLASSES.has(archetype.renderClass)) {
    return issue(ERROR_CODES.MISSING_ARCHETYPE, { exerciseId, archetypeId, fields: missingArchetype });
  }
  const referencePath = path.resolve(path.dirname(context.paths.archetypes), archetype.canonicalReferenceScene);
  if (!(await pathExists(referencePath))) {
    return issue(ERROR_CODES.MISSING_REFERENCE_SCENE, { exerciseId, archetypeId, referenceScene: referencePath });
  }

  const muscleGroups = context.muscleLayers.exerciseGroups ?? {};
  const requestedGroups = [exercise.primarySubPart, ...exercise.secondarySubParts];
  const missingGroups = requestedGroups.filter((group) => !Array.isArray(muscleGroups[group]));
  if (missingGroups.length > 0) return issue(ERROR_CODES.MISSING_MUSCLE_MAPPING, { exerciseId, groups: missingGroups });

  const placementResolution = resolveEquipmentPlacements({ exercise, override, archetype, deterministic });
  if (placementResolution.invalid.length > 0) {
    return issue(ERROR_CODES.INVALID_CONTRACT, {
      exerciseId,
      field: "equipmentPlacements.A/B",
      invalidFrames: placementResolution.invalid,
    });
  }
  if (placementResolution.missingRecipe) {
    return issue(ERROR_CODES.MISSING_PLACEMENT_RECIPE, {
      exerciseId,
      equipmentType: exercise.equipmentType,
      archetypeId,
    });
  }
  const equipmentById = new Map((context.equipmentCatalog.assets ?? []).map((asset) => [asset.id, asset]));
  const normalizedPlacements = { A: [], B: [] };
  for (const frameId of ["A", "B"]) {
    for (const [placementIndex, placement] of placementResolution.frames[frameId].entries()) {
      const asset = equipmentById.get(placement.equipmentId);
      if (!asset || asset.status !== "approved" || !asset.renderClass
        || !String(asset.file ?? "").replaceAll("\\", "/").startsWith("final/")) {
        return issue(ERROR_CODES.MISSING_EQUIPMENT, {
          exerciseId,
          frameId,
          placementIndex,
          equipmentId: placement.equipmentId,
          status: asset?.status ?? null,
          renderClass: asset?.renderClass ?? null,
        });
      }
      if (placement.viewId !== undefined && asset.viewId !== placement.viewId) {
        return issue(ERROR_CODES.MISSING_EQUIPMENT_VIEW, {
          exerciseId, frameId, placementIndex, equipmentId: placement.equipmentId,
          requested: placement.viewId, available: asset.viewId ?? null,
        });
      }
      if (!placement.anchor || !Array.isArray(asset.anchors?.[placement.anchor])) {
        return issue(ERROR_CODES.MISSING_EQUIPMENT, {
          exerciseId, frameId, placementIndex, equipmentId: placement.equipmentId,
          anchor: placement.anchor ?? null,
        });
      }
      const missingPlacement = missingFields(placement, ["target", "scale", "rotationDegrees", "z"]);
      if (missingPlacement.length > 0) {
        return issue(ERROR_CODES.MISSING_PLACEMENT, { exerciseId, frameId, placementIndex, fields: missingPlacement });
      }
      if (!Array.isArray(placement.target) || placement.target.length !== 2 || !placement.target.every(Number.isFinite)
        || placement.target[0] < 0 || placement.target[0] > archetype.canvas.width
        || placement.target[1] < 0 || placement.target[1] > archetype.canvas.height
        || !Number.isFinite(placement.scale) || placement.scale <= 0
        || !Number.isFinite(placement.rotationDegrees) || !Number.isFinite(placement.z) || placement.z === 0) {
        return issue(ERROR_CODES.INVALID_CONTRACT, { exerciseId, frameId, placementIndex });
      }
      normalizedPlacements[frameId].push({
        ...placement,
        instanceId: placement.instanceId ?? `${placement.equipmentId}#${placementIndex}`,
        viewId: asset.viewId,
      });
    }
    if (new Set(normalizedPlacements[frameId].map((placement) => placement.instanceId)).size
      !== normalizedPlacements[frameId].length) {
      return issue(ERROR_CODES.MISSING_PLACEMENT, { exerciseId, frameId, field: "unique instanceId" });
    }
  }
  if (exercise.equipmentType !== "bodyweight" && normalizedPlacements.A.length === 0) {
    return issue(ERROR_CODES.MISSING_PLACEMENT, { exerciseId, field: "equipmentPlacements.A/B" });
  }
  if (!sameEquipmentIdentity(normalizedPlacements.A, normalizedPlacements.B)) {
    return issue(ERROR_CODES.MISSING_PLACEMENT, { exerciseId, field: "A/B equipment identity" });
  }

  const lockedAnchors = firstDefined(
    own(override, "lockedAnchors") ? override.lockedAnchors : undefined,
    own(archetype, "lockedAnchors") ? archetype.lockedAnchors : undefined,
    archetype.renderPolicy?.lockedAnchors,
    deterministic.values?.lockedAnchors,
  );
  if (!lockedAnchors || typeof lockedAnchors !== "object" || Array.isArray(lockedAnchors)) {
    return issue(ERROR_CODES.MISSING_LOCKED_ANCHORS, { exerciseId, archetypeId });
  }
  const rawTolerance = firstDefined(
    own(override, "anchorTolerancePixels") ? override.anchorTolerancePixels : undefined,
    own(archetype, "anchorTolerancePixels") ? archetype.anchorTolerancePixels : undefined,
    archetype.renderPolicy?.anchorTolerancePixels,
    deterministic.values?.anchorTolerancePixels,
  );
  const tolerance = resolveTolerance(rawTolerance, archetype.canvas);
  if (!Number.isFinite(tolerance) || tolerance < 0) {
    return issue(ERROR_CODES.MISSING_ANCHOR_TOLERANCE, { exerciseId, archetypeId });
  }
  const equipmentIds = [...new Set(normalizedPlacements.A.map((placement) => placement.equipmentId))];
  const lockedEquipment = firstDefined(
    own(override, "lockedEquipment") ? override.lockedEquipment : undefined,
    own(archetype, "lockedEquipment") ? archetype.lockedEquipment : undefined,
    archetype.renderPolicy?.lockedEquipment,
    deterministic.values?.lockedEquipment,
    normalizedPlacements.A.filter((placement) => placement.locked === true).map((placement) => placement.instanceId),
    [],
  );
  if (!Array.isArray(lockedEquipment)) {
    return issue(ERROR_CODES.MISSING_LOCKED_EQUIPMENT, { exerciseId, archetypeId });
  }
  return {
    ready: true,
    code: "READY",
    exercise,
    override,
    archetype,
    deterministic,
    metadataResolution,
    placementResolution,
    normalizedPlacements,
    lockedAnchors,
    tolerance,
    equipmentIds,
    lockedEquipment,
    referencePath,
  };
}

export async function compileExercise({ exerciseName, context, outputDirectory }) {
  const exerciseId = resolveExerciseId(exerciseName, context.nameIndex);
  const readiness = await inspectExerciseReadiness(exerciseId, context);
  if (!readiness.ready) fail(readiness.code, `Exercise is not ready: ${exerciseId}`, readiness.details);
  const {
    exercise,
    override,
    archetype,
    deterministic,
    metadataResolution,
    normalizedPlacements,
    lockedAnchors,
    tolerance,
    equipmentIds,
    lockedEquipment,
    referencePath,
  } = readiness;
  const muscleMapping = buildMuscleMapping(exercise, context.muscleLayers.exerciseGroups);
  const output = outputDirectory ?? path.join(context.paths.outputRoot, exercise.slug);
  const finalDirectory = context.paths.finalDirectory
    ?? path.resolve(context.paths.outputRoot, "..", "final");
  const invisibleGripTargets = {
    A: buildInvisibleGripTargets(normalizedPlacements.A, archetype),
    B: buildInvisibleGripTargets(normalizedPlacements.B, archetype),
  };

  const variables = {
    exerciseNameKo: exercise.nameKo,
    exerciseId,
    cameraJson: JSON.stringify(archetype.camera),
    poseAJson: JSON.stringify(archetype.poses.A),
    poseBJson: JSON.stringify(archetype.poses.B),
    lockedJointsJson: JSON.stringify(archetype.lockedJoints),
    animatedJointsJson: JSON.stringify(archetype.animatedJoints),
    primaryLayersJson: JSON.stringify(muscleMapping.primaryLayers),
    secondaryLayersJson: JSON.stringify(muscleMapping.secondaryLayers),
    lockedAnchorsJson: JSON.stringify({ anchors: lockedAnchors, tolerancePixels: tolerance }),
    invisibleGripTargetsAJson: JSON.stringify(invisibleGripTargets.A),
    invisibleGripTargetsBJson: JSON.stringify(invisibleGripTargets.B),
    invisibleGripTargetsJson: JSON.stringify(invisibleGripTargets),
  };
  const [templateA, templateB] = await Promise.all([
    fs.readFile(context.paths.templateA, "utf8"),
    fs.readFile(context.paths.templateB, "utf8"),
  ]);
  const promptA = renderTemplate(templateA, variables);
  const promptB = renderTemplate(templateB, variables);
  const exerciseMetadata = {
    movementPattern: exercise.movementPattern,
    motionType: exercise.motionType,
    supportMode: metadataResolution.metadata.supportMode,
    bodyOrientation: metadataResolution.metadata.bodyOrientation,
    equipmentKinematics: metadataResolution.metadata.equipmentKinematics,
    laterality: exercise.laterality,
    gripVariant: metadataResolution.metadata.gripVariant,
    equipmentType: exercise.equipmentType,
  };
  if (!metadataResolution.canonicalViewFromCamera) {
    exerciseMetadata.canonicalView = metadataResolution.metadata.canonicalView;
  }
  const scene = {
    contractType: EXERCISE_SCENE_CONTRACT_TYPE,
    schemaVersion: EXERCISE_SCENE_SCHEMA_VERSION,
    exerciseId,
    nameKo: exercise.nameKo,
    slug: exercise.slug,
    archetypeId: resolveArchetypeId({ exercise, override, deterministic }),
    renderClass: archetype.renderClass,
    canvas: archetype.canvas,
    camera: archetype.camera,
    exerciseMetadata,
    metadataResolution: {
      fields: metadataResolution.sources,
      deterministicRule: deterministic.rule?.id ?? null,
      canonicalViewSource: metadataResolution.sources.canonicalView,
    },
    muscleMapping,
    equipment: equipmentIds,
    frames: [
      frameContract("A", exercise.slug, archetype.poses.A, normalizedPlacements.A, invisibleGripTargets.A, output, finalDirectory),
      frameContract("B", exercise.slug, archetype.poses.B, normalizedPlacements.B, invisibleGripTargets.B, output, finalDirectory),
    ],
    renderPolicy: {
      lockedJoints: archetype.lockedJoints,
      animatedJoints: archetype.animatedJoints,
      lockedAnchors,
      anchorTolerancePixels: tolerance,
      lockedEquipment,
    },
    generationContract: {
      baseFrame: "A",
      derivedFrames: { B: "edit_from_A" },
      referenceScene: path.relative(output, referencePath).replaceAll("\\", "/"),
      equipmentAnchorStrategy: archetype.equipmentAnchorStrategy,
      prompts: { A: "prompt-a.md", B: "prompt-b-edit.md" },
      invisibleGripTargets,
      renderSteps: renderSteps(archetype.renderClass),
    },
  };
  await fs.mkdir(output, { recursive: true });
  await Promise.all([
    writeJson(path.join(output, "scene.json"), scene),
    writeJson(path.join(output, "placements-a.json"), normalizedPlacements.A),
    writeJson(path.join(output, "placements-b.json"), normalizedPlacements.B),
    fs.writeFile(path.join(output, "prompt-a.md"), promptA, "utf8"),
    fs.writeFile(path.join(output, "prompt-b-edit.md"), promptB, "utf8"),
  ]);
  return { scene, outputDirectory: output };
}

function issue(code, details) {
  return { ready: false, code, details };
}

function own(object, key) {
  return object !== null && typeof object === "object" && Object.prototype.hasOwnProperty.call(object, key);
}

function firstDefined(...values) {
  return values.find((value) => value !== undefined && value !== null);
}

function resolveTolerance(value, canvas) {
  if (Number.isFinite(value)) return value;
  if (!value || typeof value !== "object") return null;
  const amount = Number(value.value ?? value.ratio ?? value.percent);
  if (!Number.isFinite(amount)) return null;
  if (["long_side_percent", "canvas_long_side_percent"].includes(value.mode)) {
    return Math.max(canvas.width, canvas.height) * amount;
  }
  return null;
}

function buildMuscleMapping(exercise, groups) {
  const primaryLayers = [...new Set(groups[exercise.primarySubPart] ?? [])];
  const primary = new Set(primaryLayers);
  const secondaryLayers = [...new Set(
    exercise.secondarySubParts.flatMap((group) => groups[group] ?? []).filter((layer) => !primary.has(layer)),
  )];
  return {
    primaryGroup: exercise.primarySubPart,
    primaryLayers,
    secondaryGroups: exercise.secondarySubParts,
    secondaryLayers,
  };
}

function sameEquipmentIdentity(left, right) {
  const ids = (placements) => placements
    .map((item) => `${item.instanceId}|${item.equipmentId}|${item.viewId}`)
    .sort();
  return JSON.stringify(ids(left)) === JSON.stringify(ids(right));
}

function renderTemplate(template, variables) {
  return template.replace(/\{\{([A-Za-z0-9]+)\}\}/g, (_, key) => {
    if (!(key in variables)) fail(ERROR_CODES.INVALID_CONTRACT, `Unknown prompt template variable: ${key}`);
    return String(variables[key]);
  });
}

function buildInvisibleGripTargets(equipmentPlacements, archetype) {
  const strategy = archetype?.equipmentAnchorStrategy?.type;
  if (strategy !== "invisible_grip") return [];
  return equipmentPlacements.map((placement) => ({
    instanceId: placement.instanceId,
    equipmentId: placement.equipmentId,
    anchor: placement.anchor,
    target: [...placement.target],
  }));
}

function frameContract(id, slug, pose, equipmentPlacements, invisibleGripTargets, outputDirectory, finalDirectory) {
  const lower = id.toLowerCase();
  return {
    id,
    file: finalFrameReference(outputDirectory, finalDirectory, slug, id),
    mannequinFile: `${slug}-${lower}-mannequin.png`,
    pose,
    equipmentPlacements,
    invisibleGripTargets,
  };
}

function renderSteps(renderClass) {
  const steps = {
    bodyweight: ["generate_A_mannequin", "validate_A", "edit_A_into_B", "validate_B"],
    movable_free_weight: ["generate_A_mannequin_with_invisible_grips", "composite_canonical_equipment_A", "validate_A", "edit_A_into_B", "composite_same_canonical_equipment_B", "validate_B"],
    fixed_support: ["place_canonical_support", "generate_mannequin_pose", "composite_support_behind_mannequin", "validate_A", "edit_A_into_B", "reuse_support_transform", "validate_B"],
    fixed_machine: ["place_machine_back", "generate_mannequin_pose", "place_machine_front_occluder", "validate_A", "edit_A_into_B", "reuse_machine_identity", "validate_B"],
    cable_machine: ["place_machine", "generate_mannequin_pose", "place_canonical_attachment", "draw_vector_cable", "place_front_occluder", "validate_A", "edit_A_into_B", "reuse_machine_and_attachment_identity", "validate_B"],
  };
  return steps[renderClass];
}

function parseArguments(argv, defaults) {
  const result = { paths: { ...defaults }, exerciseName: null, outputDirectory: null };
  const pathFlags = {
    "--exercise-catalog": "exerciseCatalog", "--name-index": "nameIndex",
    "--overrides": "overrides", "--archetypes": "archetypes",
    "--deterministic-mapping": "deterministicMapping",
    "--equipment-catalog": "equipmentCatalog", "--muscle-layers": "muscleLayers",
    "--template-a": "templateA", "--template-b": "templateB", "--output-root": "outputRoot",
    "--final-directory": "finalDirectory",
  };
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    if (pathFlags[value]) result.paths[pathFlags[value]] = path.resolve(argv[++index]);
    else if (value === "--out") result.outputDirectory = path.resolve(argv[++index]);
    else if (!result.exerciseName) result.exerciseName = value;
    else throw new Error(`Unexpected argument: ${value}`);
  }
  if (!result.exerciseName) throw new Error("Usage: node compile-exercise.mjs <exercise-name> [--equipment-catalog <path>] [--out <directory>]");
  return result;
}

async function main() {
  const toolsDirectory = path.dirname(fileURLToPath(import.meta.url));
  const repositoryRoot = path.resolve(toolsDirectory, "..", "..", "..");
  const args = parseArguments(process.argv.slice(2), defaultCompilePaths(repositoryRoot));
  const context = await loadCompileContext(args.paths);
  const result = await compileExercise({ exerciseName: args.exerciseName, context, outputDirectory: args.outputDirectory });
  console.log(JSON.stringify({ ok: true, exerciseId: result.scene.exerciseId, outputDirectory: result.outputDirectory }, null, 2));
}

if (isDirectRun(import.meta.url)) {
  main().catch((error) => {
    printPipelineError(error);
    process.exitCode = error instanceof PipelineError ? 2 : 1;
  });
}
