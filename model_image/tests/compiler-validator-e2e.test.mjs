import assert from "node:assert/strict";
import crypto from "node:crypto";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { createRequire } from "node:module";
import test from "node:test";

import { compileExercise } from "../exercise-images/tools/compile-exercise.mjs";
import { validateAssetManifests } from "../equipment/tools/validate-asset-manifests.mjs";
import { normalizeExerciseName, writeJson } from "../lib/pipeline-contract.mjs";

const require = createRequire(import.meta.url);
const sharpModulePath = process.env.SHARP_MODULE_PATH ?? (() => {
  try { return path.dirname(require.resolve("sharp/package.json")); } catch { return undefined; }
})();
const sharp = sharpModulePath ? require(sharpModulePath) : null;

test("compiler output is accepted by the actual asset-manifest validator", { skip: !sharp }, async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "fitness-compiler-validator-e2e-"));
  try {
    const catalogDirectory = path.join(root, "catalog");
    const equipmentPath = path.join(catalogDirectory, "equipment-catalog.json");
    const equipmentFile = path.join(catalogDirectory, "final", "dumbbell.png");
    const finalDirectory = path.join(root, "final");
    const outputRoot = path.join(root, "generated");
    const referencePath = path.join(root, "reference.scene.json");
    const registryPath = path.join(root, "archetype-registry.json");
    const templateA = path.join(root, "prompt-a.md");
    const templateB = path.join(root, "prompt-b.md");

    const equipmentBytes = await createVisibleAlphaPng(8, 8, { left: 2, top: 2, width: 4, height: 4 });
    await fs.mkdir(path.dirname(equipmentFile), { recursive: true });
    await fs.writeFile(equipmentFile, equipmentBytes);
    const equipmentSha = crypto.createHash("sha256").update(equipmentBytes).digest("hex");
    await writeJson(equipmentPath, {
      schemaVersion: 2,
      coordinateSystem: { origin: "top_left", unit: "asset_local_normalized_0_to_1" },
      assets: [{
        id: "dumbbell_v1",
        type: "dumbbell",
        viewId: "front_3q",
        file: "final/dumbbell.png",
        width: 8,
        height: 8,
        sha256: equipmentSha,
        anchors: { grip_center: [0.5, 0.5] },
        status: "approved",
        renderClass: "movable_free_weight",
      }],
    });

    const frameBytes = await createVisibleAlphaPng(64, 64, { left: 8, top: 8, width: 48, height: 48 });
    await fs.mkdir(finalDirectory, { recursive: true });
    await fs.writeFile(path.join(finalDirectory, "arms-test-curl-a.png"), frameBytes);
    await fs.writeFile(path.join(finalDirectory, "arms-test-curl-b.png"), frameBytes);
    await writeJson(referencePath, { schemaVersion: 1, frames: [] });
    await fs.writeFile(templateA, "A {{invisibleGripTargetsAJson}}", "utf8");
    await fs.writeFile(templateB, "B {{invisibleGripTargetsBJson}}", "utf8");

    const placementA = {
      instanceId: "dumbbell#0",
      equipmentId: "dumbbell_v1",
      viewId: "front_3q",
      anchor: "grip_center",
      target: [20, 24],
      scale: 1,
      rotationDegrees: 0,
      z: 10,
      locked: true,
    };
    const placementB = { ...placementA, target: [20.5, 24] };
    const context = {
      exerciseCatalog: { exercises: [{
        id: "arms_test_curl",
        nameKo: "테스트 컬",
        slug: "arms-test-curl",
        movementPattern: "curl",
        motionType: "curl_motion",
        supportMode: null,
        bodyOrientation: null,
        equipmentKinematics: "free_weight",
        laterality: "bilateral",
        gripVariant: null,
        canonicalView: null,
        primarySubPart: "biceps",
        secondarySubParts: ["forearms"],
        equipmentType: "dumbbell",
        archetypeId: "test",
      }] },
      nameIndex: { aliases: { [normalizeExerciseName("테스트 컬")]: ["arms_test_curl"] } },
      overrides: { exercises: {} },
      deterministicMapping: { rules: [] },
      archetypes: { archetypes: {
        test: {
          camera: { viewId: "front_3q" },
          metadata: {
            supportMode: "standing",
            bodyOrientation: "upright",
            equipmentKinematics: "free_weight",
            gripVariant: "supinated",
          },
          poses: { A: { elbows: "extended" }, B: { elbows: "flexed" } },
          lockedJoints: ["shoulders"],
          animatedJoints: ["elbows", "wrists"],
          renderClass: "movable_free_weight",
          canonicalReferenceScene: "reference.scene.json",
          equipmentAnchorStrategy: { type: "invisible_grip" },
          canvas: { width: 64, height: 64 },
          placementRecipe: { A: [placementA], B: [placementB] },
          lockedAnchors: { pelvis: [10, 10] },
          anchorTolerancePixels: 2,
          lockedEquipment: ["dumbbell#0"],
        },
      } },
      equipmentCatalog: JSON.parse(await fs.readFile(equipmentPath, "utf8")),
      muscleLayers: { exerciseGroups: { biceps: ["biceps_long"], forearms: ["forearm_flexors"] } },
      paths: {
        archetypes: registryPath,
        templateA,
        templateB,
        outputRoot,
        finalDirectory,
      },
    };

    const compiled = await compileExercise({ exerciseName: "테스트 컬", context });
    const scenePath = path.join(compiled.outputDirectory, "scene.json");
    const validation = await validateAssetManifests({
      sharpModulePath,
      catalogPath: equipmentPath,
      scenePaths: [scenePath],
    });

    assert.equal(validation.valid, true);
    assert.equal(validation.frames, 2);
    assert.equal(validation.placements, 2);
    assert.equal(compiled.scene.frames[0].file, "../../final/arms-test-curl-a.png");
    assert.deepEqual(compiled.scene.frames[0].invisibleGripTargets[0].target, [20, 24]);
    assert.deepEqual(compiled.scene.frames[1].invisibleGripTargets[0].target, [20.5, 24]);
    assert.match(await fs.readFile(path.join(compiled.outputDirectory, "prompt-a.md"), "utf8"), /20,24/);
    assert.match(await fs.readFile(path.join(compiled.outputDirectory, "prompt-b-edit.md"), "utf8"), /20\.5,24/);
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

async function createVisibleAlphaPng(width, height, rectangle) {
  return sharp({
    create: {
      width,
      height,
      channels: 4,
      background: { r: 0, g: 0, b: 0, alpha: 0 },
    },
  }).composite([{
    input: {
      create: {
        width: rectangle.width,
        height: rectangle.height,
        channels: 4,
        background: { r: 255, g: 255, b: 255, alpha: 1 },
      },
    },
    left: rectangle.left,
    top: rectangle.top,
  }]).png().toBuffer();
}

