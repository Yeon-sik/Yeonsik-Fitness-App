#!/usr/bin/env node

import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";
import {
  ERROR_CODES,
  isDirectRun,
  normalizeExerciseName,
  printPipelineError,
  readJson,
  slugFromExerciseId,
  writeJson,
} from "../lib/pipeline-contract.mjs";

const REQUIRED_SOURCE_FIELDS = [
  "id",
  "nameKo",
  "movementPattern",
  "motionType",
  "laterality",
  "primarySubPart",
  "secondarySubParts",
  "equipment",
  "resistanceType",
];

const REVIEWED_RENDER_FIELDS = [
  "supportMode",
  "bodyOrientation",
  "gripVariant",
  "canonicalView",
  "archetypeId",
];

export async function buildExerciseCatalog({ fitnessPath, overridesPath, outputDirectory }) {
  const fitness = await readJson(fitnessPath, ERROR_CODES.MISSING_EXERCISE_METADATA);
  const overridesDocument = await readJson(overridesPath, ERROR_CODES.MISSING_OVERRIDE);
  if (!Array.isArray(fitness.exercises)) {
    throw new Error(`${fitnessPath} does not contain an exercises array`);
  }
  const overrides = overridesDocument.exercises ?? {};
  const ids = new Set();
  const catalogExercises = [];
  const aliases = new Map();

  for (const source of fitness.exercises) {
    const missingSourceFields = REQUIRED_SOURCE_FIELDS.filter((field) => {
      const value = source[field];
      return value === null || value === undefined || value === "";
    });
    if (missingSourceFields.length > 0) {
      throw new Error(`${source.id ?? "<missing-id>"} is missing Fitness_Weight fields: ${missingSourceFields.join(", ")}`);
    }
    if (ids.has(source.id)) throw new Error(`Duplicate Fitness_Weight exercise id: ${source.id}`);
    ids.add(source.id);

    const override = overrides[source.id] ?? null;
    const record = {
      id: source.id,
      nameKo: source.nameKo,
      slug: override?.slug ?? slugFromExerciseId(source.id),
      movementPattern: source.movementPattern,
      motionType: source.motionType,
      supportMode: override?.supportMode ?? null,
      bodyOrientation: override?.bodyOrientation ?? null,
      equipmentKinematics: override?.equipmentKinematics ?? source.resistanceType,
      laterality: source.laterality,
      gripVariant: override?.gripVariant ?? null,
      canonicalView: override?.canonicalView ?? null,
      primarySubPart: source.primarySubPart,
      secondarySubParts: [...source.secondarySubParts],
      equipmentType: override?.equipmentType ?? source.equipment,
      archetypeId: override?.archetypeId ?? null,
      overrideId: override ? source.id : null,
      source: {
        file: path.relative(outputDirectory, fitnessPath).replaceAll("\\", "/"),
        nameEn: source.nameEn ?? null,
        resistanceType: source.resistanceType,
        uiPart: source.uiPart ?? null,
        notes: source.notes ?? null,
      },
      missingReviewedFields: REVIEWED_RENDER_FIELDS.filter((field) => {
        const value = field === "archetypeId" ? override?.archetypeId : override?.[field];
        return value === null || value === undefined || value === "";
      }),
    };
    catalogExercises.push(record);

    const candidateAliases = [source.id, source.nameKo, source.nameEn, ...(override?.aliases ?? [])]
      .filter(Boolean);
    for (const alias of candidateAliases) {
      const normalized = normalizeExerciseName(alias);
      if (!normalized) continue;
      const bucket = aliases.get(normalized) ?? new Set();
      bucket.add(source.id);
      aliases.set(normalized, bucket);
    }
  }

  const catalog = {
    schemaVersion: 1,
    generatedFrom: path.relative(outputDirectory, fitnessPath).replaceAll("\\", "/"),
    generationPolicy: "copy_source_fields_and_preserve_unreviewed_values_as_null",
    exerciseCount: catalogExercises.length,
    exercises: catalogExercises,
  };
  const index = {
    schemaVersion: 1,
    normalization: "NFC + trim + Korean lowercase + whitespace/underscore/hyphen collapse",
    aliases: Object.fromEntries(
      [...aliases.entries()]
        .sort(([left], [right]) => left.localeCompare(right, "ko"))
        .map(([alias, exerciseIds]) => [alias, [...exerciseIds].sort()]),
    ),
  };

  await writeJson(path.join(outputDirectory, "exercise-catalog.json"), catalog);
  await writeJson(path.join(outputDirectory, "exercise-name-index.json"), index);
  return { catalog, index };
}

async function main() {
  const dataDirectory = path.dirname(fileURLToPath(import.meta.url));
  const modelImageDirectory = path.dirname(dataDirectory);
  const repositoryRoot = path.dirname(modelImageDirectory);
  const [fitnessArgument, overridesArgument, outputArgument] = process.argv.slice(2);
  const fitnessPath = path.resolve(fitnessArgument ?? path.join(repositoryRoot, "Fitness_Weight.json"));
  const overridesPath = path.resolve(overridesArgument ?? path.join(dataDirectory, "exercise-overrides.json"));
  const outputDirectory = path.resolve(outputArgument ?? dataDirectory);
  const result = await buildExerciseCatalog({ fitnessPath, overridesPath, outputDirectory });
  console.log(JSON.stringify({
    ok: true,
    exercises: result.catalog.exerciseCount,
    aliases: Object.keys(result.index.aliases).length,
    outputDirectory,
  }, null, 2));
}

if (isDirectRun(import.meta.url)) {
  main().catch((error) => {
    printPipelineError(error);
    process.exitCode = 1;
  });
}
