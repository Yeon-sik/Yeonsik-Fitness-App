#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import {
  EXERCISE_SCENE_CONTRACT_TYPE,
  EXERCISE_SCENE_SCHEMA_VERSION,
  IMAGE_IDENTITY_SOURCES,
  SCENE_FRAME_IDS,
  finalFrameFilename,
  isIllustrationKey,
  isFinalFrameReference,
  sceneImageSlug,
} from "../../lib/scene-contract.mjs";

const require = createRequire(import.meta.url);

export async function validateAssetManifests({ sharpModulePath, catalogPath, scenePaths = [] }) {
  const sharp = require(sharpModulePath);
  const catalog = JSON.parse(await fs.readFile(catalogPath, "utf8"));
  failIf(catalog.schemaVersion !== 2, "MISSING_CATALOG_SCHEMA_V2");
  failIf(
    catalog.coordinateSystem?.origin !== "top_left"
      || catalog.coordinateSystem?.unit !== "asset_local_normalized_0_to_1",
    "INVALID_CATALOG_COORDINATE_SYSTEM",
  );
  failIf(!Array.isArray(catalog.assets), "MISSING_CATALOG_ASSETS");

  const catalogDirectory = path.dirname(path.resolve(catalogPath));
  const finalDirectory = path.resolve(catalogDirectory, "final");
  const statuses = new Set(["draft", "approved", "deprecated"]);
  const renderClasses = new Set([
    "movable_free_weight", "fixed_support", "fixed_machine", "cable_machine", "canonical_attachment",
  ]);
  const equipmentById = new Map();
  const equipmentViews = new Set();

  async function validateComponent(assetId, component, suffix = "") {
    if (!component || typeof component !== "object") failIf(true, "MISSING_EQUIPMENT_COMPONENT", `${assetId}${suffix}`);
    if (typeof component.file !== "string" || !/^final\/[a-z0-9]+(?:[-_][a-z0-9]+)*\.png$/.test(component.file)) {
      failIf(true, "SOURCE_ASSET_FORBIDDEN", `${assetId}${suffix}:${String(component.file)}`);
    }
    if (!Number.isInteger(component.width) || component.width <= 0 || !Number.isInteger(component.height) || component.height <= 0) {
      failIf(true, "MISSING_ASSET_DIMENSIONS", `${assetId}${suffix}`);
    }
    if (typeof component.sha256 !== "string" || !/^[a-f0-9]{64}$/.test(component.sha256)) {
      failIf(true, "MISSING_ASSET_SHA256", `${assetId}${suffix}`);
    }
    if (!component.anchors || typeof component.anchors !== "object" || Array.isArray(component.anchors)
      || Object.keys(component.anchors).length === 0) {
      failIf(true, "MISSING_ANCHOR", `${assetId}${suffix}`);
    }
    for (const [anchorName, point] of Object.entries(component.anchors)) {
      assertToken("INVALID_ANCHOR_NAME", anchorName);
      assertPoint(`${assetId}${suffix}.${anchorName}`, point, 1, 1, true);
    }
    const imagePath = path.resolve(catalogDirectory, component.file);
    assertInside(finalDirectory, imagePath, "SOURCE_ASSET_FORBIDDEN");
    await assertTransparentPng(imagePath, `${assetId}${suffix}`, component.width, component.height, component.sha256, false, sharp);
  }

  for (const asset of catalog.assets) {
    assertToken("INVALID_EQUIPMENT_ID", asset.id);
    assertToken("INVALID_EQUIPMENT_TYPE", asset.type);
    assertToken("INVALID_EQUIPMENT_VIEW", asset.viewId);
    failIf(equipmentById.has(asset.id), "DUPLICATE_EQUIPMENT_ID", asset.id);
    const viewKey = `${asset.type}|${asset.viewId}`;
    failIf(equipmentViews.has(viewKey), "DUPLICATE_EQUIPMENT_TYPE_VIEW", viewKey);
    if (asset.status === null || asset.status === undefined || asset.status === "") {
      failIf(true, "MISSING_EQUIPMENT_STATUS", asset.id);
    }
    if (!statuses.has(asset.status)) failIf(true, "INVALID_STATUS", `${asset.id}:${asset.status}`);
    if (asset.renderClass === null || asset.renderClass === undefined || asset.renderClass === "") {
      failIf(true, "MISSING_RENDER_CLASS", asset.id);
    }
    if (!renderClasses.has(asset.renderClass)) failIf(true, "INVALID_RENDER_CLASS", `${asset.id}:${asset.renderClass}`);
    equipmentById.set(asset.id, asset);
    equipmentViews.add(viewKey);
    await validateComponent(asset.id, asset);
    if (asset.frontOccluder) {
      if (!["fixed_machine", "cable_machine"].includes(asset.renderClass)) failIf(true, "INVALID_FRONT_OCCLUDER_CLASS", asset.id);
      await validateComponent(asset.id, asset.frontOccluder, ".frontOccluder");
    }
  }

  let frameCount = 0;
  let placementCount = 0;
  for (const scenePath of scenePaths) {
    const scene = JSON.parse(await fs.readFile(scenePath, "utf8"));
    const sceneLabel = scene.exerciseId ?? scenePath;
    validateSceneRoot(scene, sceneLabel);
    if (!scene.canvas || !Number.isInteger(scene.canvas.width) || !Number.isInteger(scene.canvas.height)) {
      failIf(true, "MISSING_SCENE_CANVAS", scenePath);
    }
    if (!Array.isArray(scene.equipment)) failIf(true, "MISSING_SCENE_EQUIPMENT", sceneLabel);
    if (new Set(scene.equipment).size !== scene.equipment.length) failIf(true, "DUPLICATE_SCENE_EQUIPMENT", sceneLabel);
    if (!scene.equipmentViews || typeof scene.equipmentViews !== "object" || Array.isArray(scene.equipmentViews)) {
      failIf(true, "MISSING_SCENE_EQUIPMENT_VIEWS", sceneLabel);
    }
    for (const equipmentId of scene.equipment) {
      const asset = equipmentById.get(equipmentId);
      if (!asset) failIf(true, "MISSING_EQUIPMENT", `${sceneLabel}:${equipmentId}`);
      if (asset.status !== "approved") failIf(true, "UNAPPROVED_EQUIPMENT", `${sceneLabel}:${equipmentId}`);
      const expectedView = scene.equipmentViews?.[equipmentId];
      if (typeof expectedView !== "string" || expectedView.length === 0) failIf(true, "MISSING_EQUIPMENT_VIEW", `${sceneLabel}:${equipmentId}`);
      if (expectedView !== asset.viewId) failIf(true, "INVALID_EQUIPMENT_VIEW", `${sceneLabel}:${equipmentId}`);
      const identityView = scene.imageIdentity.equipmentViews?.[equipmentId]
        ?? scene.imageIdentity.equipmentViews?.[asset.type];
      if (identityView !== undefined && identityView !== expectedView) {
        failIf(true, "MISSING_VIEW", `${sceneLabel}:${equipmentId}:${identityView} != ${expectedView}`);
      }
      if (!isEquipmentRenderClassCompatible(scene.renderClass, asset.renderClass)) {
        failIf(true, "INVALID_EQUIPMENT_RENDER_CLASS", `${sceneLabel}:${equipmentId}:${asset.renderClass}`);
      }
    }

    const renderPolicy = scene.renderPolicy;
    if (!renderPolicy || typeof renderPolicy !== "object") failIf(true, "MISSING_RENDER_POLICY", sceneLabel);
    if (!Object.prototype.hasOwnProperty.call(renderPolicy, "lockedAnchors")) failIf(true, "MISSING_LOCKED_ANCHORS", sceneLabel);
    if (!renderPolicy.lockedAnchors || typeof renderPolicy.lockedAnchors !== "object" || Array.isArray(renderPolicy.lockedAnchors)) {
      failIf(true, "INVALID_LOCKED_ANCHORS", sceneLabel);
    }
    if (!Number.isFinite(renderPolicy.anchorTolerancePixels) || renderPolicy.anchorTolerancePixels < 0) {
      failIf(true, "INVALID_LOCKED_ANCHOR_TOLERANCE", sceneLabel);
    }
    if (!Array.isArray(renderPolicy.lockedEquipment)) failIf(true, "MISSING_LOCKED_EQUIPMENT", sceneLabel);
    if (new Set(renderPolicy.lockedEquipment).size !== renderPolicy.lockedEquipment.length) {
      failIf(true, "DUPLICATE_LOCKED_EQUIPMENT", sceneLabel);
    }
    for (const [anchorName, point] of Object.entries(renderPolicy.lockedAnchors ?? {})) {
      assertPoint(`${sceneLabel}.${anchorName}`, point, scene.canvas.width, scene.canvas.height);
    }

    if (!Array.isArray(scene.frames) || scene.frames.length !== SCENE_FRAME_IDS.length
      || scene.frames[0]?.id !== SCENE_FRAME_IDS[0] || scene.frames[1]?.id !== SCENE_FRAME_IDS[1]) {
      failIf(true, "INVALID_AB_FRAMES", sceneLabel);
    }
    const slug = scene.slug ?? path.basename(scenePath, ".scene.json");
    if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug)) failIf(true, "INVALID_SCENE_SLUG", slug);
    const imageSlug = sceneImageSlug(scene);
    if (!isIllustrationKey(imageSlug)) failIf(true, "INVALID_IMAGE_ILLUSTRATION_KEY", imageSlug);
    const placementsByFrame = [];
    const identities = [];

    for (const frame of scene.frames) {
      if (frame.id === "B" && frame.derivedFrom !== "A") {
        failIf(true, "INVALID_AB_DERIVATION", `${sceneLabel}:B must be edited from A`);
      }
      const expectedName = finalFrameFilename(imageSlug, frame.id);
      if (!isFinalFrameFile(frame.file, expectedName)) {
        failIf(true, "INVALID_FRAME_FILENAME", `${sceneLabel}:${frame.file}; expected ${expectedName} under final/`);
      }
      if (scene.imageIdentity.source !== "placeholder"
        && path.basename(scene.imageIdentity.frameFiles[frame.id] ?? "") !== expectedName) {
        failIf(true, "IMAGE_IDENTITY_FRAME_MISMATCH", `${sceneLabel}:${frame.id}`);
      }
      const imagePath = path.resolve(path.dirname(scenePath), frame.file);
      await assertTransparentPng(imagePath, `${sceneLabel}.${frame.id}`, scene.canvas.width, scene.canvas.height, null, true, sharp);
      for (const [jointName, point] of Object.entries(frame.joints ?? {})) {
        assertPoint(`${frame.id}.${jointName}`, point, scene.canvas.width, scene.canvas.height);
      }
      if (!Array.isArray(frame.equipmentPlacements)) failIf(true, "MISSING_EQUIPMENT_PLACEMENT", `${sceneLabel}.${frame.id}`);
      if (!Array.isArray(frame.invisibleGripTargets)) failIf(true, "MISSING_INVISIBLE_GRIP_TARGETS", `${sceneLabel}.${frame.id}`);
      validateInvisibleGripTargets(scene, frame, sceneLabel);

      for (const gripTarget of frame.invisibleGripTargets) {
        if (typeof gripTarget?.instanceId !== "string" || typeof gripTarget?.equipmentId !== "string"
          || typeof gripTarget?.anchor !== "string") failIf(true, "INVALID_INVISIBLE_GRIP_TARGET", `${sceneLabel}.${frame.id}`);
        assertPoint(`${sceneLabel}.${frame.id}.${gripTarget.instanceId}`, gripTarget.target, scene.canvas.width, scene.canvas.height);
      }

      const frameIds = new Set();
      const frameIdentity = [];
      for (const [index, placement] of frame.equipmentPlacements.entries()) {
        const asset = validatePlacement(scene, frame, placement, index, equipmentById);
        frameIds.add(asset.id);
        frameIdentity.push(...placementIdentity(asset, placement, index));
      }
      for (const equipmentId of scene.equipment) {
        if (!frameIds.has(equipmentId)) failIf(true, "MISSING_EQUIPMENT_PLACEMENT", `${sceneLabel}.${frame.id}:${equipmentId}`);
      }
      identities.push(frameIdentity.sort());
      placementsByFrame.push(frame.equipmentPlacements);
      placementCount += frame.equipmentPlacements.length;
      frameCount += 1;
    }
    if (JSON.stringify(identities[0]) !== JSON.stringify(identities[1])) failIf(true, "AB_EQUIPMENT_IDENTITY_MISMATCH", sceneLabel);

    for (const lockedId of renderPolicy.lockedEquipment) {
      const pairs = findLockedPlacementPairs(lockedId, placementsByFrame[0], placementsByFrame[1]);
      if (pairs.length === 0) failIf(true, "MISSING_LOCKED_EQUIPMENT_INSTANCE", `${sceneLabel}:${lockedId}`);
      for (const [placementA, placementB] of pairs) {
        const drift = Math.hypot(placementA.target[0] - placementB.target[0], placementA.target[1] - placementB.target[1]);
        if (drift > renderPolicy.anchorTolerancePixels) failIf(true, "LOCKED_ANCHOR_DRIFT", `${sceneLabel}:${lockedId}:${drift}`);
        if (Math.abs(placementA.scale - placementB.scale) / placementA.scale > 0.01) failIf(true, "LOCKED_EQUIPMENT_SCALE_DRIFT", `${sceneLabel}:${lockedId}`);
        if (Math.abs(placementA.rotationDegrees - placementB.rotationDegrees) > 0.5) failIf(true, "LOCKED_EQUIPMENT_ROTATION_DRIFT", `${sceneLabel}:${lockedId}`);
      }
    }

    const lockedKeys = new Set(renderPolicy.lockedEquipment);
    const placementsA = placementsByFrame[0];
    const placementsB = placementsByFrame[1];
    for (const placementA of placementsA) {
      const placementB = placementsB.find((candidate) => candidate.instanceId === placementA.instanceId);
      if (!placementB) continue;
      const asset = equipmentById.get(placementA.equipmentId);
      const isLocked = lockedKeys.has(placementA.instanceId) || lockedKeys.has(placementA.equipmentId);
      if (asset.renderClass !== "movable_free_weight" && !isLocked) {
        failIf(true, "UNLOCKED_FIXED_EQUIPMENT", `${sceneLabel}:${placementA.instanceId}`);
      }
      if (Math.abs(placementA.scale - placementB.scale) / placementA.scale > 0.01) {
        failIf(true, "NON_RIGID_EQUIPMENT_SCALE_DRIFT", `${sceneLabel}:${placementA.instanceId}`);
      }
    }
  }

  return { valid: true, equipmentAssets: equipmentById.size, scenes: scenePaths.length, frames: frameCount, placements: placementCount };
}

function failIf(condition, code, detail = "") {
  if (condition) throw new Error(`${code}${detail ? `: ${detail}` : ""}`);
}

function assertToken(code, value) {
  failIf(typeof value !== "string" || !/^[a-z0-9]+(?:_[a-z0-9]+)*$/.test(value), code, String(value));
}

function isEquipmentRenderClassCompatible(sceneRenderClass, equipmentRenderClass) {
  if (sceneRenderClass === "cable_machine") {
    return ["cable_machine", "fixed_machine", "canonical_attachment"].includes(equipmentRenderClass);
  }
  if (sceneRenderClass === "movable_free_weight") {
    return ["movable_free_weight", "fixed_support"].includes(equipmentRenderClass);
  }
  return sceneRenderClass === equipmentRenderClass;
}

function isPoint(value) {
  return Array.isArray(value) && value.length === 2 && value.every(Number.isFinite);
}

function assertPoint(name, point, width, height, normalized = false) {
  failIf(!isPoint(point), "INVALID_POINT", `${name}=${JSON.stringify(point)}`);
  const maximumX = normalized ? 1 : width;
  const maximumY = normalized ? 1 : height;
  failIf(point[0] < 0 || point[0] > maximumX || point[1] < 0 || point[1] > maximumY, "POINT_OUTSIDE_CANVAS", `${name}=${JSON.stringify(point)}`);
}

function assertInside(root, candidate, code) {
  const relative = path.relative(root, candidate);
  failIf(!relative || relative.startsWith("..") || path.isAbsolute(relative), code, candidate);
}

function isFinalFrameFile(file, expectedName) {
  return isFinalFrameReference(file, expectedName);
}

function validateSceneRoot(scene, sceneLabel) {
  failIf(scene.contractType !== EXERCISE_SCENE_CONTRACT_TYPE, "INVALID_SCENE_CONTRACT", sceneLabel);
  failIf(scene.schemaVersion !== EXERCISE_SCENE_SCHEMA_VERSION, "INVALID_SCENE_SCHEMA_VERSION", sceneLabel);
  for (const field of [
    "exerciseId", "nameKo", "slug", "archetypeId", "renderClass", "canvas", "camera",
    "exerciseMetadata", "metadataResolution", "imageIdentity", "muscleMapping", "equipment", "equipmentViews", "frames",
    "renderPolicy", "generationContract",
  ]) {
    failIf(scene[field] === undefined || scene[field] === null, "MISSING_SCENE_FIELD", `${sceneLabel}:${field}`);
  }
  const metadata = scene.exerciseMetadata;
  for (const field of [
    "movementPattern", "motionType", "supportMode", "bodyOrientation",
    "equipmentKinematics", "laterality", "gripVariant", "equipmentType",
  ]) {
    failIf(typeof metadata[field] !== "string" || metadata[field].length === 0, "MISSING_SCENE_METADATA", `${sceneLabel}:${field}`);
  }
  const metadataResolution = scene.metadataResolution;
  const resolutionFields = metadataResolution.fields;
  failIf(!resolutionFields || typeof resolutionFields !== "object" || Array.isArray(resolutionFields), "MISSING_METADATA_RESOLUTION", sceneLabel);
  const metadataSources = new Set(["exercise_override", "archetype_default", "archetype_camera", "deterministic_mapping", "source_catalog"]);
  for (const field of ["supportMode", "bodyOrientation", "equipmentKinematics", "gripVariant", "canonicalView"]) {
    failIf(!(field in resolutionFields), "MISSING_METADATA_RESOLUTION", `${sceneLabel}:${field}`);
    failIf(resolutionFields[field] !== null && !metadataSources.has(resolutionFields[field]), "INVALID_METADATA_SOURCE", `${sceneLabel}:${field}`);
  }
  failIf(metadataResolution.canonicalViewSource !== null
    && !metadataSources.has(metadataResolution.canonicalViewSource), "INVALID_METADATA_SOURCE", `${sceneLabel}:canonicalView`);
  if (resolutionFields.canonicalView !== null && resolutionFields.canonicalView !== undefined
    && metadataResolution.canonicalViewSource === "archetype_camera") {
    failIf(metadata.canonicalView !== undefined, "DUPLICATE_CANONICAL_VIEW_SOURCE", sceneLabel);
  }
  const cameraView = scene.camera?.viewId ?? scene.camera?.canonicalView ?? scene.camera?.view;
  failIf(typeof cameraView !== "string" || cameraView.length === 0, "MISSING_CAMERA_VIEW", sceneLabel);

  const imageIdentity = scene.imageIdentity;
  failIf(!IMAGE_IDENTITY_SOURCES.includes(imageIdentity.source), "INVALID_IMAGE_IDENTITY_SOURCE", sceneLabel);
  failIf(imageIdentity.familyId !== null && typeof imageIdentity.familyId !== "string", "INVALID_IMAGE_IDENTITY", `${sceneLabel}:familyId`);
  failIf(imageIdentity.visualVariantKey !== null && typeof imageIdentity.visualVariantKey !== "string", "INVALID_IMAGE_IDENTITY", `${sceneLabel}:visualVariantKey`);
  failIf(imageIdentity.illustrationKey !== null && !isIllustrationKey(imageIdentity.illustrationKey),
  "INVALID_IMAGE_IDENTITY", `${sceneLabel}:illustrationKey`);
  failIf(!imageIdentity.frameFiles || typeof imageIdentity.frameFiles !== "object" || Array.isArray(imageIdentity.frameFiles), "INVALID_IMAGE_IDENTITY", `${sceneLabel}:frameFiles`);
  failIf(!imageIdentity.equipmentViews || typeof imageIdentity.equipmentViews !== "object" || Array.isArray(imageIdentity.equipmentViews), "INVALID_IMAGE_IDENTITY", `${sceneLabel}:equipmentViews`);
  if (imageIdentity.source === "placeholder") {
    failIf(imageIdentity.illustrationKey !== null, "INVALID_PLACEHOLDER_IDENTITY", sceneLabel);
  } else {
    failIf(typeof imageIdentity.familyId !== "string" || typeof imageIdentity.visualVariantKey !== "string"
      || typeof imageIdentity.illustrationKey !== "string" || typeof imageIdentity.sceneFile !== "string",
    "MISSING_IMAGE_IDENTITY", sceneLabel);
    for (const frameId of SCENE_FRAME_IDS) {
      failIf(typeof imageIdentity.frameFiles[frameId] !== "string", "MISSING_IMAGE_IDENTITY_FRAME", `${sceneLabel}:${frameId}`);
      failIf(path.basename(imageIdentity.frameFiles[frameId] ?? "") !== finalFrameFilename(imageIdentity.illustrationKey, frameId), "IMAGE_IDENTITY_FRAME_MISMATCH", `${sceneLabel}:${frameId}`);
    }
  }
  const generation = scene.generationContract;
  failIf(generation.baseFrame !== "A" || generation.derivedFrames?.B !== "edit_from_A", "INVALID_GENERATION_CONTRACT", sceneLabel);
  failIf(!generation.equipmentAnchorStrategy || !generation.prompts?.A || !generation.prompts?.B
    || !Array.isArray(generation.renderSteps), "INVALID_GENERATION_CONTRACT", sceneLabel);
  failIf(generation.mannequin?.contract !== "mannequin-generation.v1"
    || generation.mannequin.baseFrame !== "A"
    || generation.mannequin.derivedFrame !== "B"
    || generation.mannequin.camera !== "scene.camera"
    || generation.mannequin.canvas !== "scene.canvas"
    || generation.mannequin.proportions !== "preserve_from_A"
    || generation.mannequin.lockedAnchors !== "scene.renderPolicy.lockedAnchors",
  "INVALID_MANNEQUIN_GENERATION_CONTRACT", sceneLabel);
  failIf(generation.equipmentComposition?.source !== "canonical_equipment_catalog_v2"
    || generation.equipmentComposition.frameA !== "compose_approved_equipment"
    || generation.equipmentComposition.frameB !== "reuse_A_equipment_identity"
    || generation.equipmentComposition.movingEquipment !== "rigid_transform_only",
  "INVALID_EQUIPMENT_COMPOSITION_CONTRACT", sceneLabel);
}

function validateInvisibleGripTargets(scene, frame, sceneLabel) {
  const strategy = scene.generationContract?.equipmentAnchorStrategy?.type;
  const generationTargets = scene.generationContract?.invisibleGripTargets?.[frame.id];
  if (generationTargets !== undefined) {
    failIf(!Array.isArray(generationTargets), "INVALID_INVISIBLE_GRIP_TARGETS", `${sceneLabel}.${frame.id}`);
    failIf(JSON.stringify(generationTargets) !== JSON.stringify(frame.invisibleGripTargets), "INVISIBLE_GRIP_TARGET_CONTRACT_MISMATCH", `${sceneLabel}.${frame.id}`);
  }
  if (strategy !== "invisible_grip") {
    failIf(frame.invisibleGripTargets.length !== 0, "UNEXPECTED_INVISIBLE_GRIP_TARGETS", `${sceneLabel}.${frame.id}`);
    return;
  }
  failIf(frame.invisibleGripTargets.length !== frame.equipmentPlacements.length, "INVISIBLE_GRIP_TARGET_COUNT_MISMATCH", `${sceneLabel}.${frame.id}`);
  const placements = new Map(frame.equipmentPlacements.map((placement) => [placement.instanceId, placement]));
  for (const target of frame.invisibleGripTargets) {
    const placement = placements.get(target.instanceId);
    failIf(!placement, "INVISIBLE_GRIP_TARGET_NOT_PLACED", `${sceneLabel}.${frame.id}:${target.instanceId}`);
    failIf(target.equipmentId !== placement.equipmentId || target.anchor !== placement.anchor
      || JSON.stringify(target.target) !== JSON.stringify(placement.target),
    "INVISIBLE_GRIP_TARGET_MISMATCH", `${sceneLabel}.${frame.id}:${target.instanceId}`);
  }
}

async function assertTransparentPng(imagePath, label, expectedWidth, expectedHeight, expectedSha, requireTransparentPixel, sharp) {
  const bytes = await fs.readFile(imagePath);
  if (expectedSha && crypto.createHash("sha256").update(bytes).digest("hex") !== expectedSha) failIf(true, "ASSET_IDENTITY_MISMATCH", label);
  const metadata = await sharp(bytes).metadata();
  if (metadata.format !== "png" || !metadata.hasAlpha) failIf(true, "MISSING_TRANSPARENCY", label);
  if (expectedWidth !== null && (metadata.width !== expectedWidth || metadata.height !== expectedHeight)) {
    failIf(true, "CANVAS_MISMATCH", `${label}:${metadata.width}x${metadata.height}`);
  }
  const raw = await sharp(bytes).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  let transparent = false;
  let visible = false;
  for (let offset = 3; offset < raw.data.length; offset += raw.info.channels) {
    transparent ||= raw.data[offset] === 0;
    visible ||= raw.data[offset] > 0;
    if (transparent && visible) break;
  }
  failIf(!visible, "MISSING_VISIBLE_PIXELS", label);
  if (requireTransparentPixel) failIf(!transparent, "MISSING_TRANSPARENCY", `${label}: no fully transparent pixel`);
}

function validatePlacement(scene, frame, placement, index, equipmentById) {
  const label = `${scene.exerciseId}.${frame.id}.equipmentPlacements[${index}]`;
  const asset = equipmentById.get(placement?.equipmentId);
  if (!asset) failIf(true, "MISSING_EQUIPMENT", `${label}:${String(placement?.equipmentId)}`);
  if (asset.status !== "approved") failIf(true, "UNAPPROVED_EQUIPMENT", asset.id);
  if (!scene.equipment.includes(asset.id)) failIf(true, "PLACEMENT_NOT_DECLARED", `${label}:${asset.id}`);
  if (placement.viewId !== asset.viewId) failIf(true, "INVALID_EQUIPMENT_VIEW", `${label}:${placement.viewId}`);
  if (typeof placement.anchor !== "string" || !asset.anchors?.[placement.anchor]) failIf(true, "MISSING_ANCHOR", `${label}:${placement.anchor}`);
  assertPoint(`${label}.target`, placement.target, scene.canvas.width, scene.canvas.height);
  failIf(!Number.isFinite(placement.scale) || placement.scale <= 0, "INVALID_SCALE", label);
  failIf(!Number.isFinite(placement.rotationDegrees), "INVALID_ROTATION", label);
  failIf(!Number.isFinite(placement.z) || placement.z === 0, "INVALID_Z", label);
  if (typeof placement.instanceId !== "string" || !placement.instanceId) failIf(true, "MISSING_PLACEMENT_INSTANCE_ID", label);
  if (placement.includeFrontOccluder === true) {
    if (!asset.frontOccluder) failIf(true, "MISSING_FRONT_OCCLUDER", label);
    if (!asset.frontOccluder.anchors?.[placement.anchor]) failIf(true, "MISSING_ANCHOR", `${label}.frontOccluder.${placement.anchor}`);
    if (!Number.isFinite(placement.frontZ) || placement.frontZ <= 0 || placement.frontZ <= placement.z) failIf(true, "INVALID_FRONT_Z", label);
  } else if (placement.frontZ !== undefined) failIf(true, "UNUSED_FRONT_Z", label);
  return asset;
}

function placementIdentity(asset, placement, index) {
  const key = placement.instanceId ?? `${asset.id}#${index}`;
  const values = [`${key}|${asset.id}|${asset.viewId}|${asset.file}|${asset.sha256}|primary`];
  if (placement.includeFrontOccluder === true) {
    values.push(`${key}|${asset.id}|${asset.viewId}|${asset.frontOccluder?.file}|${asset.frontOccluder?.sha256}|frontOccluder`);
  }
  return values;
}

function findLockedPlacementPairs(lockedId, placementsA, placementsB) {
  const selectedA = placementsA.filter((placement) => placement.instanceId === lockedId || placement.equipmentId === lockedId);
  return selectedA.map((placementA) => {
    const placementB = placementsB.find((candidate) => candidate.instanceId === placementA.instanceId)
      ?? placementsB.find((candidate) => candidate.equipmentId === placementA.equipmentId);
    return placementB ? [placementA, placementB] : null;
  }).filter(Boolean);
}

async function main() {
  const [sharpModulePath, catalogPath, ...scenePaths] = process.argv.slice(2);
  if (!sharpModulePath || !catalogPath) {
    throw new Error("Usage: node validate-asset-manifests.mjs <sharp-module> <catalog-json> [scene-json ...]");
  }
  console.log(JSON.stringify(await validateAssetManifests({ sharpModulePath, catalogPath, scenePaths })));
}

const normalizedUrl = new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");
if (path.resolve(decodeURIComponent(normalizedUrl)) === path.resolve(process.argv[1] ?? "")) {
  main().catch((error) => {
    console.error(error?.stack ?? String(error));
    process.exitCode = 1;
  });
}
