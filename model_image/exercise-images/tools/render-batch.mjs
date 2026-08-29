#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";
import {
  ERROR_CODES,
  isDirectRun,
  PipelineError,
  printPipelineError,
  writeJson,
} from "../../lib/pipeline-contract.mjs";
import { compileExercise, defaultCompilePaths, loadCompileContext } from "./compile-exercise.mjs";

export async function compileBatch({ exerciseNames, context, outputDirectory }) {
  const items = [];
  for (const exerciseName of exerciseNames) {
    try {
      const result = await compileExercise({ exerciseName, context, outputDirectory: null });
      items.push({
        exerciseName,
        exerciseId: result.scene.exerciseId,
        slug: result.scene.slug,
        illustrationKey: result.scene.imageIdentity.illustrationKey ?? result.scene.slug,
        imageIdentitySource: result.scene.imageIdentity.source,
        status: "COMPILED",
        directory: result.outputDirectory,
      });
    } catch (error) {
      items.push({
        exerciseName,
        status: "BLOCKED",
        code: error instanceof PipelineError ? error.code : ERROR_CODES.INVALID_CONTRACT,
        message: error.message,
      });
    }
  }
  const manifest = {
    contractType: "exercise-image-batch.v1",
    implementation: "scaffold",
    generationApi: null,
    status: items.every((item) => item.status === "COMPILED") ? "COMPILED" : "BLOCKED",
    zipStatus: "NOT_CREATED",
    zipPolicy: "ZIP is created only after every item has validated <illustrationKey>-a.png and <illustrationKey>-b.png outputs",
    items,
  };
  await writeJson(path.join(outputDirectory, "batch-manifest.json"), manifest);
  return manifest;
}

async function main() {
  const toolsDirectory = path.dirname(fileURLToPath(import.meta.url));
  const repositoryRoot = path.resolve(toolsDirectory, "..", "..", "..");
  const defaults = defaultCompilePaths(repositoryRoot);
  const listPath = process.argv[2] ? path.resolve(process.argv[2]) : null;
  if (!listPath) throw new Error("Usage: node render-batch.mjs <exercise-names.txt> [--equipment-catalog <path>] [--out <directory>]");
  const paths = { ...defaults };
  let outputDirectory = path.join(defaults.outputRoot, "batch");
  for (let index = 3; index < process.argv.length; index += 1) {
    const argument = process.argv[index];
    if (argument === "--equipment-catalog") paths.equipmentCatalog = path.resolve(process.argv[++index]);
    else if (argument === "--deterministic-mapping") paths.deterministicMapping = path.resolve(process.argv[++index]);
    else if (argument === "--family-mapping") paths.familyMapping = path.resolve(process.argv[++index]);
    else if (argument === "--image-identity") paths.imageIdentity = path.resolve(process.argv[++index]);
    else if (argument === "--final-directory") paths.finalDirectory = path.resolve(process.argv[++index]);
    else if (argument === "--out") outputDirectory = path.resolve(process.argv[++index]);
    else throw new Error(`Unexpected argument: ${argument}`);
  }
  const exerciseNames = (await fs.readFile(listPath, "utf8"))
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"));
  const context = await loadCompileContext(paths);
  const report = await compileBatch({ exerciseNames, context, outputDirectory });
  console.log(JSON.stringify(report, null, 2));
  if (report.status !== "COMPILED") process.exitCode = 2;
}

if (isDirectRun(import.meta.url)) {
  main().catch((error) => {
    printPipelineError(error);
    process.exitCode = error instanceof PipelineError ? 2 : 1;
  });
}
