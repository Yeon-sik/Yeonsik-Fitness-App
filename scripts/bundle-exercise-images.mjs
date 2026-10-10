import fs from "node:fs/promises";
import path from "node:path";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";
import { validateExerciseImageExport } from "./sync-exercise-images.mjs";

const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const resourceName = (slug, frame) => {
  const base = slug.replace(/[^A-Za-z0-9]+/g, "_").toLowerCase();
  return `${base.startsWith("exercise_") ? base : "exercise_" + base}_${frame.toLowerCase()}.png`;
};

/** Persist only the deployment PNGs produced by the normal Gradle image task. */
export async function bundleExerciseImages({ inputDirectory, resourcesDirectory, outputDirectory }) {
  const manifestBytes = await fs.readFile(path.join(inputDirectory, "manifest.json"));
  const manifest = JSON.parse(manifestBytes);
  const validation = await validateExerciseImageExport(manifest, inputDirectory);
  if (validation.missingFiles.length || !validation.entries.length) {
    throw new Error("Cannot bundle an empty or incomplete exercise image export.");
  }
  // Validate the entire bundle before changing any previously bundled files.
  const files = [];
  for (const entry of validation.entries) {
    for (const frame of entry.frames) {
      const bytes = await fs.readFile(path.join(resourcesDirectory, resourceName(entry.slug, frame.id)));
      if (bytes.length < 33 || !bytes.subarray(0, 8).equals(Buffer.from("89504e470d0a1a0a", "hex"))
        || bytes.toString("ascii", 12, 16) !== "IHDR"
        || bytes.readUInt32BE(16) < 1 || bytes.readUInt32BE(20) < 1
        || Math.max(bytes.readUInt32BE(16), bytes.readUInt32BE(20)) > 768
        || bytes[25] !== 3) {
        throw new Error(`Expected a Gradle-generated indexed PNG at most 768px: ${entry.exerciseId}:${frame.id}`);
      }
      const source = await fs.readFile(path.join(inputDirectory, frame.file));
      files.push({ exerciseId: entry.exerciseId, frameId: frame.id, file: frame.file,
        sourceSha256: sha256(source), deploymentSha256: sha256(bytes), bytes });
    }
  }
  let previousEntries = [];
  try {
    const previous = JSON.parse(await fs.readFile(path.join(outputDirectory, "manifest.json"), "utf8"));
    // This also bounds every previously referenced path before removing obsolete files.
    previousEntries = (await validateExerciseImageExport(previous, outputDirectory)).entries;
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
  }
  await fs.mkdir(outputDirectory, { recursive: true });
  for (const file of files) {
    const destination = path.join(outputDirectory, file.file);
    await fs.mkdir(path.dirname(destination), { recursive: true });
    await fs.writeFile(destination, file.bytes);
  }
  const receipt = {
    schema: "fitness-exercise-image-deployment.v1",
    sourceManifestSha256: sha256(manifestBytes), maxDimension: 768, paletteSize: 256,
    exerciseCount: validation.entries.length, frameCount: files.length,
    frames: files.map(({ bytes, ...identity }) => identity),
  };
  await fs.writeFile(path.join(outputDirectory, "deployment.json"), JSON.stringify(receipt, null, 2) + "\n");
  await fs.writeFile(path.join(outputDirectory, "manifest.json"), manifestBytes);
  const currentFiles = new Set(files.map((file) => file.file));
  for (const entry of previousEntries) {
    for (const frame of entry.frames) {
      if (!currentFiles.has(frame.file)) await fs.rm(path.join(outputDirectory, frame.file), { force: true });
    }
  }
  return { exerciseCount: receipt.exerciseCount, frameCount: receipt.frameCount };
}

if (path.resolve(process.argv[1] ?? "") === fileURLToPath(import.meta.url)) {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
  bundleExerciseImages({
    inputDirectory: path.join(root, "generated/exercise-images"),
    resourcesDirectory: path.join(root, "app/build/generated/exercise-illustration/res/drawable-nodpi"),
    outputDirectory: path.join(root, "app/src/main/assets-fallback/exercise-images"),
  }).then((result) => console.log("[exercise-images] bundled " + JSON.stringify(result)))
    .catch((error) => { console.error(error); process.exitCode = 1; });
}
