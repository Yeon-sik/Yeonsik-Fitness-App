#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);

function fail(code, detail) {
  throw new Error(`${code}${detail ? `: ${detail}` : ""}`);
}

function usage() {
  return [
    "Usage: node promote-equipment.mjs <sharp-module> <catalog.json> <source.png>",
    "  --id <equipment_id> --type <type> --view-id <view_id>",
    "  --file <final/kebab-name.png> --anchors <anchors.json>",
    "  --status <draft|approved|deprecated>",
    "  --render-class <movable_free_weight|fixed_support|fixed_machine|cable_machine|canonical_attachment>",
    "  [--trim] [--replace]",
  ].join("\n");
}

function parseArgs(argv) {
  if (argv.length < 3) fail("MISSING_ARGUMENT", usage());
  const [sharpModulePath, catalogPath, sourcePath, ...rest] = argv;
  const options = { trim: false, replace: false };
  for (let index = 0; index < rest.length; index += 1) {
    const flag = rest[index];
    if (flag === "--trim" || flag === "--replace") {
      options[flag.slice(2)] = true;
      continue;
    }
    if (!flag.startsWith("--")) fail("INVALID_ARGUMENT", flag);
    const value = rest[index + 1];
    if (!value || value.startsWith("--")) fail("MISSING_ARGUMENT", flag);
    options[flag.slice(2)] = value;
    index += 1;
  }
  return { sharpModulePath, catalogPath, sourcePath, options };
}

function assertToken(code, value) {
  if (typeof value !== "string" || !/^[a-z0-9]+(?:_[a-z0-9]+)*$/.test(value)) {
    fail(code, String(value ?? ""));
  }
}

function assertInside(root, candidate, code) {
  const relative = path.relative(root, candidate);
  if (!relative || relative.startsWith("..") || path.isAbsolute(relative)) fail(code, candidate);
}

function validateAnchors(anchors) {
  if (!anchors || typeof anchors !== "object" || Array.isArray(anchors) || Object.keys(anchors).length === 0) {
    fail("MISSING_ANCHOR", "anchors must be a non-empty object");
  }
  for (const [name, point] of Object.entries(anchors)) {
    assertToken("INVALID_ANCHOR_NAME", name);
    if (!Array.isArray(point) || point.length !== 2 || !point.every(Number.isFinite)) {
      fail("INVALID_ANCHOR", `${name}=${JSON.stringify(point)}`);
    }
    if (point.some((coordinate) => coordinate < 0 || coordinate > 1)) {
      fail("INVALID_ANCHOR", `${name} must be asset-local normalized [0,1]`);
    }
  }
}

function alphaBounds(data, width, height, channels) {
  if (channels !== 4) fail("MISSING_TRANSPARENCY", `expected RGBA pixels, got ${channels} channels`);
  let left = width;
  let top = height;
  let right = -1;
  let bottom = -1;
  let transparentPixels = 0;
  let visiblePixels = 0;
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const alpha = data[(y * width + x) * channels + 3];
      if (alpha === 0) transparentPixels += 1;
      if (alpha > 0) {
        visiblePixels += 1;
        left = Math.min(left, x);
        top = Math.min(top, y);
        right = Math.max(right, x);
        bottom = Math.max(bottom, y);
      }
    }
  }
  if (visiblePixels === 0) fail("MISSING_VISIBLE_PIXELS", "source is fully transparent");
  if (transparentPixels === 0) fail("MISSING_TRANSPARENCY", "source has no fully transparent pixel");
  return { left, top, width: right - left + 1, height: bottom - top + 1 };
}

function recalculateAnchors(anchors, originalWidth, originalHeight, bounds) {
  const adjusted = {};
  for (const [name, [x, y]] of Object.entries(anchors)) {
    const next = [
      (x * originalWidth - bounds.left) / bounds.width,
      (y * originalHeight - bounds.top) / bounds.height,
    ];
    if (next.some((coordinate) => !Number.isFinite(coordinate) || coordinate < 0 || coordinate > 1)) {
      fail("ANCHOR_OUTSIDE_TRIMMED_ASSET", `${name}=${JSON.stringify(next)}`);
    }
    adjusted[name] = next.map((coordinate) => Number(coordinate.toFixed(8)));
  }
  return adjusted;
}

const { sharpModulePath, catalogPath, sourcePath, options } = parseArgs(process.argv.slice(2));
for (const key of ["id", "type", "view-id", "file", "anchors", "status", "render-class"]) {
  if (!options[key]) fail(`MISSING_${key.replaceAll("-", "_").toUpperCase()}`);
}

assertToken("INVALID_EQUIPMENT_ID", options.id);
assertToken("INVALID_EQUIPMENT_TYPE", options.type);
assertToken("INVALID_EQUIPMENT_VIEW", options["view-id"]);

const statuses = new Set(["draft", "approved", "deprecated"]);
if (!statuses.has(options.status)) fail("INVALID_STATUS", options.status);
const renderClasses = new Set([
  "movable_free_weight",
  "fixed_support",
  "fixed_machine",
  "cable_machine",
  "canonical_attachment",
]);
if (!renderClasses.has(options["render-class"])) fail("INVALID_RENDER_CLASS", options["render-class"]);

const normalizedFile = options.file.replaceAll("\\", "/");
if (!/^final\/[a-z0-9]+(?:[-_][a-z0-9]+)*\.png$/.test(normalizedFile)) {
  fail("INVALID_CANONICAL_FILENAME", normalizedFile);
}

const absoluteCatalog = path.resolve(catalogPath);
const catalogDirectory = path.dirname(absoluteCatalog);
const absoluteSource = path.resolve(sourcePath);
const sourceDirectory = path.resolve(catalogDirectory, "source");
const finalDirectory = path.resolve(catalogDirectory, "final");
assertInside(sourceDirectory, absoluteSource, "SOURCE_MUST_BE_UNDER_EQUIPMENT_SOURCE");
const absoluteOutput = path.resolve(catalogDirectory, normalizedFile);
assertInside(finalDirectory, absoluteOutput, "FINAL_MUST_BE_UNDER_EQUIPMENT_FINAL");

const catalog = JSON.parse(await fs.readFile(absoluteCatalog, "utf8"));
if (catalog.schemaVersion !== 2) fail("MISSING_CATALOG_SCHEMA_V2");
if (catalog.coordinateSystem?.origin !== "top_left"
    || catalog.coordinateSystem?.unit !== "asset_local_normalized_0_to_1") {
  fail("INVALID_CATALOG_COORDINATE_SYSTEM");
}
if (!Array.isArray(catalog.assets)) fail("MISSING_CATALOG_ASSETS");

const anchorsDocument = JSON.parse(await fs.readFile(path.resolve(options.anchors), "utf8"));
const anchors = anchorsDocument.anchors ?? anchorsDocument;
validateAnchors(anchors);

const sharp = require(sharpModulePath);
const metadata = await sharp(absoluteSource).metadata();
if (metadata.format !== "png") fail("INVALID_SOURCE_FORMAT", metadata.format);
if (!metadata.width || !metadata.height || !metadata.hasAlpha) fail("MISSING_TRANSPARENCY");
const raw = await sharp(absoluteSource).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
const visibleBounds = alphaBounds(raw.data, raw.info.width, raw.info.height, raw.info.channels);
const crop = options.trim
  ? visibleBounds
  : { left: 0, top: 0, width: metadata.width, height: metadata.height };
const outputAnchors = options.trim
  ? recalculateAnchors(anchors, metadata.width, metadata.height, crop)
  : anchors;

const canonicalBuffer = await sharp(absoluteSource)
  .extract(crop)
  .ensureAlpha()
  .png()
  .toBuffer({ resolveWithObject: true });
const sha256 = crypto.createHash("sha256").update(canonicalBuffer.data).digest("hex");

const existingIndex = catalog.assets.findIndex((asset) => asset.id === options.id);
const collidingView = catalog.assets.find((asset) =>
  asset.id !== options.id && asset.type === options.type && asset.viewId === options["view-id"]);
if (collidingView) fail("DUPLICATE_EQUIPMENT_TYPE_VIEW", collidingView.id);
if (existingIndex >= 0 && !options.replace) fail("EQUIPMENT_ID_EXISTS", options.id);
if (existingIndex >= 0 && catalog.assets[existingIndex].frontOccluder) {
  fail("EXISTING_FRONT_OCCLUDER_REQUIRES_EXPLICIT_MANAGEMENT", options.id);
}
try {
  await fs.access(absoluteOutput);
  if (!options.replace) fail("FINAL_FILE_EXISTS", normalizedFile);
} catch (error) {
  if (error.code !== "ENOENT") throw error;
}

const asset = {
  id: options.id,
  type: options.type,
  viewId: options["view-id"],
  file: normalizedFile,
  width: canonicalBuffer.info.width,
  height: canonicalBuffer.info.height,
  sha256,
  anchors: outputAnchors,
  status: options.status,
  renderClass: options["render-class"],
};
if (existingIndex >= 0) catalog.assets[existingIndex] = asset;
else catalog.assets.push(asset);

await fs.mkdir(path.dirname(absoluteOutput), { recursive: true });
const nonce = `${process.pid}-${Date.now()}`;
const outputTemp = `${absoluteOutput}.${nonce}.tmp`;
const catalogTemp = `${absoluteCatalog}.${nonce}.tmp`;
try {
  await fs.writeFile(outputTemp, canonicalBuffer.data, { flag: "wx" });
  await fs.writeFile(catalogTemp, `${JSON.stringify(catalog, null, 2)}\n`, { flag: "wx" });
  if (options.replace) await fs.copyFile(outputTemp, absoluteOutput);
  else await fs.rename(outputTemp, absoluteOutput);
  // Windows does not reliably allow rename() over an existing destination.
  // copyFile replaces that one explicitly named catalog without a delete gap.
  await fs.copyFile(catalogTemp, absoluteCatalog);
} finally {
  await fs.rm(outputTemp, { force: true });
  await fs.rm(catalogTemp, { force: true });
}

console.log(JSON.stringify({
  promoted: true,
  id: asset.id,
  status: asset.status,
  file: asset.file,
  width: asset.width,
  height: asset.height,
  sha256: asset.sha256,
  trimmed: options.trim,
}));
