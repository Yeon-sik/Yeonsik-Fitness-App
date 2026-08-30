#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDirectory, "../../..");
const equipmentDirectory = path.join(root, "model_image", "equipment");
const finalDirectory = path.join(equipmentDirectory, "final");
const catalogPath = path.join(equipmentDirectory, "equipment-catalog.json");
const bookmarkPath = path.join(equipmentDirectory, "equipment-catalog(2).json");
const decisionPackPath = path.join(root, "model_image", "archetypes", "DECISION_PACK.json");

const RENDER_CLASS_BY_TYPE = new Map([
  ["functional_trainer", "cable_machine"],
  ["cable_attachment_set", "canonical_attachment"],
  ["single_d_handle", "canonical_attachment"],
  ["rope_attachment", "canonical_attachment"],
  ["straight_bar", "canonical_attachment"],
  ["wide_lat_bar", "canonical_attachment"],
  ["ankle_cuff", "canonical_attachment"],
  ["ab_wheel", "movable_free_weight"],
  ["hand_gripper", "movable_free_weight"],
  ["resistance_band", "movable_free_weight"],
  ["weight_plate", "movable_free_weight"],
  ["ez_curl_bar", "movable_free_weight"],
  ["landmine_base", "fixed_support"],
  ["smith_machine", "fixed_machine"],
  ["assisted_pullup_dip_machine", "fixed_machine"],
  ["captains_chair", "fixed_support"],
  ["ghd_nordic_support", "fixed_support"],
  ["hip_abduction_adduction_machine", "fixed_machine"],
  ["leg_extension", "fixed_machine"],
  ["leg_press", "fixed_machine"],
  ["lying_leg_curl", "fixed_machine"],
  ["pec_deck", "fixed_machine"],
  ["plate_loaded_press_frame", "fixed_machine"],
  ["roman_chair", "fixed_support"],
  ["seated_calf_raise_machine", "fixed_machine"],
  ["seated_leg_curl", "fixed_machine"],
  ["seated_row_machine", "fixed_machine"],
  ["standing_calf_raise_machine", "fixed_machine"],
]);

function readJson(file) {
  return fs.readFile(file, "utf8").then((value) => JSON.parse(value));
}

function readPngDimensions(bytes, label) {
  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  if (bytes.length < 24 || !bytes.subarray(0, 8).equals(signature) || bytes.subarray(12, 16).toString("ascii") !== "IHDR") {
    throw new Error(`INVALID_PNG: ${label}`);
  }
  return { width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20) };
}

function finalFileFor(id) {
  return `final/${id.replace(/_v1$/, "")}.png`;
}

function renderClassFor(type) {
  const renderClass = RENDER_CLASS_BY_TYPE.get(type);
  if (!renderClass) throw new Error(`MISSING_RENDER_CLASS_RULE: ${type}`);
  return renderClass;
}

const [catalog, bookmark, decisionPack] = await Promise.all([
  readJson(catalogPath),
  readJson(bookmarkPath),
  readJson(decisionPackPath),
]);

const approvedRows = decisionPack.equipmentDecisions.filter((row) => row.status === "NOT_YET_PROMOTED");
const bookmarkById = new Map(bookmark.assets.map((asset) => [asset.id, asset]));
const existingById = new Map(catalog.assets.map((asset) => [asset.id, asset]));
const promoted = [];

await fs.mkdir(finalDirectory, { recursive: true });

for (const row of approvedRows) {
  const source = bookmarkById.get(row.assetId);
  if (!source) throw new Error(`MISSING_BOOKMARK_ASSET: ${row.assetId}`);
  if (existingById.has(source.id)) throw new Error(`DUPLICATE_CANONICAL_ASSET: ${source.id}`);
  if (!source.file.startsWith("source/")) throw new Error(`SOURCE_ASSET_EXPECTED: ${source.id}:${source.file}`);

  const sourcePath = path.join(equipmentDirectory, source.file);
  const targetFile = finalFileFor(source.id);
  const targetPath = path.join(equipmentDirectory, targetFile.replaceAll("/", path.sep));
  const bytes = await fs.readFile(sourcePath);
  const dimensions = readPngDimensions(bytes, sourcePath);
  await fs.copyFile(sourcePath, targetPath);

  const asset = {
    id: source.id,
    type: source.type,
    viewId: source.viewId,
    file: targetFile,
    width: dimensions.width,
    height: dimensions.height,
    sha256: crypto.createHash("sha256").update(bytes).digest("hex"),
    anchors: source.anchors ?? {},
    status: "approved",
    renderClass: renderClassFor(source.type),
  };
  promoted.push(asset);
}

const output = {
  schemaVersion: 2,
  coordinateSystem: catalog.coordinateSystem,
  assets: [...catalog.assets, ...promoted],
};
await fs.writeFile(catalogPath, `${JSON.stringify(output, null, 2)}\n`, "utf8");
console.log(JSON.stringify({ promoted: promoted.length, ids: promoted.map((asset) => asset.id) }, null, 2));
