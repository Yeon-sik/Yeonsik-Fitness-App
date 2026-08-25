import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const [sharpModulePath, catalogPath, ...scenePaths] = process.argv.slice(2);

if (!sharpModulePath || !catalogPath) {
  throw new Error(
    "Usage: node validate-asset-manifests.mjs <sharp-module> <catalog-json> [scene-json ...]",
  );
}

const sharp = require(sharpModulePath);
const catalog = JSON.parse(await fs.readFile(catalogPath, "utf8"));
const equipmentIds = new Set();

function assertPoint(name, point, width, height, normalized) {
  if (!Array.isArray(point) || point.length !== 2 || !point.every(Number.isFinite)) {
    throw new Error(`Invalid point ${name}: ${JSON.stringify(point)}`);
  }
  const maximumX = normalized ? 1 : width;
  const maximumY = normalized ? 1 : height;
  if (point[0] < 0 || point[0] > maximumX || point[1] < 0 || point[1] > maximumY) {
    throw new Error(`Point outside canvas ${name}: ${JSON.stringify(point)}`);
  }
}

for (const asset of catalog.assets) {
  if (equipmentIds.has(asset.id)) throw new Error(`Duplicate equipment ID: ${asset.id}`);
  equipmentIds.add(asset.id);
  const imagePath = path.resolve(path.dirname(catalogPath), asset.file);
  const metadata = await sharp(imagePath).metadata();
  if (metadata.width !== asset.canvas.width || metadata.height !== asset.canvas.height) {
    throw new Error(`Canvas mismatch for ${asset.id}: ${metadata.width}x${metadata.height}`);
  }
  for (const [anchorName, point] of Object.entries(asset.anchors)) {
    assertPoint(`${asset.id}.${anchorName}`, point, 1, 1, true);
  }
}

let frameCount = 0;
for (const scenePath of scenePaths) {
  const scene = JSON.parse(await fs.readFile(scenePath, "utf8"));
  for (const equipmentId of scene.equipment ?? []) {
    if (!equipmentIds.has(equipmentId)) {
      throw new Error(`${scene.exerciseId} references unknown equipment: ${equipmentId}`);
    }
  }
  for (const [anchorName, point] of Object.entries(scene.lockedAnchors ?? {})) {
    assertPoint(anchorName, point, scene.canvas.width, scene.canvas.height, false);
  }
  for (const frame of scene.frames) {
    const imagePath = path.resolve(path.dirname(scenePath), frame.file);
    const metadata = await sharp(imagePath).metadata();
    if (metadata.width !== scene.canvas.width || metadata.height !== scene.canvas.height) {
      throw new Error(`Frame canvas mismatch: ${frame.file}`);
    }
    for (const [jointName, point] of Object.entries(frame.joints ?? {})) {
      assertPoint(jointName, point, scene.canvas.width, scene.canvas.height, false);
    }
    if (frame.barbell?.center) {
      assertPoint(
        `${frame.id}.barbell.center`,
        frame.barbell.center,
        scene.canvas.width,
        scene.canvas.height,
        false,
      );
    }
    for (const [equipmentName, transform] of Object.entries(frame.movingEquipment ?? {})) {
      if (transform.center) {
        assertPoint(
          `${frame.id}.${equipmentName}.center`,
          transform.center,
          scene.canvas.width,
          scene.canvas.height,
          false,
        );
      }
    }
    frameCount += 1;
  }
}

console.log(JSON.stringify({
  valid: true,
  equipmentAssets: equipmentIds.size,
  scenes: scenePaths.length,
  frames: frameCount,
}));
