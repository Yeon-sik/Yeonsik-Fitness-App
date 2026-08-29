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
  isIllustrationKey,
} from "../../lib/scene-contract.mjs";
import { resolveImageIdentity } from "../../lib/exercise-family-contract.mjs";

const REQUIRED_METADATA_FIELDS = [
  "id", "nameKo", "slug", "movementPattern", "motionType", "equipmentKinematics",
  "laterality", "primarySubPart", "secondarySubParts", "equipmentType",
];
const RENDER_CLASSES = new Set([
  "bodyweight", "movable_free_weight", "fixed_support", "fixed_machine", "cable_machine",
]);
const EQUIPMENT_RENDER_CLASSES = new Set([
  "movable_free_weight", "fixed_support", "fixed_machine", "cable_machine", "canonical_attachment",
]);

export function defaultCompilePaths(repositoryRoot) {
  const modelImage = path.join(repositoryRoot, "model_image");
  return {
    exerciseCatalog: path.join(modelImage, "data", "exercise-catalog.json"),
    nameIndex: path.join(modelImage, "data", "exercise-name-index.json"),
    overrides: path.join(modelImage, "data", "exercise-overrides.json"),
    archetypes: path.join(modelImage, "archetypes", "archetype-registry.json"),
    deterministicMapping: path.join(modelImage, "archetypes", "deterministic-mapping.json"),
    familyMapping: path.join(modelImage, "family", "data", "exercise-family-mapping-v1.json"),
    imageIdentity: path.join(modelImage, "data", "exercise-image-identity-v1.json"),
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
  const familyMapping = paths.familyMapping && await pathExists(paths.familyMapping)
    ? await readJson(paths.familyMapping, ERROR_CODES.INVALID_CONTRACT)
    : null;
  const imageIdentity = paths.imageIdentity && await pathExists(paths.imageIdentity)
    ? await readJson(paths.imageIdentity, ERROR_CODES.INVALID_CONTRACT)
    : null;
  if (!(await pathExists(paths.equipmentCatalog))) {
    fail(ERROR_CODES.MISSING_EQUIPMENT_CATALOG, "Canonical equipment catalog is missing", {
      expected: paths.equipmentCatalog,
      policy: "No fallback catalog filename is selected automatically",
    });
  }
  const equipmentCatalog = await readJson(paths.equipmentCatalog, ERROR_CODES.MISSING_EQUIPMENT_CATALOG);
  return {
    exerciseCatalog,
    nameIndex,
    overrides,
    archetypes,
    deterministicMapping,
    familyMapping,
    imageIdentity,
    equipmentCatalog,
    muscleLayers,
    paths,
  };
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

  const imageIdentity = resolveExerciseImageIdentity(exercise, context);
  const imageIdentityIssue = await validateImageIdentityReadiness(imageIdentity, context);
  if (imageIdentityIssue) return issue(imageIdentityIssue.code, {
    exerciseId,
    ...imageIdentityIssue.details,
  });

  const missingArchetype = missingFields(archetype, [
    "camera", "poses", "lockedJoints", "animatedJoints", "renderClass",
    "canonicalReferenceScene", "equipmentAnchorStrategy", "canvas",
  ]);
  if (missingArchetype.length > 0 || !archetype.poses?.A || !archetype.poses?.B || !RENDER_CLASSES.has(archetype.renderClass)) {
    return issue(ERROR_CODES.MISSING_ARCHETYPE, { exerciseId, archetypeId, fields: missingArchetype });
  }
  const cameraViewId = archetype.camera?.viewId ?? archetype.camera?.canonicalView ?? archetype.camera?.view;
  if (typeof cameraViewId !== "string" || cameraViewId.length === 0) {
    return issue(ERROR_CODES.MISSING_VIEW, { exerciseId, archetypeId, field: "camera.viewId" });
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
  const requiredEquipmentViews = resolveEquipmentViewRequirements(archetype, imageIdentity);
  const equipmentViewConflicts = findEquipmentViewConflicts(archetype, imageIdentity);
  if (equipmentViewConflicts.length > 0) {
    return issue(ERROR_CODES.MISSING_VIEW, {
      exerciseId,
      conflicts: equipmentViewConflicts,
    });
  }
  const normalizedPlacements = { A: [], B: [] };
  for (const frameId of ["A", "B"]) {
    for (const [placementIndex, placement] of placementResolution.frames[frameId].entries()) {
      const asset = equipmentById.get(placement.equipmentId);
      if (!asset || asset.status !== "approved" || !EQUIPMENT_RENDER_CLASSES.has(asset.renderClass)
        || typeof asset.viewId !== "string" || !isFinalEquipmentFile(asset.file)
        || !isRenderClassCompatible(archetype.renderClass, asset.renderClass)) {
        return issue(ERROR_CODES.MISSING_EQUIPMENT, {
          exerciseId,
          frameId,
          placementIndex,
          equipmentId: placement.equipmentId,
          status: asset?.status ?? null,
          renderClass: asset?.renderClass ?? null,
          sceneRenderClass: archetype.renderClass,
        });
      }
      if (context.paths.equipmentCatalog && !(await pathExists(path.resolve(path.dirname(context.paths.equipmentCatalog), asset.file)))) {
        return issue(ERROR_CODES.MISSING_EQUIPMENT, {
          exerciseId,
          frameId,
          placementIndex,
          equipmentId: placement.equipmentId,
          file: asset.file,
          reason: "MISSING_FINAL_ASSET",
        });
      }
      const requiredView = requiredEquipmentView(asset, requiredEquipmentViews);
      if (placement.viewId !== undefined && asset.viewId !== placement.viewId) {
        return issue(ERROR_CODES.MISSING_EQUIPMENT_VIEW, {
          exerciseId, frameId, placementIndex, equipmentId: placement.equipmentId,
          requested: placement.viewId, available: asset.viewId ?? null,
          reason: ERROR_CODES.MISSING_VIEW,
        });
      }
      if (requiredView !== undefined && asset.viewId !== requiredView) {
        return issue(ERROR_CODES.MISSING_VIEW, {
          exerciseId,
          frameId,
          placementIndex,
          equipmentId: placement.equipmentId,
          requested: requiredView,
          available: asset.viewId ?? null,
          cameraViewId: archetype.camera?.viewId ?? archetype.camera?.canonicalView ?? null,
          reason: ERROR_CODES.MISSING_VIEW,
          source: requiredEquipmentViews.sources?.[asset.id] ?? requiredEquipmentViews.sources?.[asset.type] ?? null,
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
  if (!sameEquipmentIdentity(normalizedPlacements.A, normalizedPlacements.B, equipmentById)) {
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
  const placementInstances = new Set(normalizedPlacements.A.map((placement) => placement.instanceId));
  const placementEquipment = new Set(normalizedPlacements.A.map((placement) => placement.equipmentId));
  if (lockedEquipment.some((item) => !placementInstances.has(item) && !placementEquipment.has(item))) {
    return issue(ERROR_CODES.MISSING_LOCKED_EQUIPMENT, {
      exerciseId,
      archetypeId,
      lockedEquipment,
      availableInstances: [...placementInstances],
    });
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
    imageIdentity,
    requiredEquipmentViews,
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
    imageIdentity,
    requiredEquipmentViews,
    referencePath,
  } = readiness;
  const muscleMapping = buildMuscleMapping(exercise, context.muscleLayers.exerciseGroups);
  const output = outputDirectory ?? path.join(context.paths.outputRoot, exercise.slug);
  const finalDirectory = context.paths.finalDirectory
    ?? path.resolve(context.paths.outputRoot, "..", "final");
  const imageSlug = imageIdentity.illustrationKey ?? exercise.slug;
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
    imageIdentity: {
      familyId: imageIdentity.familyId,
      visualVariantKey: imageIdentity.visualVariantKey,
      illustrationKey: imageIdentity.illustrationKey,
      source: imageIdentity.source,
      sceneFile: imageIdentity.sceneFile ?? null,
      frameFiles: imageIdentity.frameFiles ?? {},
      equipmentViews: imageIdentity.equipmentViews ?? {},
    },
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
    equipmentViews: Object.fromEntries(equipmentIds.map((equipmentId) => [
      equipmentId,
      normalizedPlacements.A.find((placement) => placement.equipmentId === equipmentId)?.viewId,
    ])),
    frames: [
      frameContract("A", imageSlug, archetype.poses.A, normalizedPlacements.A, invisibleGripTargets.A, output, finalDirectory),
      frameContract("B", imageSlug, archetype.poses.B, normalizedPlacements.B, invisibleGripTargets.B, output, finalDirectory),
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
      mannequin: {
        contract: "mannequin-generation.v1",
        baseFrame: "A",
        derivedFrame: "B",
        camera: "scene.camera",
        canvas: "scene.canvas",
        proportions: "preserve_from_A",
        lockedAnchors: "scene.renderPolicy.lockedAnchors",
      },
      equipmentComposition: {
        source: "canonical_equipment_catalog_v2",
        frameA: "compose_approved_equipment",
        frameB: "reuse_A_equipment_identity",
        movingEquipment: "rigid_transform_only",
      },
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

function mappingEntryForExercise(exercise, context) {
  return context.familyMapping?.legacyExercises?.find(
    (entry) => entry.status === "mapped" && entry.legacyExerciseId === exercise.id,
  ) ?? null;
}

function resolveExerciseImageIdentity(exercise, context) {
  const mapping = mappingEntryForExercise(exercise, context);
  const familyId = mapping?.familyId ?? exercise.familyId ?? null;
  const visualVariantKey = mapping?.visualVariantKey ?? exercise.visualVariantKey ?? null;
  const registry = context.imageIdentity ?? context.familyMapping?.imageIdentity ?? null;
  if (!registry || !familyId || !visualVariantKey) {
    return {
      familyId,
      visualVariantKey,
      illustrationKey: null,
      sceneFile: null,
      frameFiles: {},
      equipmentViews: {},
      source: "placeholder",
    };
  }
  const resolved = resolveImageIdentity(registry, {
    familyId,
    visualVariantKey,
    legacyExerciseId: exercise.id,
  });
  return {
    ...resolved,
    familyId,
    visualVariantKey,
    illustrationKey: resolved.illustrationKey === "placeholder" ? null : resolved.illustrationKey,
    frameFiles: resolved.frameFiles ?? resolved.frames ?? {},
    equipmentViews: resolved.equipmentViews ?? {},
  };
}

async function validateImageIdentityReadiness(identity, context) {
  if (identity.source === "placeholder") return null;
  if (!["exact_visual_variant", "family_default"].includes(identity.source)) {
    return { code: ERROR_CODES.INVALID_IMAGE_IDENTITY, details: { source: identity.source } };
  }
  if (typeof identity.familyId !== "string" || typeof identity.visualVariantKey !== "string"
    || typeof identity.illustrationKey !== "string"
    || !isIllustrationKey(identity.illustrationKey)
    || typeof identity.sceneFile !== "string") {
    return { code: ERROR_CODES.MISSING_IMAGE_IDENTITY, details: { source: identity.source } };
  }
  if (!identity.frameFiles || typeof identity.frameFiles !== "object" || Array.isArray(identity.frameFiles)) {
    return { code: ERROR_CODES.MISSING_IMAGE_ASSET, details: { illustrationKey: identity.illustrationKey, field: "frameFiles" } };
  }
  const references = [["sceneFile", identity.sceneFile]];
  for (const frameId of ["A", "B"]) references.push([`frameFiles.${frameId}`, identity.frameFiles[frameId]]);
  for (const [field, reference] of references) {
    if (typeof reference !== "string" || path.isAbsolute(reference) || reference.trim().length === 0) {
      return { code: ERROR_CODES.INVALID_IMAGE_IDENTITY, details: { illustrationKey: identity.illustrationKey, field } };
    }
    if (field.startsWith("frameFiles.")) {
      const frameId = field.endsWith(".A") ? "a" : "b";
      if (path.basename(reference) !== `${identity.illustrationKey}-${frameId}.png`) {
        return { code: ERROR_CODES.INVALID_IMAGE_IDENTITY, details: { illustrationKey: identity.illustrationKey, field, reference } };
      }
    }
    if (context.paths.imageIdentity) {
      const resolved = path.resolve(path.dirname(context.paths.imageIdentity), reference);
      if (!(await pathExists(resolved))) {
        return { code: ERROR_CODES.MISSING_IMAGE_ASSET, details: { illustrationKey: identity.illustrationKey, field, reference: resolved } };
      }
    }
  }
  if (identity.equipmentViews !== undefined
    && (!identity.equipmentViews || typeof identity.equipmentViews !== "object" || Array.isArray(identity.equipmentViews)
      || Object.entries(identity.equipmentViews).some(([key, value]) => !key || typeof value !== "string" || !value))) {
    return { code: ERROR_CODES.INVALID_IMAGE_IDENTITY, details: { illustrationKey: identity.illustrationKey, field: "equipmentViews" } };
  }
  return null;
}

function viewRequirementsFrom(source, sourceName) {
  if (!source || typeof source !== "object" || Array.isArray(source)) return { map: {}, sources: {} };
  return {
    map: Object.fromEntries(Object.entries(source).filter(([, value]) => value !== null && value !== undefined)),
    sources: Object.fromEntries(Object.keys(source).map((key) => [key, sourceName])),
  };
}

function resolveEquipmentViewRequirements(archetype, imageIdentity) {
  const sources = [
    viewRequirementsFrom(archetype?.equipmentViews, "archetype.equipmentViews"),
    viewRequirementsFrom(archetype?.requiredEquipmentViews, "archetype.requiredEquipmentViews"),
    viewRequirementsFrom(imageIdentity?.equipmentViews, `image_identity.${imageIdentity?.source ?? "unknown"}`),
  ];
  const map = {};
  const sourceMap = {};
  for (const source of sources) {
    for (const [key, value] of Object.entries(source.map)) {
      if (map[key] === undefined) {
        map[key] = value;
        sourceMap[key] = source.sources[key];
      }
    }
  }
  return { map, sources: sourceMap };
}

function findEquipmentViewConflicts(archetype, imageIdentity) {
  const sources = [
    viewRequirementsFrom(archetype?.equipmentViews, "archetype.equipmentViews"),
    viewRequirementsFrom(archetype?.requiredEquipmentViews, "archetype.requiredEquipmentViews"),
    viewRequirementsFrom(imageIdentity?.equipmentViews, `image_identity.${imageIdentity?.source ?? "unknown"}`),
  ];
  const conflicts = [];
  const values = new Map();
  for (const source of sources) {
    for (const [key, value] of Object.entries(source.map)) {
      const previous = values.get(key);
      if (previous && previous.value !== value) {
        conflicts.push({ key, values: [previous.value, value], sources: [previous.source, source.sources[key]] });
      } else if (!previous) {
        values.set(key, { value, source: source.sources[key] });
      }
    }
  }
  return conflicts;
}

function requiredEquipmentView(asset, requirements) {
  return requirements.map[asset.id] ?? requirements.map[asset.type];
}

function isFinalEquipmentFile(file) {
  return typeof file === "string"
    && /^final\/[a-z0-9]+(?:[-_][a-z0-9]+)*\.png$/.test(file.replaceAll("\\", "/"));
}

function isRenderClassCompatible(sceneRenderClass, equipmentRenderClass) {
  if (sceneRenderClass === "cable_machine") {
    return ["cable_machine", "fixed_machine", "canonical_attachment"].includes(equipmentRenderClass);
  }
  if (sceneRenderClass === "movable_free_weight") {
    return ["movable_free_weight", "fixed_support"].includes(equipmentRenderClass);
  }
  return sceneRenderClass === equipmentRenderClass;
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

function sameEquipmentIdentity(left, right, equipmentById = new Map()) {
  const ids = (placements) => placements
    .map((item) => {
      const asset = equipmentById.get(item.equipmentId);
      return [
        item.instanceId,
        item.equipmentId,
        item.viewId,
        asset?.file ?? null,
        asset?.sha256 ?? null,
        item.includeFrontOccluder === true,
        item.includeFrontOccluder === true ? asset?.frontOccluder?.file ?? null : null,
        item.includeFrontOccluder === true ? asset?.frontOccluder?.sha256 ?? null : null,
      ].join("|");
    })
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
    ...(id === "B" ? { derivedFrom: "A" } : {}),
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
    "--family-mapping": "familyMapping", "--image-identity": "imageIdentity",
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
