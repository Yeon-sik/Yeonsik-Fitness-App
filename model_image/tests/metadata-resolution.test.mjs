import assert from "node:assert/strict";
import test from "node:test";

import {
  resolveEquipmentPlacements,
  resolveExerciseMetadata,
} from "../lib/metadata-resolution.mjs";

test("metadata precedence is override, archetype, deterministic mapping, then missing", () => {
  const exercise = {
    supportMode: "source-value-must-not-win",
    bodyOrientation: null,
    equipmentKinematics: "source-kinematics",
    gripVariant: null,
    canonicalView: "source-view-must-not-win",
  };
  const archetype = {
    camera: { viewId: "rear_three_quarter" },
    metadata: {
      bodyOrientation: "upright",
      equipmentKinematics: "free_weight",
      gripVariant: "neutral",
    },
  };
  const deterministic = {
    values: {
      supportMode: "standing",
      bodyOrientation: "mapped-body-orientation",
      gripVariant: "mapped-grip",
      canonicalView: "mapped-view",
    },
    conflicts: [],
  };
  const result = resolveExerciseMetadata({
    exercise,
    override: { supportMode: "seated" },
    archetype,
    deterministic,
  });

  assert.deepEqual(result.metadata, {
    supportMode: "seated",
    bodyOrientation: "upright",
    equipmentKinematics: "free_weight",
    gripVariant: "neutral",
    canonicalView: "rear_three_quarter",
  });
  assert.deepEqual(result.sources, {
    supportMode: "exercise_override",
    bodyOrientation: "archetype_default",
    equipmentKinematics: "archetype_default",
    gripVariant: "archetype_default",
    canonicalView: "archetype_camera",
  });
  assert.deepEqual(result.missingFields, []);
});

test("unresolved metadata remains MISSING instead of being inferred from the exercise name", () => {
  const result = resolveExerciseMetadata({
    exercise: { supportMode: null, bodyOrientation: null, equipmentKinematics: null, gripVariant: null, canonicalView: null },
    override: {},
    archetype: {},
    deterministic: { values: {} },
  });
  assert.deepEqual(result.missingFields, [
    "supportMode",
    "bodyOrientation",
    "equipmentKinematics",
    "gripVariant",
    "canonicalView",
  ]);
});

test("deterministic mapping supplies metadata after archetype defaults are absent", () => {
  const result = resolveExerciseMetadata({
    exercise: { supportMode: null, bodyOrientation: null, equipmentKinematics: null, gripVariant: null, canonicalView: null },
    override: {},
    archetype: {},
    deterministic: {
      values: {
        supportMode: "standing",
        bodyOrientation: "upright",
        equipmentKinematics: "free_weight",
        gripVariant: "neutral",
        canonicalView: "front",
      },
      conflicts: [],
    },
  });
  assert.deepEqual(result.missingFields, []);
  assert.equal(result.sources.supportMode, "deterministic_mapping");
  assert.equal(result.sources.canonicalView, "deterministic_mapping");
});

test("source-owned equipment kinematics remains available without a reviewed override", () => {
  const result = resolveExerciseMetadata({
    exercise: {
      supportMode: null,
      bodyOrientation: null,
      equipmentKinematics: "free_weight",
      gripVariant: null,
      canonicalView: null,
    },
    override: {},
    archetype: {},
    deterministic: { values: {} },
  });
  assert.equal(result.metadata.equipmentKinematics, "free_weight");
  assert.equal(result.sources.equipmentKinematics, "source_catalog");
  assert.ok(result.missingFields.includes("supportMode"));
  assert.ok(!result.missingFields.includes("equipmentKinematics"));
});

test("archetype placement recipes support sparse exercise exceptions", () => {
  const base = {
    instanceId: "dumbbell#0",
    equipmentId: "dumbbell_v1",
    viewId: "front_3q",
    anchor: "grip_center",
    target: [100, 200],
    scale: 1,
    rotationDegrees: 0,
    z: 10,
  };
  const result = resolveEquipmentPlacements({
    exercise: { equipmentType: "dumbbell" },
    archetype: { placementRecipe: { A: [base], B: [{ ...base, target: [100, 120] }] } },
    override: { equipmentPlacements: { B: [{ instanceId: "dumbbell#0", rotationDegrees: 10 }] } },
  });

  assert.equal(result.missingRecipe, false);
  assert.deepEqual(result.frames.A, [base]);
  assert.deepEqual(result.frames.B, [{ ...base, target: [100, 120], rotationDegrees: 10 }]);
});
