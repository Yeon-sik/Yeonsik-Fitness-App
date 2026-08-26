#!/usr/bin/env node

/**
 * Deterministically composites catalog-approved equipment into an exercise
 * figure while preserving the source canvas and alpha channel.
 *
 * Usage:
 *   node compose-approved-equipment.mjs <sharp-module> <catalog.json>
 *     <base-figure.png> <output.png> <placements.json>
 *
 * placements.json is an array. A full-canvas asset can be placed with:
 *   {"equipmentId":"flat_bench_three_quarter_v1","mode":"canvas"}
 *
 * A transparent asset can be cropped to its visible alpha bounds and placed
 * by one of the anchors recorded in equipment-catalog.json with:
 *   {"equipmentId":"dumbbell_adjustable_three_quarter_v1",
 *    "anchor":"grip_center","center":[650,105],"scale":0.22,
 *    "rotationDegrees":0}
 *
 * All placement coordinates are in the catalog canvas coordinate system.
 * Equipment is rendered behind the figure, so the figure's hands can occlude
 * the grip naturally. The tool intentionally refuses assets or placements
 * that do not match the catalog contract.
 */

import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";

function fail(message) {
  console.error(`compose-approved-equipment: ${message}`);
  process.exit(1);
}

const [sharpModulePath, catalogPath, baseFigurePath, outputPath, placementsPath] =
  process.argv.slice(2);

if (!sharpModulePath || !catalogPath || !baseFigurePath || !outputPath || !placementsPath) {
  fail("expected <sharp-module> <catalog.json> <base-figure.png> <output.png> <placements.json>");
}

const sharp = (await import(pathToFileUrl(sharpModulePath))).default;
const catalog = JSON.parse(await fs.readFile(catalogPath, "utf8"));
const placements = JSON.parse(await fs.readFile(placementsPath, "utf8"));

if (!Array.isArray(placements) || placements.length === 0) {
  fail("placements.json must contain at least one placement");
}

const baseMetadata = await sharp(baseFigurePath).metadata();
if (!baseMetadata.width || !baseMetadata.height) {
  fail(`cannot read base figure dimensions: ${baseFigurePath}`);
}

const canvasWidth = Number(catalog.canvas?.width ?? baseMetadata.width);
const canvasHeight = Number(catalog.canvas?.height ?? baseMetadata.height);
if (baseMetadata.width !== canvasWidth || baseMetadata.height !== canvasHeight) {
  fail(`base figure must be ${canvasWidth}x${canvasHeight}; got ${baseMetadata.width}x${baseMetadata.height}`);
}

const equipmentById = new Map((catalog.assets ?? []).map((asset) => [asset.id, asset]));
const layers = [];

for (const [index, placement] of placements.entries()) {
  const asset = equipmentById.get(placement.equipmentId);
  if (!asset) fail(`placement ${index} references unknown equipment ${placement.equipmentId}`);
  if (!asset.file) fail(`equipment ${asset.id} has no catalog file`);

  const assetPath = path.resolve(path.dirname(catalogPath), asset.file);
  const assetMetadata = await sharp(assetPath).metadata();
  if (assetMetadata.width !== canvasWidth || assetMetadata.height !== canvasHeight) {
    fail(`equipment ${asset.id} must be ${canvasWidth}x${canvasHeight}; got ${assetMetadata.width}x${assetMetadata.height}`);
  }

  if (placement.mode === "canvas") {
    layers.push({ input: assetPath, left: 0, top: 0 });
    continue;
  }

  const anchorName = placement.anchor ?? "grip_center";
  const anchor = asset.anchors?.[anchorName];
  if (!Array.isArray(anchor) || anchor.length !== 2) {
    fail(`equipment ${asset.id} has no two-dimensional anchor ${anchorName}`);
  }
  if (!Array.isArray(placement.center) || placement.center.length !== 2) {
    fail(`placement ${index} needs center: [x, y]`);
  }

  const scale = Number(placement.scale ?? 1);
  if (!Number.isFinite(scale) || scale <= 0) fail(`placement ${index} has invalid scale`);
  const rotationDegrees = Number(placement.rotationDegrees ?? 0);
  if (!Number.isFinite(rotationDegrees)) fail(`placement ${index} has invalid rotationDegrees`);

  const visible = await sharp(assetPath)
    .ensureAlpha()
    .trim({ background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer({ resolveWithObject: true });
  const trimLeft = Number(visible.info.trimOffsetLeft ?? 0);
  const trimTop = Number(visible.info.trimOffsetTop ?? 0);
  const scaledWidth = Math.max(1, Math.round(visible.info.width * scale));
  const scaledHeight = Math.max(1, Math.round(visible.info.height * scale));

  const anchorInTrimmedImage = [
    (anchor[0] * canvasWidth - trimLeft) * scale,
    (anchor[1] * canvasHeight - trimTop) * scale,
  ];
  const transformed = await sharp(visible.data)
    .resize(scaledWidth, scaledHeight, { fit: "fill" })
    .rotate(rotationDegrees, { background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer({ resolveWithObject: true });

  // The current catalog placements use zero rotation. For non-zero rotation,
  // the caller must provide an explicit top-left because Sharp expands the
  // rotated image; rejecting it prevents silent anchor drift.
  if (rotationDegrees !== 0) {
    fail(`placement ${index} uses rotationDegrees=${rotationDegrees}; provide a pre-rotated approved asset instead`);
  }

  const left = Math.round(Number(placement.center[0]) - anchorInTrimmedImage[0]);
  const top = Math.round(Number(placement.center[1]) - anchorInTrimmedImage[1]);
  if (!Number.isInteger(left) || !Number.isInteger(top)) fail(`placement ${index} has invalid center`);
  if (left < -transformed.info.width || top < -transformed.info.height || left > canvasWidth || top > canvasHeight) {
    fail(`placement ${index} falls outside the canvas`);
  }
  layers.push({ input: transformed.data, left, top });
}

const baseLayer = await sharp(baseFigurePath).ensureAlpha().png().toBuffer();
await sharp({
  create: {
    width: canvasWidth,
    height: canvasHeight,
    channels: 4,
    background: { r: 0, g: 0, b: 0, alpha: 0 },
  },
})
  .composite([...layers, { input: baseLayer, left: 0, top: 0 }])
  .png()
  .toFile(outputPath);

console.log(JSON.stringify({ output: outputPath, width: canvasWidth, height: canvasHeight, placements: placements.length }));

function pathToFileUrl(filePath) {
  const absolute = path.resolve(filePath).replaceAll("\\", "/");
  return `file:///${absolute.replace(/^([A-Za-z]):/, "$1:")}`;
}
