#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const EXPORT_CONTRACT_TYPE = "fitness-exercise-image-export.v1";
const EXPORT_SCHEMA_VERSION = 1;
const FRAME_IDS = ["A", "B"];

export class ExerciseImageExportError extends Error {
  constructor(code, message, details = {}) {
    super(message);
    this.name = "ExerciseImageExportError";
    this.code = code;
    this.details = details;
  }
}

function fail(code, message, details = {}) {
  throw new ExerciseImageExportError(code, message, details);
}

async function isDirectory(filePath) {
  try {
    return (await fs.stat(filePath)).isDirectory();
  } catch {
    return false;
  }
}

async function isFile(filePath) {
  try {
    return (await fs.stat(filePath)).isFile();
  } catch {
    return false;
  }
}

function isWithin(rootPath, candidatePath) {
  const relative = path.relative(rootPath, candidatePath);
  return relative === "" || (relative !== ".." && !relative.startsWith(`..${path.sep}`) && !path.isAbsolute(relative));
}

function resolveRelativeExportFile(exportDirectory, value, label) {
  if (typeof value !== "string" || value.trim().length === 0) {
    fail("INVALID_MANIFEST", `${label} must be a non-empty relative path`);
  }
  const normalized = value.trim().replaceAll("\\", "/");
  if (path.posix.isAbsolute(normalized) || /^[A-Za-z]:\//.test(normalized)) {
    fail("INVALID_MANIFEST", `${label} must be relative: ${value}`);
  }
  const resolved = path.resolve(exportDirectory, normalized);
  if (!isWithin(path.resolve(exportDirectory), resolved)) {
    fail("INVALID_MANIFEST", `${label} escapes the export directory: ${value}`);
  }
  if (!normalized.startsWith("images/")) {
    fail("INVALID_MANIFEST", `${label} must remain under images/: ${value}`);
  }
  return { normalized, resolved };
}

function validateLookupKeys(entry, label) {
  if (entry.lookupKeys === undefined) return [];
  if (!Array.isArray(entry.lookupKeys)) {
    fail("INVALID_MANIFEST", `${label}.lookupKeys must be an array`);
  }
  const keys = [];
  for (const [index, key] of entry.lookupKeys.entries()) {
    if (typeof key !== "string" || key.trim().length === 0) {
      fail("INVALID_MANIFEST", `${label}.lookupKeys[${index}] must be a non-empty string`);
    }
    if (!keys.includes(key.trim())) keys.push(key.trim());
  }
  return keys;
}

export async function validateExerciseImageExport(manifest, exportDirectory) {
  if (!manifest || typeof manifest !== "object" || Array.isArray(manifest)) {
    fail("INVALID_MANIFEST", "Export manifest must be a JSON object");
  }
  if (manifest.contractType !== EXPORT_CONTRACT_TYPE) {
    fail("INVALID_MANIFEST", `Unsupported export contractType: ${manifest.contractType ?? "<missing>"}`);
  }
  if (manifest.schemaVersion !== EXPORT_SCHEMA_VERSION) {
    fail("INVALID_MANIFEST", `Unsupported export schemaVersion: ${manifest.schemaVersion ?? "<missing>"}`);
  }
  if (!manifest.exercises || typeof manifest.exercises !== "object" || Array.isArray(manifest.exercises)) {
    fail("INVALID_MANIFEST", "Export manifest exercises must be an object keyed by exerciseId");
  }

  const entries = [];
  const seenSlugs = new Set();
  const missingFiles = [];
  for (const [exerciseId, rawEntry] of Object.entries(manifest.exercises)) {
    const label = `exercises.${exerciseId}`;
    if (!exerciseId.trim() || !rawEntry || typeof rawEntry !== "object" || Array.isArray(rawEntry)) {
      fail("INVALID_MANIFEST", `${label} must be an object`);
    }
    const entry = rawEntry;
    if (entry.exerciseId !== undefined && entry.exerciseId !== exerciseId) {
      fail("INVALID_MANIFEST", `${label}.exerciseId must match its object key`);
    }
    if (typeof entry.slug !== "string" || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(entry.slug)) {
      fail("INVALID_MANIFEST", `${label}.slug must be kebab-case`);
    }
    if (seenSlugs.has(entry.slug)) {
      fail("INVALID_MANIFEST", `Duplicate export slug: ${entry.slug}`);
    }
    seenSlugs.add(entry.slug);
    if (!Array.isArray(entry.frames) || entry.frames.length !== FRAME_IDS.length) {
      fail("INVALID_MANIFEST", `${label}.frames must contain exactly A and B`);
    }
    const entryMissingFiles = [];
    const frames = [];
    for (const [index, rawFrame] of entry.frames.entries()) {
      const frameLabel = `${label}.frames[${index}]`;
      if (!rawFrame || typeof rawFrame !== "object" || Array.isArray(rawFrame)) {
        fail("INVALID_MANIFEST", `${frameLabel} must be an object`);
      }
      const expectedId = FRAME_IDS[index];
      if (rawFrame.id !== expectedId) {
        fail("INVALID_MANIFEST", `${label}.frames must be ordered A then B`);
      }
      if (!Number.isInteger(rawFrame.durationMs) || rawFrame.durationMs <= 0) {
        fail("INVALID_MANIFEST", `${frameLabel}.durationMs must be a positive integer`);
      }
      const resolvedFile = resolveRelativeExportFile(exportDirectory, rawFrame.file, `${frameLabel}.file`);
      if (!(await isFile(resolvedFile.resolved))) {
        missingFiles.push(resolvedFile.normalized);
        entryMissingFiles.push(resolvedFile.normalized);
      }
      frames.push({
        id: expectedId,
        file: resolvedFile.normalized,
        durationMs: rawFrame.durationMs,
      });
    }
    const preferredHeightDp = entry.preferredHeightDp === undefined ? 280 : entry.preferredHeightDp;
    if (!Number.isInteger(preferredHeightDp) || preferredHeightDp <= 0) {
      fail("INVALID_MANIFEST", `${label}.preferredHeightDp must be a positive integer`);
    }
    entries.push({
      exerciseId,
      slug: entry.slug,
      lookupKeys: validateLookupKeys(entry, label),
      preferredHeightDp,
      frames,
      missingFiles: entryMissingFiles,
    });
  }
  return { entries, missingFiles: [...new Set(missingFiles)] };
}

function toManifestEntry(entry) {
  return {
    exerciseId: entry.exerciseId,
    slug: entry.slug,
    lookupKeys: entry.lookupKeys,
    preferredHeightDp: entry.preferredHeightDp,
    frames: entry.frames,
  };
}

async function loadFallbackEntries(fallbackDirectory) {
  const manifestPath = path.join(fallbackDirectory, "manifest.json");
  if (!(await isFile(manifestPath))) return null;
  let manifest;
  try {
    manifest = JSON.parse(await fs.readFile(manifestPath, "utf8"));
  } catch (error) {
    fail("INVALID_FALLBACK_MANIFEST_JSON", "Unable to parse app fallback manifest: " + manifestPath, {
      cause: error.message,
    });
  }
  const validation = await validateExerciseImageExport(manifest, fallbackDirectory);
  if (validation.entries.length === 0 || validation.missingFiles.length > 0) {
    fail("INVALID_FALLBACK_MANIFEST", "App fallback manifest is incomplete: " + manifestPath, {
      missingFiles: validation.missingFiles,
    });
  }
  return validation.entries.map((entry) => ({
    ...entry,
    sourceDirectory: fallbackDirectory,
  }));
}

function mergeEntriesWithFallback(exportEntries, fallbackEntries = []) {
  const selected = new Map();
  const missingExerciseIds = new Set();
  for (const entry of exportEntries) {
    if ((entry.missingFiles ?? []).length > 0) {
      missingExerciseIds.add(entry.exerciseId);
      continue;
    }
    selected.set(entry.exerciseId, entry);
  }
  const usedSlugs = new Set([...selected.values()].map((entry) => entry.slug));
  for (const entry of fallbackEntries) {
    if ((!missingExerciseIds.has(entry.exerciseId) && selected.has(entry.exerciseId))
      || usedSlugs.has(entry.slug)) {
      continue;
    }
    selected.set(entry.exerciseId, entry);
    usedSlugs.add(entry.slug);
  }
  return [...selected.values()].sort((left, right) => left.exerciseId.localeCompare(right.exerciseId));
}

async function clearOutput(outputDirectory) {
  await fs.rm(outputDirectory, { recursive: true, force: true });
}

function warning(logger, message) {
  (logger.warn ?? logger.log ?? console.warn)(`[exercise-images] WARNING ${message}`);
}

export async function syncExerciseImages({
  rootDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), ".."),
  pipelineDirectory = path.join(rootDirectory, "Fitness-Image-Pipeline"),
  exportDirectory = path.join(pipelineDirectory, "export"),
  outputDirectory = path.join(rootDirectory, "generated", "exercise-images"),
  fallbackDirectory = path.join(rootDirectory, "app", "src", "main", "assets-fallback", "exercise-images"),
  logger = console,
} = {}) {
  const pipelineExists = await isDirectory(pipelineDirectory);
  if (!pipelineExists) {
    await clearOutput(outputDirectory);
    warning(logger, `Image Pipeline directory is missing; using app fallback (${pipelineDirectory}).`);
    return { status: "missing", copiedFiles: 0, exerciseCount: 0, missingFiles: [] };
  }
  if (!(await isDirectory(exportDirectory))) {
    await clearOutput(outputDirectory);
    warning(logger, `Image Pipeline export directory is missing; using app fallback (${exportDirectory}).`);
    return { status: "missing", copiedFiles: 0, exerciseCount: 0, missingFiles: [] };
  }

  const manifestPath = path.join(exportDirectory, "manifest.json");
  if (!(await isFile(manifestPath))) {
    await clearOutput(outputDirectory);
    warning(logger, `Image export manifest is missing; using app fallback (${manifestPath}).`);
    return { status: "missing", copiedFiles: 0, exerciseCount: 0, missingFiles: [] };
  }

  let manifest;
  try {
    manifest = JSON.parse(await fs.readFile(manifestPath, "utf8"));
  } catch (error) {
    fail("INVALID_MANIFEST_JSON", `Unable to parse image export manifest: ${manifestPath}`, {
      cause: error.message,
    });
  }
  const validation = await validateExerciseImageExport(manifest, exportDirectory);
  const exportEntries = validation.entries.map((entry) => ({
    ...entry,
    sourceDirectory: exportDirectory,
  }));
  const fallbackEntries = await loadFallbackEntries(fallbackDirectory);
  const mergedEntries = mergeEntriesWithFallback(exportEntries, fallbackEntries ?? []);
  const usedFallbackIds = mergedEntries
    .filter((entry) => entry.sourceDirectory === fallbackDirectory)
    .map((entry) => entry.exerciseId);
  const incomplete = validation.missingFiles.length > 0
    || validation.entries.length === 0
    || (fallbackEntries !== null && mergedEntries.length !== validation.entries.length);

  if (mergedEntries.length === 0) {
    await clearOutput(outputDirectory);
    const reason = validation.entries.length === 0
      ? "the export contains no exercise images"
      : "referenced files are missing: " + validation.missingFiles.join(", ");
    warning(logger, reason + "; using app fallback.");
    return {
      status: "incomplete",
      copiedFiles: 0,
      exerciseCount: 0,
      missingFiles: validation.missingFiles,
    };
  }

  await clearOutput(outputDirectory);
  await fs.mkdir(outputDirectory, { recursive: true });
  let copiedFiles = 0;
  for (const entry of mergedEntries) {
    for (const frame of entry.frames) {
      const source = path.resolve(entry.sourceDirectory, frame.file);
      const destination = path.resolve(outputDirectory, frame.file);
      await fs.mkdir(path.dirname(destination), { recursive: true });
      await fs.copyFile(source, destination);
      copiedFiles += 1;
    }
  }
  const outputManifest = {
    ...manifest,
    exercises: Object.fromEntries(
      mergedEntries.map((entry) => [entry.exerciseId, toManifestEntry(entry)]),
    ),
  };
  await fs.writeFile(
    path.join(outputDirectory, "manifest.json"),
    JSON.stringify(outputManifest, null, 2) + "\n",
    "utf8",
  );
  if (incomplete) {
    const detail = usedFallbackIds.length > 0
      ? "using app fallback for: " + usedFallbackIds.join(", ")
      : "using app fallback where available";
    warning(logger, "Export is incomplete; " + detail + ".");
  } else {
    logger.log?.("[exercise-images] synced " + mergedEntries.length + " exercises and " + copiedFiles + " frames.");
  }
  return {
    status: incomplete ? "incomplete" : "synced",
    copiedFiles,
    exerciseCount: mergedEntries.length,
    missingFiles: validation.missingFiles,
  };
}
function parseArguments(argv, rootDirectory) {
  const options = { rootDirectory };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === "--pipeline-dir") options.pipelineDirectory = path.resolve(argv[++index]);
    else if (argument === "--export-dir") options.exportDirectory = path.resolve(argv[++index]);
    else if (argument === "--output-dir") options.outputDirectory = path.resolve(argv[++index]);
    else throw new Error(`Unexpected argument: ${argument}`);
  }
  return options;
}

async function main() {
  const rootDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
  const result = await syncExerciseImages(parseArguments(process.argv.slice(2), rootDirectory));
  if (result.status === "synced") return;
  process.exitCode = 0;
}

if (path.resolve(fileURLToPath(import.meta.url)) === path.resolve(process.argv[1] ?? "")) {
  main().catch((error) => {
    if (error instanceof ExerciseImageExportError) {
      console.error(JSON.stringify({ ok: false, code: error.code, message: error.message, details: error.details }, null, 2));
      process.exitCode = 2;
      return;
    }
    console.error(error?.stack ?? String(error));
    process.exitCode = 1;
  });
}
