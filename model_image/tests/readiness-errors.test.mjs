import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { inspectExerciseReadiness } from "../exercise-images/tools/compile-exercise.mjs";
import { writeJson } from "../lib/pipeline-contract.mjs";

test("readiness does not require an exercise override", async () => {
  const context = await readyContext();
  context.overrides.exercises = {};
  const result = await inspectExerciseReadiness("test", context);
  assert.equal(result.ready, true);
  assert.equal(result.metadataResolution.sources.supportMode, "archetype_default");
  assert.equal(result.metadataResolution.sources.canonicalView, "archetype_camera");
  assert.equal(result.normalizedPlacements.A[0].instanceId, "equipment_v1#0");
  assert.deepEqual(result.normalizedPlacements.A[0].target, [50, 50]);
});

test("readiness fails with MISSING_ARCHETYPE", async () => {
  const context = await readyContext();
  context.archetypes.archetypes = {};
  assert.equal((await inspectExerciseReadiness("test", context)).code, "MISSING_ARCHETYPE");
});

test("readiness fails with MISSING_REFERENCE_SCENE", async () => {
  const context = await readyContext();
  context.archetypes.archetypes.test.canonicalReferenceScene = "missing.scene.json";
  assert.equal((await inspectExerciseReadiness("test", context)).code, "MISSING_REFERENCE_SCENE");
});

test("readiness rejects unapproved equipment", async () => {
  const context = await readyContext();
  context.equipmentCatalog.assets[0].status = "draft";
  assert.equal((await inspectExerciseReadiness("test", context)).code, "MISSING_EQUIPMENT");
});

test("readiness rejects a mismatched equipment view", async () => {
  const context = await readyContext();
  context.overrides.exercises.test.equipmentPlacements.B[0].viewId = "side";
  assert.equal((await inspectExerciseReadiness("test", context)).code, "MISSING_EQUIPMENT_VIEW");
});

async function readyContext() {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "fitness-readiness-"));
  const reference = path.join(root, "reference.scene.json");
  await writeJson(reference, { schemaVersion: 1 });
  const placement = {
    equipmentId: "equipment_v1",
    viewId: "front",
    anchor: "center",
    target: [50, 50],
    scale: 1,
    rotationDegrees: 0,
    z: -1,
  };
  return {
    exerciseCatalog: { exercises: [{
      id: "test",
      nameKo: "테스트",
      slug: "test",
      movementPattern: "test",
      motionType: "test",
      supportMode: "standing",
      bodyOrientation: "upright",
      equipmentKinematics: "free_weight",
      laterality: "bilateral",
      gripVariant: "neutral",
      canonicalView: "front",
      primarySubPart: "primary",
      secondarySubParts: [],
      equipmentType: "dumbbell",
      archetypeId: "test",
    }] },
    overrides: { exercises: { test: {
      archetypeId: "test",
      equipmentPlacements: { A: [{ ...placement }], B: [{ ...placement }] },
    } } },
    archetypes: { archetypes: { test: {
      camera: { viewId: "front" },
      poses: { A: { pose: "a" }, B: { pose: "b" } },
      lockedJoints: [],
      animatedJoints: ["elbow"],
      renderClass: "movable_free_weight",
      canonicalReferenceScene: "reference.scene.json",
      equipmentAnchorStrategy: { type: "invisible_grip" },
      canvas: { width: 100, height: 100 },
      metadata: {
        supportMode: "standing",
        bodyOrientation: "upright",
        equipmentKinematics: "free_weight",
        gripVariant: "neutral",
      },
      placementRecipe: { A: [{ ...placement }], B: [{ ...placement }] },
      lockedAnchors: {},
      anchorTolerancePixels: 8,
      lockedEquipment: [],
    } } },
    equipmentCatalog: { assets: [{
      id: "equipment_v1",
      status: "approved",
      renderClass: "movable_free_weight",
      file: "final/equipment.png",
      viewId: "front",
      anchors: { center: [0.5, 0.5] },
    }] },
    muscleLayers: { exerciseGroups: { primary: ["muscle"] } },
    paths: { archetypes: path.join(root, "archetype-registry.json") },
  };
}
