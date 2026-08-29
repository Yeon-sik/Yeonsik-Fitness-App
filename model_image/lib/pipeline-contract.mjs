import fs from "node:fs/promises";
import path from "node:path";

export const ERROR_CODES = Object.freeze({
  MISSING_EXERCISE_METADATA: "MISSING_EXERCISE_METADATA",
  MISSING_ARCHETYPE: "MISSING_ARCHETYPE",
  MISSING_EQUIPMENT: "MISSING_EQUIPMENT",
  MISSING_EQUIPMENT_VIEW: "MISSING_EQUIPMENT_VIEW",
  MISSING_REFERENCE_SCENE: "MISSING_REFERENCE_SCENE",
  MISSING_OVERRIDE: "MISSING_OVERRIDE",
  MISSING_MUSCLE_MAPPING: "MISSING_MUSCLE_MAPPING",
  MISSING_EQUIPMENT_CATALOG: "MISSING_EQUIPMENT_CATALOG",
  MISSING_DETERMINISTIC_MAPPING: "MISSING_DETERMINISTIC_MAPPING",
  MISSING_PLACEMENT_RECIPE: "MISSING_PLACEMENT_RECIPE",
  MISSING_PLACEMENT: "MISSING_PLACEMENT",
  MISSING_LOCKED_ANCHORS: "MISSING_LOCKED_ANCHORS",
  MISSING_ANCHOR_TOLERANCE: "MISSING_ANCHOR_TOLERANCE",
  MISSING_LOCKED_EQUIPMENT: "MISSING_LOCKED_EQUIPMENT",
  MISSING_GENERATION_ADAPTER: "MISSING_GENERATION_ADAPTER",
  INVALID_CONTRACT: "INVALID_CONTRACT",
});

export class PipelineError extends Error {
  constructor(code, message, details = {}) {
    super(message);
    this.name = "PipelineError";
    this.code = code;
    this.details = details;
  }
}

export function fail(code, message, details = {}) {
  throw new PipelineError(code, message, details);
}

export async function readJson(filePath, missingCode = ERROR_CODES.INVALID_CONTRACT) {
  let text;
  try {
    text = await fs.readFile(filePath, "utf8");
  } catch (error) {
    if (error?.code === "ENOENT") {
      fail(missingCode, `Required file is missing: ${filePath}`, { file: filePath });
    }
    throw error;
  }
  try {
    return JSON.parse(text);
  } catch (error) {
    fail(ERROR_CODES.INVALID_CONTRACT, `Invalid JSON: ${filePath}`, {
      file: filePath,
      cause: error.message,
    });
  }
}

export async function writeJson(filePath, value) {
  await fs.mkdir(path.dirname(filePath), { recursive: true });
  await fs.writeFile(filePath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

export async function pathExists(filePath) {
  try {
    await fs.access(filePath);
    return true;
  } catch {
    return false;
  }
}

export function normalizeExerciseName(value) {
  return String(value ?? "")
    .normalize("NFC")
    .trim()
    .toLocaleLowerCase("ko-KR")
    .replace(/[\s_-]+/g, " ");
}

export function slugFromExerciseId(exerciseId) {
  return String(exerciseId ?? "")
    .trim()
    .toLowerCase()
    .replace(/_/g, "-")
    .replace(/[^a-z0-9-]+/g, "-")
    .replace(/-{2,}/g, "-")
    .replace(/^-|-$/g, "");
}

export function missingFields(object, fields) {
  return fields.filter((field) => {
    const value = object?.[field];
    return value === null || value === undefined || value === "";
  });
}

export function assertPoint(point, label, { normalized = false } = {}) {
  if (!Array.isArray(point) || point.length !== 2 || !point.every(Number.isFinite)) {
    fail(ERROR_CODES.INVALID_CONTRACT, `${label} must be a finite [x, y] point`);
  }
  if (normalized && point.some((value) => value < 0 || value > 1)) {
    fail(ERROR_CODES.INVALID_CONTRACT, `${label} must use asset-local normalized coordinates`);
  }
}

export function isDirectRun(importMetaUrl) {
  if (!process.argv[1]) return false;
  const normalizedUrl = new URL(importMetaUrl).pathname.replace(/^\/([A-Za-z]:)/, "$1");
  return path.resolve(decodeURIComponent(normalizedUrl)) === path.resolve(process.argv[1]);
}

export function printPipelineError(error) {
  if (error instanceof PipelineError) {
    console.error(JSON.stringify({ ok: false, code: error.code, message: error.message, details: error.details }, null, 2));
    return;
  }
  console.error(error?.stack ?? String(error));
}
