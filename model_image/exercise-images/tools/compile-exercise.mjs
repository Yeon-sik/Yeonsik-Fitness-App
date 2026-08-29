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

const REQUIRED_METADATA_FIELDS = [
  "id", "nameKo", "slug", "movementPattern", "motionType", "supportMode",
  "bodyOrientation", "equipmentKinematics", "laterality", "gripVariant",
  "canonicalView", "primarySubPart", "secondarySubParts", "equipmentType",
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
    equipmentCatalog: path.join(modelImage, "equipment", "equipment-catalog.json"),
    muscleLayers: path.join(modelImage, "style-4", "muscle-layers.json"),
    templateA: path.join(modelImage, "exercise-images", "templates", "mannequin-a.prompt.md"),
    templateB: path.join(modelImage, "exercise-images", "templates", "mannequin-b-edit.prompt.md"),
    outputRoot: path.join(modelImage, "exercise-images", "generated"),
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
  if (!(await pathExists(paths.equipmentCatalog))) {
    fail(ERROR_CODES.MISSING_EQUIPMENT_CATALOG, "Canonical equipment catalog is missing", {
      expected: paths.equipmentCatalog,
      policy: "No fallback catalog filename is selected automatically",
    });
  }
  const equipmentCatalog = await readJson(paths.equipmentCatalog, ERROR_CODES.MISSING_EQUIPMENT_CATALOG);
  return { exerciseCatalog, nameIndex, overrides, archetypes, equipmentCatalog, muscleLayers, paths };
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

  const override = context.overrides.exercises?.[exerciseId];
  if (!override) return issue(ERROR_CODES.MISSING_OVERRIDE, { exerciseId });
  const archetypeId = override.archetypeId ?? exercise.archetypeId;
  const archetype = context.archetypes.archetypes?.[archetypeId];
  if (!archetypeId || !archetype) return issue(ERROR_CODES.MISSING_ARCHETYPE, { exerciseId, archetypeId: archetypeId ?? null });

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

  const placementsByFrame = override.equipmentPlacements;
  if (!placementsByFrame || !Array.isArray(placementsByFrame.A) || !Array.isArray(placementsByFrame.B)) {
    return issue(ERROR_CODES.MISSING_OVERRIDE, { exerciseId, field: "equipmentPlacements.A/B" });
  }
  const equipmentById = new Map((context.equipmentCatalog.assets ?? []).map((asset) => [asset.id, asset]));
  for (const frameId of ["A", "B"]) {
    for (const [placementIndex, placement] of placementsByFrame[frameId].entries()) {
      const asset = equipmentById.get(placement.equipmentId);
      if (!asset || asset.status !== "approved" || !String(asset.file ?? "").replaceAll("\\", "/").startsWith("final/")) {
        return issue(ERROR_CODES.MISSING_EQUIPMENT, { exerciseId, frameId, placementIndex, equipmentId: placement.equipmentId });
      }
      if (!placement.viewId || asset.viewId !== placement.viewId) {
        return issue(ERROR_CODES.MISSING_EQUIPMENT_VIEW, {
          exerciseId, frameId, placementIndex, equipmentId: placement.equipmentId,
          requested: placement.viewId ?? null, available: asset.viewId ?? null,
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
        return issue(ERROR_CODES.MISSING_OVERRIDE, { exerciseId, frameId, placementIndex, fields: missingPlacement });
      }
    }
  }
  if (!sameEquipmentIdentity(placementsByFrame.A, placementsByFrame.B)) {
    return issue(ERROR_CODES.MISSING_OVERRIDE, { exerciseId, field: "A/B equipment identity" });
  }
  return { ready: true, code: "READY", exercise, override, archetype, referencePath };
}

export async function compileExercise({ exerciseName, context, outputDirectory }) {
  const exerciseId = resolveExerciseId(exerciseName, context.nameIndex);
  const readiness = await inspectExerciseReadiness(exerciseId, context);
  if (!readiness.ready) fail(readiness.code, `Exercise is not ready: ${exerciseId}`, readiness.details);
  const { exercise, override, archetype, referencePath } = readiness;
  const muscleMapping = buildMuscleMapping(exercise, context.muscleLayers.exerciseGroups);
  const equipmentIds = [...new Set([
    ...override.equipmentPlacements.A,
    ...override.equipmentPlacements.B,
  ].map((placement) => placement.equipmentId))];
  const lockedAnchors = override.lockedAnchors ?? {};
  const tolerance = override.anchorTolerancePixels;
  if (!Number.isFinite(tolerance)) {
    fail(ERROR_CODES.MISSING_OVERRIDE, "anchorTolerancePixels must be explicitly reviewed", { exerciseId });
  }

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
  };
  const [templateA, templateB] = await Promise.all([
    fs.readFile(context.paths.templateA, "utf8"),
    fs.readFile(context.paths.templateB, "utf8"),
  ]);
  const promptA = renderTemplate(templateA, variables);
  const promptB = renderTemplate(templateB, variables);
  const output = outputDirectory ?? path.join(context.paths.outputRoot, exercise.slug);
  const scene = {
    contractType: "exercise-image-orchestration.v1",
    schemaVersion: 1,
    exerciseId,
    nameKo: exercise.nameKo,
    slug: exercise.slug,
    archetypeId: override.archetypeId ?? exercise.archetypeId,
    renderClass: archetype.renderClass,
    canvas: archetype.canvas,
    camera: archetype.camera,
    exerciseMetadata: {
      movementPattern: exercise.movementPattern,
      motionType: exercise.motionType,
      supportMode: exercise.supportMode,
      bodyOrientation: exercise.bodyOrientation,
      equipmentKinematics: exercise.equipmentKinematics,
      laterality: exercise.laterality,
      gripVariant: exercise.gripVariant,
      canonicalView: exercise.canonicalView,
      equipmentType: exercise.equipmentType,
    },
    muscleMapping,
    equipment: equipmentIds,
    frames: [
      frameContract("A", exercise.slug, archetype.poses.A, override.equipmentPlacements.A),
      frameContract("B", exercise.slug, archetype.poses.B, override.equipmentPlacements.B),
    ],
    renderPolicy: {
      lockedJoints: archetype.lockedJoints,
      animatedJoints: archetype.animatedJoints,
      lockedAnchors,
      anchorTolerancePixels: tolerance,
    },
    generationContract: {
      baseFrame: "A",
      derivedFrames: { B: "edit_from_A" },
      referenceScene: path.relative(output, referencePath).replaceAll("\\", "/"),
      equipmentAnchorStrategy: archetype.equipmentAnchorStrategy,
      prompts: { A: "prompt-a.md", B: "prompt-b-edit.md" },
      renderSteps: renderSteps(archetype.renderClass),
    },
  };
  await fs.mkdir(output, { recursive: true });
  await Promise.all([
    writeJson(path.join(output, "scene.json"), scene),
    writeJson(path.join(output, "placements-a.json"), override.equipmentPlacements.A),
    writeJson(path.join(output, "placements-b.json"), override.equipmentPlacements.B),
    fs.writeFile(path.join(output, "prompt-a.md"), promptA, "utf8"),
    fs.writeFile(path.join(output, "prompt-b-edit.md"), promptB, "utf8"),
  ]);
  return { scene, outputDirectory: output };
}

function issue(code, details) {
  return { ready: false, code, details };
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
  const ids = (placements) => placements.map((item) => item.equipmentId).sort();
  return JSON.stringify(ids(left)) === JSON.stringify(ids(right));
}

function renderTemplate(template, variables) {
  return template.replace(/\{\{([A-Za-z0-9]+)\}\}/g, (_, key) => {
    if (!(key in variables)) fail(ERROR_CODES.INVALID_CONTRACT, `Unknown prompt template variable: ${key}`);
    return String(variables[key]);
  });
}

function frameContract(id, slug, pose, equipmentPlacements) {
  const lower = id.toLowerCase();
  return {
    id,
    file: `${slug}-${lower}.png`,
    mannequinFile: `${slug}-${lower}-mannequin.png`,
    pose,
    equipmentPlacements,
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
    "--equipment-catalog": "equipmentCatalog", "--muscle-layers": "muscleLayers",
    "--template-a": "templateA", "--template-b": "templateB", "--output-root": "outputRoot",
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
