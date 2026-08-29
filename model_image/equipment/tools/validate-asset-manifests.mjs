#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const [sharpModulePath, catalogPath, ...scenePaths] = process.argv.slice(2);
if (!sharpModulePath || !catalogPath) {
  throw new Error("Usage: node validate-asset-manifests.mjs <sharp-module> <catalog-json> [scene-json ...]");
}
const sharp = require(sharpModulePath);
const fail = (code, detail) => { throw new Error(`${code}${detail ? `: ${detail}` : ""}`); };
const isPoint = (value) => Array.isArray(value) && value.length === 2 && value.every(Number.isFinite);

function assertToken(code, value) {
  if (typeof value !== "string" || !/^[a-z0-9]+(?:_[a-z0-9]+)*$/.test(value)) fail(code, String(value));
}

function assertPoint(name, point, width, height, normalized = false) {
  if (!isPoint(point)) fail("INVALID_POINT", `${name}=${JSON.stringify(point)}`);
  const maximumX = normalized ? 1 : width;
  const maximumY = normalized ? 1 : height;
  if (point[0] < 0 || point[0] > maximumX || point[1] < 0 || point[1] > maximumY) {
    fail("POINT_OUTSIDE_CANVAS", `${name}=${JSON.stringify(point)}`);
  }
}

function assertInside(root, candidate, code) {
  const relative = path.relative(root, candidate);
  if (!relative || relative.startsWith("..") || path.isAbsolute(relative)) fail(code, candidate);
}

async function assertTransparentPng(imagePath, label, expectedWidth, expectedHeight, expectedSha, requireTransparentPixel = true) {
  const bytes = await fs.readFile(imagePath);
  if (expectedSha && crypto.createHash("sha256").update(bytes).digest("hex") !== expectedSha) fail("ASSET_IDENTITY_MISMATCH", label);
  const metadata = await sharp(bytes).metadata();
  if (metadata.format !== "png" || !metadata.hasAlpha) fail("MISSING_TRANSPARENCY", label);
  if (metadata.width !== expectedWidth || metadata.height !== expectedHeight) {
    fail("CANVAS_MISMATCH", `${label}:${metadata.width}x${metadata.height}`);
  }
  const raw = await sharp(bytes).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  let transparent = false;
  let visible = false;
  for (let offset = 3; offset < raw.data.length; offset += raw.info.channels) {
    transparent ||= raw.data[offset] === 0;
    visible ||= raw.data[offset] > 0;
    if (transparent && visible) break;
  }
  if (!visible) fail("MISSING_VISIBLE_PIXELS", label);
  if (requireTransparentPixel && !transparent) fail("MISSING_TRANSPARENCY", `${label}: no fully transparent pixel`);
}

const catalog = JSON.parse(await fs.readFile(catalogPath, "utf8"));
if (catalog.schemaVersion !== 2) fail("MISSING_CATALOG_SCHEMA_V2");
if (catalog.coordinateSystem?.origin !== "top_left" || catalog.coordinateSystem?.unit !== "asset_local_normalized_0_to_1") {
  fail("INVALID_CATALOG_COORDINATE_SYSTEM");
}
if (!Array.isArray(catalog.assets)) fail("MISSING_CATALOG_ASSETS");
const catalogDirectory = path.dirname(path.resolve(catalogPath));
const finalDirectory = path.resolve(catalogDirectory, "final");
const statuses = new Set(["draft", "approved", "deprecated"]);
const renderClasses = new Set(["movable_free_weight", "fixed_support", "fixed_machine", "cable_machine", "canonical_attachment"]);
const equipmentById = new Map();
const equipmentViews = new Set();

async function validateComponent(assetId, component, suffix = "") {
  if (!component || typeof component !== "object") fail("MISSING_EQUIPMENT_COMPONENT", `${assetId}${suffix}`);
  if (typeof component.file !== "string" || !/^final\/[a-z0-9]+(?:-[a-z0-9]+)*\.png$/.test(component.file)) {
    fail("SOURCE_ASSET_FORBIDDEN", `${assetId}${suffix}:${String(component.file)}`);
  }
  if (!Number.isInteger(component.width) || component.width <= 0 || !Number.isInteger(component.height) || component.height <= 0) {
    fail("MISSING_ASSET_DIMENSIONS", `${assetId}${suffix}`);
  }
  if (typeof component.sha256 !== "string" || !/^[a-f0-9]{64}$/.test(component.sha256)) fail("MISSING_ASSET_SHA256", `${assetId}${suffix}`);
  if (!component.anchors || typeof component.anchors !== "object" || Array.isArray(component.anchors) || Object.keys(component.anchors).length === 0) {
    fail("MISSING_ANCHOR", `${assetId}${suffix}`);
  }
  for (const [anchorName, point] of Object.entries(component.anchors)) {
    assertToken("INVALID_ANCHOR_NAME", anchorName);
    assertPoint(`${assetId}${suffix}.${anchorName}`, point, 1, 1, true);
  }
  const imagePath = path.resolve(catalogDirectory, component.file);
  assertInside(finalDirectory, imagePath, "SOURCE_ASSET_FORBIDDEN");
  await assertTransparentPng(imagePath, `${assetId}${suffix}`, component.width, component.height, component.sha256, false);
}

for (const asset of catalog.assets) {
  assertToken("INVALID_EQUIPMENT_ID", asset.id);
  assertToken("INVALID_EQUIPMENT_TYPE", asset.type);
  assertToken("INVALID_EQUIPMENT_VIEW", asset.viewId);
  if (equipmentById.has(asset.id)) fail("DUPLICATE_EQUIPMENT_ID", asset.id);
  const viewKey = `${asset.type}|${asset.viewId}`;
  if (equipmentViews.has(viewKey)) fail("DUPLICATE_EQUIPMENT_TYPE_VIEW", viewKey);
  if (!statuses.has(asset.status)) fail("INVALID_STATUS", `${asset.id}:${asset.status}`);
  if (!renderClasses.has(asset.renderClass)) fail("INVALID_RENDER_CLASS", `${asset.id}:${asset.renderClass}`);
  equipmentById.set(asset.id, asset);
  equipmentViews.add(viewKey);
  await validateComponent(asset.id, asset);
  if (asset.frontOccluder) {
    if (!["fixed_machine", "cable_machine"].includes(asset.renderClass)) fail("INVALID_FRONT_OCCLUDER_CLASS", asset.id);
    await validateComponent(asset.id, asset.frontOccluder, ".frontOccluder");
  }
}

function placementIdentity(asset, placement) {
  const values = [`${asset.id}|${asset.file}|${asset.sha256}|primary`];
  if (placement.includeFrontOccluder === true) {
    values.push(`${asset.id}|${asset.frontOccluder?.file}|${asset.frontOccluder?.sha256}|frontOccluder`);
  }
  return values;
}

function validatePlacement(scene, frame, placement, index) {
  const label = `${scene.exerciseId}.${frame.id}.equipmentPlacements[${index}]`;
  const asset = equipmentById.get(placement?.equipmentId);
  if (!asset) fail("MISSING_EQUIPMENT", `${label}:${String(placement?.equipmentId)}`);
  if (asset.status !== "approved") fail("UNAPPROVED_EQUIPMENT", asset.id);
  if (!scene.equipment.includes(asset.id)) fail("PLACEMENT_NOT_DECLARED", `${label}:${asset.id}`);
  if (placement.viewId !== undefined && placement.viewId !== asset.viewId) fail("INVALID_EQUIPMENT_VIEW", `${label}:${placement.viewId}`);
  if (typeof placement.anchor !== "string" || !asset.anchors?.[placement.anchor]) fail("MISSING_ANCHOR", `${label}:${placement.anchor}`);
  assertPoint(`${label}.target`, placement.target, scene.canvas.width, scene.canvas.height);
  if (!Number.isFinite(placement.scale) || placement.scale <= 0) fail("INVALID_SCALE", label);
  if (!Number.isFinite(placement.rotationDegrees)) fail("INVALID_ROTATION", label);
  if (!Number.isFinite(placement.z) || placement.z === 0) fail("INVALID_Z", label);
  if (placement.includeFrontOccluder === true) {
    if (!asset.frontOccluder) fail("MISSING_FRONT_OCCLUDER", label);
    if (!asset.frontOccluder.anchors?.[placement.anchor]) fail("MISSING_ANCHOR", `${label}.frontOccluder.${placement.anchor}`);
    if (!Number.isFinite(placement.frontZ) || placement.frontZ <= 0 || placement.frontZ <= placement.z) fail("INVALID_FRONT_Z", label);
  } else if (placement.frontZ !== undefined) fail("UNUSED_FRONT_Z", label);
  return asset;
}

let frameCount = 0;
let placementCount = 0;
for (const scenePath of scenePaths) {
  const scene = JSON.parse(await fs.readFile(scenePath, "utf8"));
  if (!scene.canvas || !Number.isInteger(scene.canvas.width) || !Number.isInteger(scene.canvas.height)) fail("MISSING_SCENE_CANVAS", scenePath);
  if (!Array.isArray(scene.equipment)) fail("MISSING_SCENE_EQUIPMENT", scenePath);
  if (new Set(scene.equipment).size !== scene.equipment.length) fail("DUPLICATE_SCENE_EQUIPMENT", scene.exerciseId);
  for (const equipmentId of scene.equipment) {
    const asset = equipmentById.get(equipmentId);
    if (!asset) fail("MISSING_EQUIPMENT", `${scene.exerciseId}:${equipmentId}`);
    if (asset.status !== "approved") fail("UNAPPROVED_EQUIPMENT", `${scene.exerciseId}:${equipmentId}`);
    const expectedView = scene.equipmentViews?.[equipmentId];
    if (expectedView !== undefined && expectedView !== asset.viewId) fail("INVALID_EQUIPMENT_VIEW", `${scene.exerciseId}:${equipmentId}`);
  }
  for (const [anchorName, point] of Object.entries(scene.lockedAnchors ?? {})) {
    assertPoint(`${scene.exerciseId}.${anchorName}`, point, scene.canvas.width, scene.canvas.height);
  }
  if (!Array.isArray(scene.frames) || scene.frames.length !== 2 || scene.frames[0]?.id !== "A" || scene.frames[1]?.id !== "B") {
    fail("INVALID_AB_FRAMES", scene.exerciseId);
  }
  const slug = scene.slug ?? path.basename(scenePath, ".scene.json");
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug)) fail("INVALID_SCENE_SLUG", slug);
  const hasPlacements = scene.frames.map((frame) => Array.isArray(frame.equipmentPlacements));
  if (hasPlacements.some(Boolean) && !hasPlacements.every(Boolean)) fail("MISSING_EQUIPMENT_PLACEMENT", scene.exerciseId);
  const identities = [];
  const placementsByFrame = [];

  for (const frame of scene.frames) {
    const expectedName = `${slug}-${frame.id.toLowerCase()}.png`;
    if (path.basename(frame.file) !== expectedName || !String(frame.file).replaceAll("\\", "/").includes("/final/")) {
      fail("INVALID_FRAME_FILENAME", `${scene.exerciseId}:${frame.file}; expected ${expectedName}`);
    }
    const imagePath = path.resolve(path.dirname(scenePath), frame.file);
    await assertTransparentPng(imagePath, `${scene.exerciseId}.${frame.id}`, scene.canvas.width, scene.canvas.height);
    for (const [jointName, point] of Object.entries(frame.joints ?? {})) assertPoint(`${frame.id}.${jointName}`, point, scene.canvas.width, scene.canvas.height);
    const framePlacements = frame.equipmentPlacements ?? [];
    if (hasPlacements.every(Boolean)) {
      const frameIds = new Set();
      const frameIdentity = [];
      framePlacements.forEach((placement, index) => {
        const asset = validatePlacement(scene, frame, placement, index);
        frameIds.add(asset.id);
        frameIdentity.push(...placementIdentity(asset, placement));
      });
      if ([...new Set(scene.equipment)].some((id) => !frameIds.has(id))) fail("MISSING_EQUIPMENT_PLACEMENT", `${scene.exerciseId}.${frame.id}`);
      identities.push(frameIdentity.sort());
    }
    placementsByFrame.push(framePlacements);
    placementCount += framePlacements.length;
    frameCount += 1;
  }
  if (identities.length === 2 && JSON.stringify(identities[0]) !== JSON.stringify(identities[1])) fail("AB_EQUIPMENT_IDENTITY_MISMATCH", scene.exerciseId);

  const lockedIds = new Set(scene.compositionContract?.lockedEquipment ?? []);
  const tolerance = Number(scene.compositionContract?.lockedAnchorTolerancePixels ?? Math.max(scene.canvas.width, scene.canvas.height) * 0.005);
  if (!Number.isFinite(tolerance) || tolerance < 0) fail("INVALID_LOCKED_ANCHOR_TOLERANCE", scene.exerciseId);
  for (const [index, placementA] of placementsByFrame[0].entries()) {
    if (placementA.locked !== true && !lockedIds.has(placementA.equipmentId)) continue;
    const key = placementA.instanceId ?? `${placementA.equipmentId}#${index}`;
    const placementB = placementsByFrame[1].find((item, candidateIndex) => (item.instanceId ?? `${item.equipmentId}#${candidateIndex}`) === key);
    if (!placementB) fail("MISSING_LOCKED_EQUIPMENT_INSTANCE", `${scene.exerciseId}:${key}`);
    const drift = Math.hypot(placementA.target[0] - placementB.target[0], placementA.target[1] - placementB.target[1]);
    if (drift > tolerance) fail("LOCKED_ANCHOR_DRIFT", `${scene.exerciseId}:${key}:${drift}`);
    if (Math.abs(placementA.scale - placementB.scale) / placementA.scale > 0.01) fail("LOCKED_EQUIPMENT_SCALE_DRIFT", `${scene.exerciseId}:${key}`);
    if (Math.abs(placementA.rotationDegrees - placementB.rotationDegrees) > 0.5) fail("LOCKED_EQUIPMENT_ROTATION_DRIFT", `${scene.exerciseId}:${key}`);
  }
}

console.log(JSON.stringify({ valid: true, equipmentAssets: equipmentById.size, scenes: scenePaths.length, frames: frameCount, placements: placementCount }));
