import assert from "node:assert/strict";
import crypto from "node:crypto";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import test from "node:test";

const require = createRequire(import.meta.url);
const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const promoteScript = path.join(repositoryRoot, "model_image/equipment/tools/promote-equipment.mjs");
const composeScript = path.join(repositoryRoot, "model_image/exercise-images/tools/compose-approved-equipment.mjs");
const validatorScript = path.join(repositoryRoot, "model_image/equipment/tools/validate-asset-manifests.mjs");
const sharpModulePath = process.env.SHARP_MODULE_PATH ?? (() => {
  try { return path.dirname(require.resolve("sharp/package.json")); } catch { return undefined; }
})();
const sharp = sharpModulePath ? require(sharpModulePath) : null;

function run(script, args) {
  return spawnSync(process.execPath, [script, ...args], { encoding: "utf8" });
}

async function writeJson(file, value) {
  await fs.mkdir(path.dirname(file), { recursive: true });
  await fs.writeFile(file, `${JSON.stringify(value, null, 2)}\n`);
}

test("promotion trims native PNG, recalculates anchors, and records identity", { skip: !sharp }, async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "equipment-promote-"));
  try {
    const source = path.join(root, "source/example.png");
    const catalogPath = path.join(root, "equipment-catalog.json");
    const anchorsPath = path.join(root, "anchors.json");
    await fs.mkdir(path.dirname(source), { recursive: true });
    await sharp({ create: { width: 10, height: 10, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
      .composite([{ input: { create: { width: 4, height: 5, channels: 4, background: { r: 255, g: 0, b: 0, alpha: 1 } } }, left: 2, top: 3 }])
      .png().toFile(source);
    await writeJson(catalogPath, {
      schemaVersion: 2,
      coordinateSystem: { origin: "top_left", unit: "asset_local_normalized_0_to_1" },
      assets: [],
    });
    await writeJson(anchorsPath, { grip_center: [0.4, 0.55] });
    const result = run(promoteScript, [
      sharpModulePath, catalogPath, source,
      "--id", "example_v1", "--type", "example", "--view-id", "front",
      "--file", "final/example.png", "--anchors", anchorsPath,
      "--status", "approved", "--render-class", "movable_free_weight", "--trim",
    ]);
    assert.equal(result.status, 0, result.stderr);
    const catalog = JSON.parse(await fs.readFile(catalogPath, "utf8"));
    assert.deepEqual(catalog.assets[0].anchors.grip_center, [0.5, 0.5]);
    assert.equal(catalog.assets[0].width, 4);
    assert.equal(catalog.assets[0].height, 5);
    assert.match(catalog.assets[0].sha256, /^[a-f0-9]{64}$/);
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

test("compositor rotates around an asset-local anchor on the mannequin z plane", { skip: !sharp }, async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "equipment-compose-"));
  try {
    await fs.mkdir(path.join(root, "final"), { recursive: true });
    const assetPath = path.join(root, "final/red-bar.png");
    const mannequinPath = path.join(root, "mannequin.png");
    const outputPath = path.join(root, "output.png");
    const placementsPath = path.join(root, "placements.json");
    const catalogPath = path.join(root, "equipment-catalog.json");
    await sharp({ create: { width: 2, height: 4, channels: 4, background: { r: 255, g: 0, b: 0, alpha: 1 } } }).png().toFile(assetPath);
    await sharp({ create: { width: 20, height: 20, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
      .composite([{ input: { create: { width: 1, height: 1, channels: 4, background: { r: 0, g: 255, b: 0, alpha: 1 } } }, left: 7, top: 10 }])
      .png().toFile(mannequinPath);
    const bytes = await fs.readFile(assetPath);
    await writeJson(catalogPath, {
      schemaVersion: 2,
      coordinateSystem: { origin: "top_left", unit: "asset_local_normalized_0_to_1" },
      assets: [{
        id: "red_bar_v1", type: "bar", viewId: "front", file: "final/red-bar.png",
        width: 2, height: 4, sha256: crypto.createHash("sha256").update(bytes).digest("hex"),
        anchors: { pivot: [0, 0] }, status: "approved", renderClass: "movable_free_weight",
      }],
    });
    await writeJson(placementsPath, [{
      equipmentId: "red_bar_v1", anchor: "pivot", target: [10, 10],
      scale: 1, rotationDegrees: 90, z: 1,
    }]);
    const result = run(composeScript, [sharpModulePath, catalogPath, mannequinPath, outputPath, placementsPath]);
    assert.equal(result.status, 0, result.stderr);
    const metadata = await sharp(outputPath).metadata();
    assert.equal(metadata.width, 20);
    assert.equal(metadata.height, 20);
    const visible = await sharp(outputPath).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
    let left = visible.info.width;
    let right = -1;
    let top = visible.info.height;
    let bottom = -1;
    for (let y = 0; y < visible.info.height; y += 1) {
      for (let x = 0; x < visible.info.width; x += 1) {
        if (visible.data[(y * visible.info.width + x) * visible.info.channels + 3] > 0) {
          left = Math.min(left, x); right = Math.max(right, x);
          top = Math.min(top, y); bottom = Math.max(bottom, y);
        }
      }
    }
    assert.equal(right - left + 1, 4);
    assert.equal(bottom - top + 1, 2);
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

test("validator fails closed for source catalog references", { skip: !sharp }, async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "equipment-validate-"));
  try {
    const catalogPath = path.join(root, "equipment-catalog.json");
    await writeJson(catalogPath, {
      schemaVersion: 2,
      coordinateSystem: { origin: "top_left", unit: "asset_local_normalized_0_to_1" },
      assets: [{
        id: "bad_v1", type: "bad", viewId: "front", file: "source/bad.png",
        width: 1, height: 1, sha256: "0".repeat(64), anchors: { center: [0.5, 0.5] },
        status: "approved", renderClass: "fixed_support",
      }],
    });
    const result = run(validatorScript, [sharpModulePath, catalogPath]);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /SOURCE_ASSET_FORBIDDEN/);
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});
