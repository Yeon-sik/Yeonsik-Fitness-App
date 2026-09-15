import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import {
  ExerciseImageExportError,
  syncExerciseImages,
} from "../sync-exercise-images.mjs";

async function temporaryRoot() {
  return fs.mkdtemp(path.join(os.tmpdir(), "fitness-image-sync-"));
}

function silentLogger() {
  return { log() {}, warn() {} };
}

async function writeManifest(exportDirectory, fileName = "images/example-a.png") {
  await fs.mkdir(path.join(exportDirectory, "images"), { recursive: true });
  await fs.writeFile(path.join(exportDirectory, fileName), "test-png", "utf8");
  await fs.writeFile(
    path.join(exportDirectory, "manifest.json"),
    `${JSON.stringify({
      contractType: "fitness-exercise-image-export.v1",
      schemaVersion: 1,
      exercises: {
        example_exercise: {
          slug: "example-exercise",
          lookupKeys: ["example-alias"],
          preferredHeightDp: 280,
          frames: [
            { id: "A", file: fileName, durationMs: 1000 },
            { id: "B", file: fileName, durationMs: 1000 },
          ],
        },
      },
    }, null, 2)}\n`,
    "utf8",
  );
}

test("syncs a valid export into generated exercise images", async (t) => {
  const rootDirectory = await temporaryRoot();
  t.after(() => fs.rm(rootDirectory, { recursive: true, force: true }));
  const exportDirectory = path.join(rootDirectory, "Fitness-Image", "export");
  const outputDirectory = path.join(rootDirectory, "generated", "exercise-images");
  await writeManifest(exportDirectory);

  const result = await syncExerciseImages({ rootDirectory, logger: silentLogger() });

  assert.equal(result.status, "synced");
  assert.equal(result.exerciseCount, 1);
  assert.equal(result.copiedFiles, 2);
  assert.equal(await fs.readFile(path.join(outputDirectory, "images", "example-a.png"), "utf8"), "test-png");
  assert.match(await fs.readFile(path.join(outputDirectory, "manifest.json"), "utf8"), /example_exercise/);
});

test("clears stale generated output when the pipeline is missing", async (t) => {
  const rootDirectory = await temporaryRoot();
  t.after(() => fs.rm(rootDirectory, { recursive: true, force: true }));
  const outputDirectory = path.join(rootDirectory, "generated", "exercise-images");
  await fs.mkdir(outputDirectory, { recursive: true });
  await fs.writeFile(path.join(outputDirectory, "stale.txt"), "stale", "utf8");

  const result = await syncExerciseImages({ rootDirectory, logger: silentLogger() });

  assert.equal(result.status, "missing");
  assert.equal(await fs.access(outputDirectory).then(() => false, () => true), true);
});

test("does not publish a partially referenced export", async (t) => {
  const rootDirectory = await temporaryRoot();
  t.after(() => fs.rm(rootDirectory, { recursive: true, force: true }));
  const exportDirectory = path.join(rootDirectory, "Fitness-Image", "export");
  const outputDirectory = path.join(rootDirectory, "generated", "exercise-images");
  await writeManifest(exportDirectory, "images/missing-a.png");
  await fs.rm(path.join(exportDirectory, "images", "missing-a.png"));
  await fs.mkdir(outputDirectory, { recursive: true });
  await fs.writeFile(path.join(outputDirectory, "stale.txt"), "stale", "utf8");

  const result = await syncExerciseImages({ rootDirectory, logger: silentLogger() });

  assert.equal(result.status, "incomplete");
  assert.deepEqual(result.missingFiles, ["images/missing-a.png"]);
  assert.equal(await fs.access(outputDirectory).then(() => false, () => true), true);
});

async function writeManifestDocument(directory, manifest, files) {
  await fs.mkdir(path.join(directory, "images"), { recursive: true });
  for (const [file, content] of Object.entries(files)) {
    await fs.writeFile(path.join(directory, file), content, "utf8");
  }
  await fs.writeFile(
    path.join(directory, "manifest.json"),
    JSON.stringify(manifest, null, 2) + "\n",
    "utf8",
  );
}
test("rejects a structurally invalid export manifest", async (t) => {
  const rootDirectory = await temporaryRoot();
  t.after(() => fs.rm(rootDirectory, { recursive: true, force: true }));
  const exportDirectory = path.join(rootDirectory, "Fitness-Image", "export");
  await fs.mkdir(exportDirectory, { recursive: true });
  await fs.writeFile(path.join(exportDirectory, "manifest.json"), "{\"schemaVersion\": 99}\n", "utf8");

  await assert.rejects(
    syncExerciseImages({ rootDirectory, logger: silentLogger() }),
    (error) => error instanceof ExerciseImageExportError && error.code === "INVALID_MANIFEST",
  );
});

test("uses export images for healthy exercises and fallback only for affected exercises", async (t) => {
  const rootDirectory = await temporaryRoot();
  t.after(() => fs.rm(rootDirectory, { recursive: true, force: true }));
  const exportDirectory = path.join(rootDirectory, "Fitness-Image", "export");
  const fallbackDirectory = path.join(rootDirectory, "app", "src", "main", "assets-fallback", "exercise-images");
  const outputDirectory = path.join(rootDirectory, "generated", "exercise-images");
  const makeEntry = (slug) => ({
    slug,
    preferredHeightDp: 280,
    frames: [
      { id: "A", file: "images/" + slug + "-a.png", durationMs: 1000 },
      { id: "B", file: "images/" + slug + "-b.png", durationMs: 1000 },
    ],
  });
  const fallbackManifest = {
    contractType: "fitness-exercise-image-export.v1",
    schemaVersion: 1,
    exercises: {
      healthy_exercise: makeEntry("healthy-exercise"),
      missing_exercise: makeEntry("missing-exercise"),
    },
  };
  const exportManifest = {
    contractType: "fitness-exercise-image-export.v1",
    schemaVersion: 1,
    exercises: {
      healthy_exercise: makeEntry("healthy-exercise"),
      missing_exercise: makeEntry("missing-exercise"),
    },
  };
  await writeManifestDocument(fallbackDirectory, fallbackManifest, {
    "images/healthy-exercise-a.png": "fallback-healthy-a",
    "images/healthy-exercise-b.png": "fallback-healthy-b",
    "images/missing-exercise-a.png": "fallback-missing-a",
    "images/missing-exercise-b.png": "fallback-missing-b",
  });
  await writeManifestDocument(exportDirectory, exportManifest, {
    "images/healthy-exercise-a.png": "export-healthy-a",
    "images/healthy-exercise-b.png": "export-healthy-b",
  });

  const result = await syncExerciseImages({
    rootDirectory,
    fallbackDirectory,
    logger: silentLogger(),
  });

  assert.equal(result.status, "incomplete");
  assert.equal(result.exerciseCount, 2);
  assert.equal(result.copiedFiles, 4);
  assert.deepEqual(result.missingFiles, [
    "images/missing-exercise-a.png",
    "images/missing-exercise-b.png",
  ]);
  assert.equal(
    await fs.readFile(path.join(outputDirectory, "images", "healthy-exercise-a.png"), "utf8"),
    "export-healthy-a",
  );
  assert.equal(
    await fs.readFile(path.join(outputDirectory, "images", "missing-exercise-a.png"), "utf8"),
    "fallback-missing-a",
  );
  const syncedManifest = JSON.parse(await fs.readFile(path.join(outputDirectory, "manifest.json"), "utf8"));
  assert.deepEqual(Object.keys(syncedManifest.exercises).sort(), [
    "healthy_exercise",
    "missing_exercise",
  ]);
});
