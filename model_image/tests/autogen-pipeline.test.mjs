import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { buildExerciseCatalog } from "../data/build-exercise-catalog.mjs";
import { compileExercise, inspectExerciseReadiness } from "../exercise-images/tools/compile-exercise.mjs";
import { normalizeExerciseName, writeJson } from "../lib/pipeline-contract.mjs";

test("catalog builder preserves unreviewed rendering metadata as null", async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "fitness-image-catalog-"));
  const fitnessPath = path.join(root, "Fitness_Weight.json");
  const overridesPath = path.join(root, "exercise-overrides.json");
  const outputDirectory = path.join(root, "data");
  await writeJson(fitnessPath, {
    exercises: [{
      id: "arms_test_curl",
      nameKo: "테스트 컬",
      nameEn: "Test Curl",
      movementPattern: "curl",
      motionType: "curl_motion",
      laterality: "bilateral",
      primarySubPart: "biceps",
      secondarySubParts: ["forearms"],
      equipment: "dumbbell",
      resistanceType: "free_weight",
    }],
  });
  await writeJson(overridesPath, { schemaVersion: 1, exercises: {} });

  const { catalog, index } = await buildExerciseCatalog({ fitnessPath, overridesPath, outputDirectory });
  const exercise = catalog.exercises[0];
  assert.equal(exercise.supportMode, null);
  assert.equal(exercise.bodyOrientation, null);
  assert.equal(exercise.gripVariant, null);
  assert.equal(exercise.canonicalView, null);
  assert.equal(exercise.equipmentKinematics, "free_weight");
  assert.deepEqual(exercise.missingReviewedFields, []);
  assert.equal(exercise.overridePolicy, "optional_exception");
  assert.deepEqual(index.aliases[normalizeExerciseName("테스트 컬")], ["arms_test_curl"]);
});

test("readiness resolves nullable metadata from the selected archetype", async () => {
  const context = baseContext();
  context.exerciseCatalog.exercises[0].supportMode = null;
  context.exerciseCatalog.exercises[0].bodyOrientation = null;
  context.exerciseCatalog.exercises[0].gripVariant = null;
  context.exerciseCatalog.exercises[0].canonicalView = null;
  const result = await inspectExerciseReadiness("arms_test_curl", context);
  assert.equal(result.ready, true);
  assert.equal(result.metadataResolution.sources.supportMode, "archetype_default");
  assert.equal(result.metadataResolution.sources.bodyOrientation, "archetype_default");
  assert.equal(result.metadataResolution.sources.gripVariant, "archetype_default");
  assert.equal(result.metadataResolution.sources.canonicalView, "archetype_camera");
});

test("readiness reports missing metadata only after archetype resolution", async () => {
  const context = baseContext();
  context.exerciseCatalog.exercises[0].supportMode = null;
  context.exerciseCatalog.exercises[0].bodyOrientation = null;
  context.exerciseCatalog.exercises[0].gripVariant = null;
  context.exerciseCatalog.exercises[0].canonicalView = null;
  delete context.archetypes.archetypes.test.metadata;
  const result = await inspectExerciseReadiness("arms_test_curl", context);
  assert.equal(result.ready, false);
  assert.equal(result.code, "MISSING_EXERCISE_METADATA");
  assert.ok(result.details.fields.includes("supportMode"));
});

test("compiler emits A/B edit and identical canonical equipment contracts", async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "fitness-image-compile-"));
  const referenceScene = path.join(root, "reference.scene.json");
  const templateA = path.join(root, "a.md");
  const templateB = path.join(root, "b.md");
  await writeJson(referenceScene, { schemaVersion: 1, exerciseId: "reference", frames: [] });
  await fs.writeFile(templateA, "A {{exerciseId}} {{poseAJson}} {{invisibleGripTargetsAJson}}", "utf8");
  await fs.writeFile(templateB, "B from A {{exerciseId}} {{poseBJson}} {{invisibleGripTargetsBJson}}", "utf8");
  const context = baseContext();
  context.paths = {
    archetypes: path.join(root, "archetype-registry.json"),
    templateA,
    templateB,
    outputRoot: path.join(root, "generated"),
  };
  context.archetypes.archetypes.test.canonicalReferenceScene = "reference.scene.json";

  const result = await compileExercise({ exerciseName: "테스트 컬", context });
  assert.deepEqual(result.scene.frames.map((frame) => frame.id), ["A", "B"]);
  assert.equal(result.scene.generationContract.derivedFrames.B, "edit_from_A");
  assert.deepEqual(
    result.scene.frames.map((frame) => frame.equipmentPlacements[0].equipmentId),
    ["dumbbell_v1", "dumbbell_v1"],
  );
  assert.match(await fs.readFile(path.join(result.outputDirectory, "prompt-b-edit.md"), "utf8"), /B from A/);
  assert.match(await fs.readFile(path.join(result.outputDirectory, "prompt-a.md"), "utf8"), /"target":\[100,200\]/);
  assert.match(await fs.readFile(path.join(result.outputDirectory, "prompt-b-edit.md"), "utf8"), /"target":\[100,120\]/);
  assert.equal(result.scene.exerciseMetadata.canonicalView, undefined);
  assert.equal(result.scene.metadataResolution.canonicalViewSource, "archetype_camera");
});

test("compiler inherits the archetype placement recipe when no exercise override exists", async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "fitness-image-archetype-recipe-"));
  try {
    const referenceScene = path.join(root, "reference.scene.json");
    const templateA = path.join(root, "a.md");
    const templateB = path.join(root, "b.md");
    await writeJson(referenceScene, { schemaVersion: 1, frames: [] });
    await fs.writeFile(templateA, "A {{invisibleGripTargetsAJson}}", "utf8");
    await fs.writeFile(templateB, "B {{invisibleGripTargetsBJson}}", "utf8");
    const context = baseContext();
    Object.assign(context.exerciseCatalog.exercises[0], {
      supportMode: null,
      bodyOrientation: null,
      gripVariant: null,
      canonicalView: null,
    });
    context.overrides.exercises = {};
    context.paths = {
      archetypes: path.join(root, "archetype-registry.json"),
      templateA,
      templateB,
      outputRoot: path.join(root, "generated"),
    };
    context.archetypes.archetypes.test.canonicalReferenceScene = "reference.scene.json";
    context.archetypes.archetypes.test.placementRecipe = {
      A: [{
        equipmentId: "dumbbell_v1", viewId: "front_3q", anchor: "grip_center",
        target: [100, 200], scale: 1, rotationDegrees: 0, z: 10,
      }],
      B: [{
        equipmentId: "dumbbell_v1", viewId: "front_3q", anchor: "grip_center",
        target: [100, 120], scale: 1, rotationDegrees: 10, z: 10,
      }],
    };
    context.archetypes.archetypes.test.lockedAnchors = {};
    context.archetypes.archetypes.test.anchorTolerancePixels = 8;
    context.archetypes.archetypes.test.lockedEquipment = [];

    const result = await compileExercise({ exerciseName: "테스트 컬", context });
    assert.deepEqual(result.scene.frames.map((frame) => frame.equipmentPlacements.length), [1, 1]);
    assert.deepEqual(result.scene.frames.map((frame) => frame.equipmentPlacements[0].target), [[100, 200], [100, 120]]);
    assert.deepEqual(result.scene.frames.map((frame) => frame.file), [
      "../../final/arms-test-curl-a.png",
      "../../final/arms-test-curl-b.png",
    ]);
    assert.equal(result.scene.generationContract.invisibleGripTargets.A[0].target[0], 100);
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

function baseContext() {
  const placementA = {
    equipmentId: "dumbbell_v1",
    viewId: "front_3q",
    anchor: "grip_center",
    target: [100, 200],
    scale: 1,
    rotationDegrees: 0,
    z: 10,
  };
  const placementB = { ...placementA, target: [100, 120], rotationDegrees: 10 };
  return {
    exerciseCatalog: {
      exercises: [{
        id: "arms_test_curl",
        nameKo: "테스트 컬",
        slug: "arms-test-curl",
        movementPattern: "curl",
        motionType: "curl_motion",
        supportMode: "standing",
        bodyOrientation: "upright",
        equipmentKinematics: "free_weight",
        laterality: "bilateral",
        gripVariant: "supinated",
        canonicalView: "front_3q",
        primarySubPart: "biceps",
        secondarySubParts: ["forearms"],
        equipmentType: "dumbbell",
        archetypeId: "test",
      }],
    },
    nameIndex: { aliases: { [normalizeExerciseName("테스트 컬")]: ["arms_test_curl"] } },
    overrides: {
      exercises: {
        arms_test_curl: {
          archetypeId: "test",
          anchorTolerancePixels: 8,
          lockedAnchors: { elbow_left: [50, 50] },
          equipmentPlacements: { A: [placementA], B: [placementB] },
        },
      },
    },
    archetypes: {
      archetypes: {
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
          canonicalReferenceScene: "../exercise-images/scenes/dumbbell-curl.scene.json",
          equipmentAnchorStrategy: { type: "invisible_grip" },
          canvas: { width: 640, height: 640 },
        },
      },
    },
    equipmentCatalog: {
      assets: [{
        id: "dumbbell_v1",
        status: "approved",
        renderClass: "movable_free_weight",
        file: "final/dumbbell.png",
        viewId: "front_3q",
        anchors: { grip_center: [0.5, 0.5] },
      }],
    },
    muscleLayers: {
      exerciseGroups: {
        biceps: ["biceps_long", "biceps_short"],
        forearms: ["forearm_flexors"],
      },
    },
    paths: { archetypes: path.resolve("model_image/archetypes/archetype-registry.json") },
  };
}
