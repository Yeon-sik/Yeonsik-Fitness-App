import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { bundleExerciseImages } from "../bundle-exercise-images.mjs";

async function fixture(t) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "fitness-image-bundle-"));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  const options = {
    inputDirectory: path.join(root, "input"), resourcesDirectory: path.join(root, "resources"),
    outputDirectory: path.join(root, "fallback"),
  };
  for (const directory of Object.values(options)) await fs.mkdir(directory, { recursive: true });
  await fs.mkdir(path.join(options.inputDirectory, "images"));
  const manifest = { contractType: "fitness-exercise-image-export.v1", schemaVersion: 1,
    exercises: { example: { slug: "example", lookupKeys: ["example"], preferredHeightDp: 280,
      frames: ["A", "B"].map((id) => ({ id, file: `images/example-${id.toLowerCase()}.png`, durationMs: 1000 })) } } };
  await fs.writeFile(path.join(options.inputDirectory, "manifest.json"), JSON.stringify(manifest));
  // Small PNG header fixture for the deployment-format guard; decoding is checked by Gradle.
  const png = Buffer.alloc(33);
  Buffer.from("89504e470d0a1a0a", "hex").copy(png);
  png.write("IHDR", 12); png.writeUInt32BE(1, 16); png.writeUInt32BE(1, 20); png[25] = 3;
  for (const side of ["a", "b"]) {
    await fs.writeFile(path.join(options.inputDirectory, `images/example-${side}.png`), "source-" + side);
    await fs.writeFile(path.join(options.resourcesDirectory, `exercise_example_${side}.png`), png);
  }
  return { ...options, png, manifest };
}

test("bundles deployment PNGs with exact IDs, slots, timing and source hashes", async (t) => {
  const f = await fixture(t);
  assert.deepEqual(await bundleExerciseImages(f), { exerciseCount: 1, frameCount: 2 });
  assert.deepEqual(JSON.parse(await fs.readFile(path.join(f.outputDirectory, "manifest.json"))), f.manifest);
  assert.deepEqual(await fs.readFile(path.join(f.outputDirectory, "images/example-a.png")), f.png);
  const receipt = JSON.parse(await fs.readFile(path.join(f.outputDirectory, "deployment.json")));
  assert.deepEqual(receipt.frames.map((frame) => [frame.exerciseId, frame.frameId]), [["example", "A"], ["example", "B"]]);
  assert.match(receipt.frames[0].sourceSha256, /^[a-f0-9]{64}$/);
  assert.notEqual(receipt.frames[0].sourceSha256, receipt.frames[0].deploymentSha256);
});

test("missing deployment frame preserves the previous bundle", async (t) => {
  const f = await fixture(t);
  const previous = "previous-manifest";
  await fs.writeFile(path.join(f.outputDirectory, "manifest.json"), previous);
  await fs.rm(path.join(f.resourcesDirectory, "exercise_example_b.png"));
  await assert.rejects(bundleExerciseImages(f), { code: "ENOENT" });
  assert.equal(await fs.readFile(path.join(f.outputDirectory, "manifest.json"), "utf8"), previous);
  await assert.rejects(fs.access(path.join(f.outputDirectory, "images/example-a.png")), { code: "ENOENT" });
});

test("rejects oversized or non-indexed deployment images before writing", async (t) => {
  const f = await fixture(t);
  f.png.writeUInt32BE(769, 16);
  await fs.writeFile(path.join(f.resourcesDirectory, "exercise_example_a.png"), f.png);
  await assert.rejects(bundleExerciseImages(f), /at most 768px/);
  f.png.writeUInt32BE(1, 16); f.png[25] = 6;
  await fs.writeFile(path.join(f.resourcesDirectory, "exercise_example_a.png"), f.png);
  await assert.rejects(bundleExerciseImages(f), /indexed PNG/);
  await assert.rejects(fs.access(path.join(f.outputDirectory, "manifest.json")), { code: "ENOENT" });
});

test("removes only obsolete manifest references and preserves unrelated files", async (t) => {
  const f = await fixture(t);
  await fs.mkdir(path.join(f.outputDirectory, "images"));
  const previous = structuredClone(f.manifest);
  previous.exercises.example.frames[0].file = "images/old-a.png";
  previous.exercises.example.frames[1].file = "images/old-b.png";
  await fs.writeFile(path.join(f.outputDirectory, "manifest.json"), JSON.stringify(previous));
  for (const name of ["old-a.png", "old-b.png", "unrelated.png"]) {
    await fs.writeFile(path.join(f.outputDirectory, "images", name), "preserve-unless-obsolete");
  }
  await bundleExerciseImages(f);
  await assert.rejects(fs.access(path.join(f.outputDirectory, "images/old-a.png")), { code: "ENOENT" });
  assert.equal(await fs.readFile(path.join(f.outputDirectory, "images/unrelated.png"), "utf8"), "preserve-unless-obsolete");
});
