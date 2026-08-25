#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const [
  sharpModulePath,
  catalogPath,
  baseFigurePath,
  outputPath,
  placementsPath,
  placementSet,
] = process.argv.slice(2);

if (!sharpModulePath || !catalogPath || !baseFigurePath || !outputPath || !placementsPath) {
  throw new Error(
    "Usage: node compose-anchored-equipment.mjs <sharp-module> <catalog.json> "
      + "<base-figure.png> <output.png> <placements.json> [placement-set]",
  );
}

const sharp = require(sharpModulePath);
const catalog = JSON.parse(await fs.readFile(catalogPath, "utf8"));
const placementDocument = JSON.parse(await fs.readFile(placementsPath, "utf8"));
const placements = Array.isArray(placementDocument)
  ? placementDocument
  : placementDocument[placementSet];

if (!Array.isArray(placements) || placements.length === 0) {
  throw new Error(`No placements found${placementSet ? ` for ${placementSet}` : ""}`);
}

const baseMetadata = await sharp(baseFigurePath).metadata();
const canvasWidth = Number(catalog.canvas?.width ?? baseMetadata.width);
const canvasHeight = Number(catalog.canvas?.height ?? baseMetadata.height);
if (baseMetadata.width !== canvasWidth || baseMetadata.height !== canvasHeight) {
  throw new Error(
    `Base figure must be ${canvasWidth}x${canvasHeight}; `
      + `got ${baseMetadata.width}x${baseMetadata.height}`,
  );
}

const equipmentById = new Map((catalog.assets ?? []).map((asset) => [asset.id, asset]));
const layers = [];

for (const [index, placement] of placements.entries()) {
  const asset = equipmentById.get(placement.equipmentId);
  if (!asset) throw new Error(`Unknown equipment at placement ${index}: ${placement.equipmentId}`);
  const assetPath = path.resolve(path.dirname(catalogPath), asset.file);
  const assetMetadata = await sharp(assetPath).metadata();
  if (assetMetadata.width !== canvasWidth || assetMetadata.height !== canvasHeight) {
    throw new Error(`Equipment ${asset.id} does not match the scene canvas`);
  }

  if (placement.mode === "canvas") {
    layers.push({ input: assetPath, left: 0, top: 0 });
    continue;
  }

  const anchorName = placement.anchor ?? "grip_center";
  const anchor = asset.anchors?.[anchorName];
  if (!Array.isArray(anchor) || anchor.length !== 2) {
    throw new Error(`Equipment ${asset.id} has no anchor ${anchorName}`);
  }
  if (!Array.isArray(placement.center) || placement.center.length !== 2) {
    throw new Error(`Placement ${index} needs center: [x, y]`);
  }

  const scale = Number(placement.scale ?? 1);
  const rotationDegrees = Number(placement.rotationDegrees ?? 0);
  if (!Number.isFinite(scale) || scale <= 0) throw new Error(`Invalid scale at ${index}`);
  if (!Number.isFinite(rotationDegrees)) throw new Error(`Invalid rotation at ${index}`);

  const visible = await sharp(assetPath)
    .ensureAlpha()
    .trim({ background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer({ resolveWithObject: true });
  // Sharp reports trim offsets as negative translation from the source canvas.
  const trimLeft = Number(visible.info.trimOffsetLeft ?? 0);
  const trimTop = Number(visible.info.trimOffsetTop ?? 0);
  const scaledWidth = Math.max(1, Math.round(visible.info.width * scale));
  const scaledHeight = Math.max(1, Math.round(visible.info.height * scale));
  const anchorInCrop = [
    (anchor[0] * canvasWidth + trimLeft) * scale,
    (anchor[1] * canvasHeight + trimTop) * scale,
  ];
  const resized = await sharp(visible.data)
    .resize(scaledWidth, scaledHeight, { fit: "fill" })
    .png()
    .toBuffer();

  const radius = Math.ceil(Math.max(
    Math.hypot(anchorInCrop[0], anchorInCrop[1]),
    Math.hypot(scaledWidth - anchorInCrop[0], anchorInCrop[1]),
    Math.hypot(anchorInCrop[0], scaledHeight - anchorInCrop[1]),
    Math.hypot(scaledWidth - anchorInCrop[0], scaledHeight - anchorInCrop[1]),
  )) + 4;
  const carrierSize = radius * 2 + 1;
  const carrier = await sharp({
    create: {
      width: carrierSize,
      height: carrierSize,
      channels: 4,
      background: { r: 0, g: 0, b: 0, alpha: 0 },
    },
  })
    .composite([{
      input: resized,
      left: Math.round(radius - anchorInCrop[0]),
      top: Math.round(radius - anchorInCrop[1]),
    }])
    .png()
    .toBuffer();
  const rotated = await sharp(carrier)
    .rotate(rotationDegrees, { background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer({ resolveWithObject: true });
  const transformed = await sharp(rotated.data)
    .trim({ background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer({ resolveWithObject: true });
  const anchorAfterTransform = [
    (rotated.info.width - 1) / 2 + Number(transformed.info.trimOffsetLeft ?? 0),
    (rotated.info.height - 1) / 2 + Number(transformed.info.trimOffsetTop ?? 0),
  ];
  const left = Math.round(Number(placement.center[0]) - anchorAfterTransform[0]);
  const top = Math.round(Number(placement.center[1]) - anchorAfterTransform[1]);
  if (
    left < -transformed.info.width
    || top < -transformed.info.height
    || left > canvasWidth
    || top > canvasHeight
  ) {
    throw new Error(`Placement ${index} falls outside the scene canvas`);
  }
  layers.push({ input: transformed.data, left, top });
}

const baseLayer = await sharp(baseFigurePath).ensureAlpha().png().toBuffer();
await fs.mkdir(path.dirname(outputPath), { recursive: true });
await sharp({
  create: {
    width: canvasWidth,
    height: canvasHeight,
    channels: 4,
    background: { r: 0, g: 0, b: 0, alpha: 0 },
  },
})
  .composite([...layers, { input: baseLayer, left: 0, top: 0 }])
  .png({ compressionLevel: 9, adaptiveFiltering: true })
  .toFile(outputPath);

console.log(JSON.stringify({
  output: outputPath,
  width: canvasWidth,
  height: canvasHeight,
  placementSet: placementSet ?? null,
  placements: placements.length,
}));
