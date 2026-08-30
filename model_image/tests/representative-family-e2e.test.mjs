import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { createRequire } from "node:module";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { compileExercise } from "../exercise-images/tools/compile-exercise.mjs";
import { composeApprovedEquipment } from "../exercise-images/tools/compose-approved-equipment.mjs";
import { validateAssetManifests } from "../equipment/tools/validate-asset-manifests.mjs";
import { normalizeExerciseName, writeJson } from "../lib/pipeline-contract.mjs";

const require = createRequire(import.meta.url);
const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const modelImageRoot = path.join(repositoryRoot, "model_image");
const actualPaths = {
  exerciseCatalog: path.join(modelImageRoot, "data", "exercise-catalog.json"),
  familyMapping: path.join(modelImageRoot, "family", "data", "exercise-family-mapping-v1.json"),
  imageIdentity: path.join(modelImageRoot, "data", "exercise-image-identity-v1.json"),
  equipmentCatalog: path.join(modelImageRoot, "equipment", "equipment-catalog.json"),
  muscleLayers: path.join(modelImageRoot, "style-4", "muscle-layers.json"),
  templateA: path.join(modelImageRoot, "exercise-images", "templates", "mannequin-a.prompt.md"),
  templateB: path.join(modelImageRoot, "exercise-images", "templates", "mannequin-b-edit.prompt.md"),
  finalDirectory: path.join(modelImageRoot, "exercise-images", "final"),
};
const sharpModulePath = process.env.SHARP_MODULE_PATH ?? (() => {
  try { return path.dirname(require.resolve("sharp/package.json")); } catch { return undefined; }
})();
const sharp = sharpModulePath ? require(sharpModulePath) : null;

const REPRESENTATIVE_SPECS = [
  {
    key: "pull_up",
    exerciseId: "back_bodyweight_pull_up",
    archetypeId: "representative.pull_up",
    illustrationKey: "pull-up",
    renderClass: "bodyweight",
    canvas: { width: 1024, height: 1536 },
    equipment: [],
    equipmentViews: {},
    anchorStrategy: "none",
    placements: { A: [], B: [] },
    lockedEquipment: [],
  },
  {
    key: "curl",
    exerciseId: "arms_dumbbell_curl",
    archetypeId: "representative.curl",
    illustrationKey: "dumbbell-curl",
    renderClass: "movable_free_weight",
    canvas: { width: 1536, height: 1024 },
    equipment: ["dumbbell_adjustable_three_quarter_v1"],
    equipmentViews: { dumbbell: "elevated_front_three_quarter" },
    anchorStrategy: "invisible_grip",
    placements: {
      A: [{ instanceId: "dumbbell#0", equipmentId: "dumbbell_adjustable_three_quarter_v1", viewId: "elevated_front_three_quarter", anchor: "grip_center", target: [600, 560], scale: 0.2, rotationDegrees: 0, z: 10 }],
      B: [{ instanceId: "dumbbell#0", equipmentId: "dumbbell_adjustable_three_quarter_v1", viewId: "elevated_front_three_quarter", anchor: "grip_center", target: [600, 300], scale: 0.2, rotationDegrees: 0, z: 10 }],
    },
    lockedEquipment: [],
  },
  {
    key: "squat",
    exerciseId: "legs_barbell_back_squat",
    archetypeId: "representative.squat",
    illustrationKey: "barbell-back-squat",
    renderClass: "movable_free_weight",
    canvas: { width: 1536, height: 1024 },
    equipment: ["barbell_loaded_three_quarter_v1"],
    equipmentViews: { barbell: "slight_rising_three_quarter" },
    anchorStrategy: "invisible_grip",
    placements: {
      A: [{ instanceId: "barbell#0", equipmentId: "barbell_loaded_three_quarter_v1", viewId: "slight_rising_three_quarter", anchor: "center", target: [760, 360], scale: 0.25, rotationDegrees: 0, z: 10 }],
      B: [{ instanceId: "barbell#0", equipmentId: "barbell_loaded_three_quarter_v1", viewId: "slight_rising_three_quarter", anchor: "center", target: [760, 280], scale: 0.25, rotationDegrees: 0, z: 10 }],
    },
    lockedEquipment: [],
  },
  {
    key: "bench_press",
    exerciseId: "chest_barbell_flat_bench_press",
    archetypeId: "representative.bench_press",
    illustrationKey: "barbell-flat-bench-press",
    renderClass: "movable_free_weight",
    canvas: { width: 1536, height: 1024 },
    equipment: ["flat_bench_three_quarter_v1", "barbell_loaded_three_quarter_v1"],
    equipmentViews: {
      flat_bench: "elevated_front_three_quarter",
      barbell: "slight_rising_three_quarter",
    },
    anchorStrategy: "none",
    placements: {
      A: [
        { instanceId: "flat_bench#0", equipmentId: "flat_bench_three_quarter_v1", viewId: "elevated_front_three_quarter", anchor: "pad_pelvis", target: [760, 560], scale: 0.35, rotationDegrees: 0, z: -10, locked: true },
        { instanceId: "barbell#0", equipmentId: "barbell_loaded_three_quarter_v1", viewId: "slight_rising_three_quarter", anchor: "center", target: [760, 350], scale: 0.25, rotationDegrees: 0, z: 10 },
      ],
      B: [
        { instanceId: "flat_bench#0", equipmentId: "flat_bench_three_quarter_v1", viewId: "elevated_front_three_quarter", anchor: "pad_pelvis", target: [760, 560], scale: 0.35, rotationDegrees: 0, z: -10, locked: true },
        { instanceId: "barbell#0", equipmentId: "barbell_loaded_three_quarter_v1", viewId: "slight_rising_three_quarter", anchor: "center", target: [760, 250], scale: 0.25, rotationDegrees: 0, z: 10 },
      ],
    },
    lockedEquipment: ["flat_bench#0"],
  },
];

test("representative family compiler resolves identity, view, A/B, and composition contracts", async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "fitness-representative-contract-"));
  try {
    for (const spec of REPRESENTATIVE_SPECS) {
      const context = await representativeContext(root, spec);
      const exercise = context.exerciseCatalog.exercises[0];
      const result = await compileExercise({
        exerciseName: exercise.nameKo,
        context,
        outputDirectory: path.join(root, "generated", spec.key),
      });
      assert.equal(result.scene.imageIdentity.familyId, expectedFamilyId(spec));
      assert.equal(result.scene.imageIdentity.source, "exact_visual_variant");
      assert.equal(result.scene.imageIdentity.illustrationKey, spec.illustrationKey);
      assert.deepEqual(result.scene.frames.map((frame) => path.basename(frame.file)), [
        `${spec.illustrationKey}-a.png`,
        `${spec.illustrationKey}-b.png`,
      ]);
      assert.deepEqual(result.scene.frames.map((frame) => frame.equipmentPlacements.length), [
        spec.placements.A.length,
        spec.placements.B.length,
      ]);
      assert.equal(result.scene.generationContract.derivedFrames.B, "edit_from_A");
      assert.equal(result.scene.generationContract.mannequin.proportions, "preserve_from_A");
      assert.equal(result.scene.generationContract.equipmentComposition.frameB, "reuse_A_equipment_identity");
      assert.deepEqual(result.scene.equipmentViews, Object.fromEntries(
        spec.equipment.map((equipmentId) => [equipmentId, context.equipmentCatalog.assets.find((asset) => asset.id === equipmentId).viewId]),
      ));
      if (spec.anchorStrategy === "invisible_grip") {
        assert.equal(result.scene.frames[0].invisibleGripTargets.length, spec.placements.A.length);
        assert.equal(result.scene.frames[1].invisibleGripTargets.length, spec.placements.B.length);
      }
    }
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

test("representative compiler output passes compositor and actual validator", { skip: !sharp }, async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "fitness-representative-raster-e2e-"));
  try {
    const catalog = JSON.parse(await fs.readFile(actualPaths.equipmentCatalog, "utf8"));
    const usedEquipmentIds = new Set(REPRESENTATIVE_SPECS.flatMap((spec) => spec.equipment));
    catalog.assets = catalog.assets.filter((asset) => usedEquipmentIds.has(asset.id));
    const catalogPath = path.join(root, "catalog", "equipment-catalog.json");
    await writeJson(catalogPath, catalog);
    for (const asset of catalog.assets) {
      await fs.mkdir(path.join(root, "catalog", "final"), { recursive: true });
      await fs.copyFile(
        path.join(modelImageRoot, "equipment", asset.file),
        path.join(root, "catalog", asset.file),
      );
    }

    const scenePaths = [];
    for (const spec of REPRESENTATIVE_SPECS) {
      const context = await representativeContext(root, spec, { catalogPath, catalog });
      const exercise = context.exerciseCatalog.exercises[0];
      const result = await compileExercise({
        exerciseName: exercise.nameKo,
        context,
        outputDirectory: path.join(root, "generated", spec.key),
      });
      const mannequinPath = path.join(root, "mannequin", `${spec.key}.png`);
      await writeSyntheticMannequin(mannequinPath, spec.canvas);
      const finalA = path.join(root, "final", `${spec.illustrationKey}-a.png`);
      const finalB = path.join(root, "final", `${spec.illustrationKey}-b.png`);
      if (spec.equipment.length === 0) {
        await fs.mkdir(path.dirname(finalA), { recursive: true });
        await fs.copyFile(mannequinPath, finalA);
        await fs.copyFile(mannequinPath, finalB);
      } else {
        await composeApprovedEquipment({
          sharpModulePath,
          catalogPath,
          mannequinPath,
          outputPath: finalA,
          placements: result.scene.frames[0].equipmentPlacements,
        });
        await composeApprovedEquipment({
          sharpModulePath,
          catalogPath,
          mannequinPath,
          outputPath: finalB,
          placements: result.scene.frames[1].equipmentPlacements,
        });
      }
      scenePaths.push(path.join(result.outputDirectory, "scene.json"));
    }

    const validation = await validateAssetManifests({ sharpModulePath, catalogPath, scenePaths });
    assert.equal(validation.valid, true);
    assert.equal(validation.scenes, REPRESENTATIVE_SPECS.length);
    assert.equal(validation.frames, REPRESENTATIVE_SPECS.length * 2);
    assert.equal(validation.placements, 8);
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

async function representativeContext(root, spec, { catalogPath = actualPaths.equipmentCatalog, catalog = null } = {}) {
  const exerciseCatalog = JSON.parse(await fs.readFile(actualPaths.exerciseCatalog, "utf8"));
  const exercise = exerciseCatalog.exercises.find((item) => item.id === spec.exerciseId);
  assert.ok(exercise, `missing representative exercise ${spec.exerciseId}`);
  const selectedExercise = { ...exercise, archetypeId: spec.archetypeId };
  const referencePath = path.join(root, "references", `${spec.key}.scene.json`);
  await writeJson(referencePath, { schemaVersion: 1, exerciseId: `${spec.key}.reference`, frames: [] });
  const actualCatalog = catalog ?? JSON.parse(await fs.readFile(actualPaths.equipmentCatalog, "utf8"));
  return {
    exerciseCatalog: { exercises: [selectedExercise] },
    nameIndex: { aliases: { [normalizeExerciseName(selectedExercise.nameKo)]: [selectedExercise.id] } },
    overrides: { exercises: {} },
    deterministicMapping: { rules: [] },
    familyMapping: JSON.parse(await fs.readFile(actualPaths.familyMapping, "utf8")),
    imageIdentity: JSON.parse(await fs.readFile(actualPaths.imageIdentity, "utf8")),
    archetypes: {
      archetypes: {
        [spec.archetypeId]: {
          camera: { viewId: "representative_camera" },
          metadata: {
            supportMode: "standing",
            bodyOrientation: "upright",
            equipmentKinematics: selectedExercise.equipmentKinematics,
            gripVariant: "neutral",
          },
          poses: { A: { phase: "start" }, B: { phase: "end" } },
          lockedJoints: ["head", "torso", "pelvis"],
          animatedJoints: ["arms", "legs"],
          renderClass: spec.renderClass,
          canonicalReferenceScene: path.relative(path.dirname(path.join(root, "archetypes.json")), referencePath).replaceAll("\\", "/"),
          equipmentAnchorStrategy: { type: spec.anchorStrategy },
          canvas: spec.canvas,
          equipmentViews: spec.equipmentViews,
          placementRecipe: spec.placements,
          lockedAnchors: { pelvis: [spec.canvas.width / 2, spec.canvas.height * 0.65] },
          anchorTolerancePixels: 2,
          lockedEquipment: spec.lockedEquipment,
        },
      },
    },
    equipmentCatalog: actualCatalog,
    muscleLayers: JSON.parse(await fs.readFile(actualPaths.muscleLayers, "utf8")),
    paths: {
      archetypes: path.join(root, "archetypes.json"),
      familyMapping: actualPaths.familyMapping,
      imageIdentity: actualPaths.imageIdentity,
      equipmentCatalog: catalogPath,
      templateA: actualPaths.templateA,
      templateB: actualPaths.templateB,
      outputRoot: path.join(root, "generated"),
      finalDirectory: path.join(root, "final"),
    },
  };
}

function expectedFamilyId(spec) {
  return {
    pull_up: "pull_up",
    curl: "biceps_curl",
    squat: "squat",
    bench_press: "bench_press",
  }[spec.key];
}

async function writeSyntheticMannequin(filePath, canvas) {
  await fs.mkdir(path.dirname(filePath), { recursive: true });
  await sharp({
    create: {
      width: canvas.width,
      height: canvas.height,
      channels: 4,
      background: { r: 0, g: 0, b: 0, alpha: 0 },
    },
  }).composite([{
    input: {
      create: {
        width: Math.max(2, Math.round(canvas.width * 0.2)),
        height: Math.max(2, Math.round(canvas.height * 0.4)),
        channels: 4,
        background: { r: 255, g: 255, b: 255, alpha: 1 },
      },
    },
    left: Math.round(canvas.width * 0.4),
    top: Math.round(canvas.height * 0.3),
  }]).png().toFile(filePath);
}
