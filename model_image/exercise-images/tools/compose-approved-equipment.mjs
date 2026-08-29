#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const fail = (code, detail) => { throw new Error(`compose-approved-equipment:${code}${detail ? `: ${detail}` : ""}`); };
const isPoint = (value) => Array.isArray(value) && value.length === 2 && value.every(Number.isFinite);

async function assertTransparentPixels(sharp, bytes, label, requireTransparentPixel = true) {
  const raw = await sharp(bytes).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  let transparent = false;
  let visible = false;
  for (let offset = 3; offset < raw.data.length; offset += raw.info.channels) {
    transparent ||= raw.data[offset] === 0;
    visible ||= raw.data[offset] > 0;
    if (visible && (transparent || !requireTransparentPixel)) return;
  }
  if (!visible) fail("MISSING_VISIBLE_PIXELS", label);
  fail("MISSING_TRANSPARENCY", `${label}: no fully transparent pixel`);
}

function assertInside(root, candidate, code) {
  const relative = path.relative(root, candidate);
  if (!relative || relative.startsWith("..") || path.isAbsolute(relative)) fail(code, candidate);
}

function assertAnchor(equipmentId, name, point) {
  if (!isPoint(point) || point.some((value) => value < 0 || value > 1)) fail("MISSING_ANCHOR", `${equipmentId}.${name}`);
}

async function verifyComponent(sharp, catalogDirectory, equipmentId, component) {
  if (typeof component.file !== "string" || !/^final\/[a-z0-9]+(?:-[a-z0-9]+)*\.png$/.test(component.file)) {
    fail("SOURCE_ASSET_FORBIDDEN", `${equipmentId}:${String(component.file)}`);
  }
  if (!Number.isInteger(component.width) || component.width <= 0 || !Number.isInteger(component.height) || component.height <= 0) {
    fail("MISSING_ASSET_DIMENSIONS", equipmentId);
  }
  if (typeof component.sha256 !== "string" || !/^[a-f0-9]{64}$/.test(component.sha256)) fail("MISSING_ASSET_SHA256", equipmentId);
  const absolutePath = path.resolve(catalogDirectory, component.file);
  assertInside(path.resolve(catalogDirectory, "final"), absolutePath, "SOURCE_ASSET_FORBIDDEN");
  const bytes = await fs.readFile(absolutePath);
  if (crypto.createHash("sha256").update(bytes).digest("hex") !== component.sha256) fail("ASSET_IDENTITY_MISMATCH", equipmentId);
  const metadata = await sharp(bytes).metadata();
  if (metadata.format !== "png" || !metadata.hasAlpha) fail("MISSING_TRANSPARENCY", equipmentId);
  // A tightly trimmed canonical asset may have no transparent border pixels;
  // hasAlpha plus visible pixels remains mandatory. The source promotion gate
  // separately proves that transparency existed before trimming.
  await assertTransparentPixels(sharp, bytes, equipmentId, false);
  if (metadata.width !== component.width || metadata.height !== component.height) {
    fail("ASSET_DIMENSIONS_MISMATCH", `${equipmentId}:${metadata.width}x${metadata.height}`);
  }
  return bytes;
}

async function renderPlacement(sharp, canvas, equipmentId, component, placement, anchorName) {
  const anchor = component.anchors?.[anchorName];
  assertAnchor(equipmentId, anchorName, anchor);
  if (!isPoint(placement.target)) fail("MISSING_TARGET", equipmentId);
  if (placement.target[0] < 0 || placement.target[0] > canvas.width || placement.target[1] < 0 || placement.target[1] > canvas.height) {
    fail("TARGET_OUTSIDE_CANVAS", equipmentId);
  }
  const scale = Number(placement.scale);
  const rotation = Number(placement.rotationDegrees);
  if (!Number.isFinite(scale) || scale <= 0) fail("INVALID_SCALE", equipmentId);
  if (!Number.isFinite(rotation)) fail("INVALID_ROTATION", equipmentId);
  const bytes = await verifyComponent(sharp, canvas.catalogDirectory, equipmentId, component);
  const scaledWidth = Math.max(1, Math.round(component.width * scale));
  const scaledHeight = Math.max(1, Math.round(component.height * scale));
  const transformed = await sharp(bytes)
    .resize(scaledWidth, scaledHeight, { fit: "fill" })
    .rotate(rotation, { background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer({ resolveWithObject: true });
  const radians = rotation * Math.PI / 180;
  const dx = anchor[0] * scaledWidth - scaledWidth / 2;
  const dy = anchor[1] * scaledHeight - scaledHeight / 2;
  const rotatedAnchorX = Math.cos(radians) * dx - Math.sin(radians) * dy + transformed.info.width / 2;
  const rotatedAnchorY = Math.sin(radians) * dx + Math.cos(radians) * dy + transformed.info.height / 2;
  const left = Math.round(placement.target[0] - rotatedAnchorX);
  const top = Math.round(placement.target[1] - rotatedAnchorY);
  const sourceLeft = Math.max(0, -left);
  const sourceTop = Math.max(0, -top);
  const destinationLeft = Math.max(0, left);
  const destinationTop = Math.max(0, top);
  const visibleWidth = Math.min(transformed.info.width - sourceLeft, canvas.width - destinationLeft);
  const visibleHeight = Math.min(transformed.info.height - sourceTop, canvas.height - destinationTop);
  if (visibleWidth <= 0 || visibleHeight <= 0) fail("PLACEMENT_OUTSIDE_CANVAS", equipmentId);
  const cropped = await sharp(transformed.data)
    .extract({ left: sourceLeft, top: sourceTop, width: visibleWidth, height: visibleHeight })
    .png().toBuffer();
  return sharp({
    create: {
      width: canvas.width, height: canvas.height, channels: 4,
      background: { r: 0, g: 0, b: 0, alpha: 0 },
    },
  }).composite([{ input: cropped, left: destinationLeft, top: destinationTop }]).png().toBuffer();
}

const [sharpModulePath, catalogPath, mannequinPath, outputPath, placementsPath] = process.argv.slice(2);
if (!sharpModulePath || !catalogPath || !mannequinPath || !outputPath || !placementsPath) {
  fail("MISSING_ARGUMENT", "expected <sharp-module> <catalog.json> <mannequin.png> <output.png> <placements.json>");
}
const sharp = require(sharpModulePath);
const catalog = JSON.parse(await fs.readFile(catalogPath, "utf8"));
if (catalog.schemaVersion !== 2) fail("MISSING_CATALOG_SCHEMA_V2");
if (catalog.coordinateSystem?.unit !== "asset_local_normalized_0_to_1") fail("INVALID_CATALOG_COORDINATE_SYSTEM");
if (!Array.isArray(catalog.assets)) fail("MISSING_CATALOG_ASSETS");
const placementsDocument = JSON.parse(await fs.readFile(placementsPath, "utf8"));
const placements = Array.isArray(placementsDocument) ? placementsDocument : placementsDocument.placements;
if (!Array.isArray(placements) || placements.length === 0) fail("MISSING_PLACEMENTS");

const mannequinBytes = await fs.readFile(mannequinPath);
const mannequinMetadata = await sharp(mannequinBytes).metadata();
if (mannequinMetadata.format !== "png" || !mannequinMetadata.hasAlpha || !mannequinMetadata.width || !mannequinMetadata.height) {
  fail("INVALID_MANNEQUIN", "mannequin must be an alpha PNG");
}
await assertTransparentPixels(sharp, mannequinBytes, "mannequin");
const canvas = { width: mannequinMetadata.width, height: mannequinMetadata.height, catalogDirectory: path.dirname(path.resolve(catalogPath)) };
const equipmentById = new Map();
for (const asset of catalog.assets) {
  if (equipmentById.has(asset.id)) fail("DUPLICATE_EQUIPMENT_ID", asset.id);
  equipmentById.set(asset.id, asset);
}

const layers = [];
for (const [index, placement] of placements.entries()) {
  const asset = equipmentById.get(placement?.equipmentId);
  if (!asset) fail("MISSING_EQUIPMENT", String(placement?.equipmentId));
  if (asset.status !== "approved") fail("UNAPPROVED_EQUIPMENT", asset.id);
  if (typeof placement.anchor !== "string" || !placement.anchor) fail("MISSING_ANCHOR", asset.id);
  const z = Number(placement.z);
  if (!Number.isFinite(z) || z === 0) fail("INVALID_Z", `${asset.id}; z=0 is mannequin-only`);
  if (placement.viewId !== undefined && placement.viewId !== asset.viewId) {
    fail("INVALID_EQUIPMENT_VIEW", `${asset.id}:${placement.viewId} != ${asset.viewId}`);
  }
  layers.push({ input: await renderPlacement(sharp, canvas, asset.id, asset, placement, placement.anchor), z, order: index * 2, equipmentId: asset.id, component: "primary" });
  if (placement.includeFrontOccluder === true) {
    if (!["fixed_machine", "cable_machine"].includes(asset.renderClass)) fail("INVALID_FRONT_OCCLUDER_CLASS", asset.id);
    if (!asset.frontOccluder) fail("MISSING_FRONT_OCCLUDER", asset.id);
    const frontZ = Number(placement.frontZ);
    if (!Number.isFinite(frontZ) || frontZ <= 0 || frontZ <= z) fail("INVALID_FRONT_Z", asset.id);
    layers.push({
      input: await renderPlacement(sharp, canvas, `${asset.id}.frontOccluder`, asset.frontOccluder, placement, placement.anchor),
      z: frontZ, order: index * 2 + 1, equipmentId: asset.id, component: "frontOccluder",
    });
  } else if (placement.frontZ !== undefined) fail("UNUSED_FRONT_Z", asset.id);
}

layers.push({ input: await sharp(mannequinBytes).ensureAlpha().png().toBuffer(), z: 0, order: -1, component: "mannequin" });
layers.sort((left, right) => left.z - right.z || left.order - right.order);
await fs.mkdir(path.dirname(path.resolve(outputPath)), { recursive: true });
await sharp({ create: { width: canvas.width, height: canvas.height, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
  .composite(layers.map(({ input }) => ({ input, left: 0, top: 0 }))).png().toFile(outputPath);
console.log(JSON.stringify({
  output: outputPath, width: canvas.width, height: canvas.height, equipmentInstances: placements.length,
  layers: layers.map(({ z, equipmentId, component }) => ({ z, equipmentId, component })),
}));
