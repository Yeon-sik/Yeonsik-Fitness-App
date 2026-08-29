#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const PNG_SIGNATURE = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);

export function readPngDimensions(bytes, label = "PNG") {
  if (!Buffer.isBuffer(bytes) || bytes.length < 24 || !bytes.subarray(0, 8).equals(PNG_SIGNATURE)) {
    throw new Error(`INVALID_PNG: ${label}`);
  }
  if (bytes.subarray(12, 16).toString("ascii") !== "IHDR") throw new Error(`INVALID_PNG_IHDR: ${label}`);
  const width = bytes.readUInt32BE(16);
  const height = bytes.readUInt32BE(20);
  if (width <= 0 || height <= 0) throw new Error(`INVALID_PNG_DIMENSIONS: ${label}`);
  return { width, height };
}

function isInside(root, candidate) {
  const relative = path.relative(root, candidate);
  return relative && !relative.startsWith("..") && !path.isAbsolute(relative);
}

function normalizeFile(file) {
  return String(file ?? "").replaceAll("\\", "/");
}

function componentFromV1(asset, field = "") {
  const component = field ? asset[field] : asset;
  if (!component || typeof component !== "object") return null;
  const file = normalizeFile(component.file);
  if (!file.startsWith("final/")) return null;
  return { file, anchors: component.anchors ?? {} };
}

function decisionFor(decisions, id) {
  return decisions?.assets?.[id] ?? decisions?.[id] ?? {};
}

async function migrateComponent({ component, catalogDirectory, finalDirectory, label }) {
  const normalizedFile = normalizeFile(component.file);
  if (!/^final\/[a-z0-9]+(?:[-_][a-z0-9]+)*\.png$/.test(normalizedFile)) {
    throw new Error(`SOURCE_ASSET_FORBIDDEN: ${label}:${normalizedFile}`);
  }
  const absolutePath = path.resolve(catalogDirectory, normalizedFile);
  if (!isInside(finalDirectory, absolutePath)) throw new Error(`SOURCE_ASSET_FORBIDDEN: ${label}:${normalizedFile}`);
  const bytes = await fs.readFile(absolutePath);
  const dimensions = readPngDimensions(bytes, absolutePath);
  return {
    file: normalizedFile,
    width: dimensions.width,
    height: dimensions.height,
    sha256: crypto.createHash("sha256").update(bytes).digest("hex"),
    anchors: component.anchors ?? {},
  };
}

export async function initV2Catalog(outputPath) {
  const catalog = {
    schemaVersion: 2,
    coordinateSystem: { origin: "top_left", unit: "asset_local_normalized_0_to_1" },
    assets: [],
  };
  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  await fs.writeFile(outputPath, `${JSON.stringify(catalog, null, 2)}\n`, "utf8");
  return { catalog, report: { initialized: true, migratedFinalAssets: 0, skippedSourceAssets: 0, pendingDecisions: [] } };
}

export async function migrateEquipmentCatalog({ inputPath, outputPath, decisionsPath = null, strict = false }) {
  if (!inputPath) return initV2Catalog(outputPath);
  const input = JSON.parse(await fs.readFile(inputPath, "utf8"));
  if (input.schemaVersion !== 1) throw new Error("MISSING_CATALOG_SCHEMA_V1");
  if (!Array.isArray(input.assets)) throw new Error("MISSING_CATALOG_ASSETS");

  const decisions = decisionsPath ? JSON.parse(await fs.readFile(decisionsPath, "utf8")) : {};
  const catalogDirectory = path.dirname(path.resolve(inputPath));
  const outputDirectory = path.dirname(path.resolve(outputPath));
  if (catalogDirectory !== outputDirectory) {
    throw new Error("OUTPUT_MUST_SHARE_CATALOG_DIRECTORY: existing final assets are registered in place; no image copy is performed");
  }
  const finalDirectory = path.resolve(catalogDirectory, "final");
  const assets = [];
  const skippedSourceAssets = [];
  const pendingDecisions = [];

  for (const legacyAsset of input.assets) {
    const component = componentFromV1(legacyAsset);
    if (!component) {
      skippedSourceAssets.push({ id: legacyAsset.id, file: normalizeFile(legacyAsset.file), reason: "non_final_reference_not_imported" });
      continue;
    }
    const decision = decisionFor(decisions, legacyAsset.id);
    const migrated = await migrateComponent({ component, catalogDirectory, finalDirectory, label: legacyAsset.id });
    const asset = {
      id: legacyAsset.id,
      type: legacyAsset.type,
      viewId: legacyAsset.viewId,
      ...migrated,
      status: decision.status ?? legacyAsset.status ?? null,
      renderClass: decision.renderClass ?? legacyAsset.renderClass ?? null,
    };
    if (decision.anchors) asset.anchors = decision.anchors;
    if (legacyAsset.frontOccluder) {
      const frontComponent = componentFromV1(legacyAsset, "frontOccluder");
      if (!frontComponent) throw new Error(`SOURCE_ASSET_FORBIDDEN: ${legacyAsset.id}.frontOccluder`);
      asset.frontOccluder = await migrateComponent({
        component: frontComponent,
        catalogDirectory,
        finalDirectory,
        label: `${legacyAsset.id}.frontOccluder`,
      });
    }
    if (asset.status === null) pendingDecisions.push({ id: asset.id, field: "status" });
    if (asset.renderClass === null) pendingDecisions.push({ id: asset.id, field: "renderClass" });
    assets.push(asset);
  }

  if (strict && pendingDecisions.length > 0) {
    throw new Error(`MISSING_MIGRATION_DECISIONS: ${JSON.stringify(pendingDecisions)}`);
  }
  const catalog = {
    schemaVersion: 2,
    coordinateSystem: { origin: "top_left", unit: "asset_local_normalized_0_to_1" },
    assets,
  };
  await fs.mkdir(outputDirectory, { recursive: true });
  await fs.writeFile(outputPath, `${JSON.stringify(catalog, null, 2)}\n`, "utf8");
  return {
    catalog,
    report: {
      initialized: false,
      migratedFinalAssets: assets.length,
      skippedSourceAssets,
      pendingDecisions,
      copiedImages: 0,
      registeredExistingFinalImages: assets.length,
    },
  };
}

function parseArguments(argv) {
  const options = { inputPath: null, outputPath: null, decisionsPath: null, strict: false, init: false };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === "--init") options.init = true;
    else if (argument === "--input") options.inputPath = path.resolve(argv[++index]);
    else if (argument === "--output") options.outputPath = path.resolve(argv[++index]);
    else if (argument === "--decisions") options.decisionsPath = path.resolve(argv[++index]);
    else if (argument === "--strict") options.strict = true;
    else throw new Error(`Unexpected argument: ${argument}`);
  }
  const equipmentDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
  options.inputPath ??= options.init ? null : path.join(equipmentDirectory, "equipment-catalog(2).json");
  options.outputPath ??= path.join(equipmentDirectory, "equipment-catalog.json");
  return options;
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  const result = await migrateEquipmentCatalog(options);
  console.log(JSON.stringify(result.report, null, 2));
}

const normalizedUrl = new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");
if (path.resolve(decodeURIComponent(normalizedUrl)) === path.resolve(process.argv[1] ?? "")) {
  main().catch((error) => {
    console.error(error?.stack ?? String(error));
    process.exitCode = 1;
  });
}
