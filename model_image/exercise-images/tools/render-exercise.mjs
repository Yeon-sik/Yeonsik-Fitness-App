#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { fileURLToPath, pathToFileURL } from "node:url";
import {
  ERROR_CODES,
  fail,
  isDirectRun,
  PipelineError,
  printPipelineError,
  writeJson,
} from "../../lib/pipeline-contract.mjs";
import { compileExercise, defaultCompilePaths, loadCompileContext } from "./compile-exercise.mjs";

export async function renderExercise({ exerciseName, context, outputDirectory, adapterPath = null }) {
  const compiled = await compileExercise({ exerciseName, context, outputDirectory });
  const scene = compiled.scene;
  const run = {
    contractType: "exercise-image-render-run.v1",
    exerciseId: scene.exerciseId,
    slug: scene.slug,
    status: "BLOCKED",
    currentStep: "generate_A_mannequin",
    adapter: adapterPath,
    scene: "scene.json",
    outputs: scene.frames.map((frame) => frame.file),
    steps: [
      { id: "compile", status: "complete" },
      { id: "generate_A_mannequin", status: "pending" },
      { id: "composite_A", status: "pending" },
      { id: "validate_A", status: "pending" },
      { id: "edit_A_into_B", status: "pending", input: "approved_A" },
      { id: "composite_B", status: "pending", equipmentIdentity: "same_as_A" },
      { id: "validate_B", status: "pending" },
      { id: "validate_filenames_and_alpha", status: "pending" },
    ],
  };

  if (!adapterPath) {
    run.blocker = {
      code: ERROR_CODES.MISSING_GENERATION_ADAPTER,
      message: "No image generation adapter is configured; no pixels were fabricated",
    };
    await writeJson(path.join(compiled.outputDirectory, "render-run.json"), run);
    fail(ERROR_CODES.MISSING_GENERATION_ADAPTER, run.blocker.message, {
      exerciseId: scene.exerciseId,
      renderRun: path.join(compiled.outputDirectory, "render-run.json"),
    });
  }

  const adapter = await import(pathToFileURL(path.resolve(adapterPath)).href);
  for (const method of ["generateA", "editBFromApprovedA"]) {
    if (typeof adapter[method] !== "function") {
      fail(ERROR_CODES.INVALID_CONTRACT, `Generation adapter does not export ${method}()`);
    }
  }
  run.status = "ADAPTER_READY";
  run.blocker = {
    code: "RENDER_EXECUTION_NOT_ENABLED",
    message: "Adapter execution is intentionally gated until compositor and output validators are explicitly configured",
  };
  await writeJson(path.join(compiled.outputDirectory, "render-run.json"), run);
  return { scene, run, outputDirectory: compiled.outputDirectory };
}

function parseArguments(argv, defaults) {
  const paths = { ...defaults };
  let exerciseName = null;
  let outputDirectory = null;
  let adapterPath = null;
  const flags = {
    "--exercise-catalog": "exerciseCatalog", "--name-index": "nameIndex",
    "--overrides": "overrides", "--archetypes": "archetypes",
    "--equipment-catalog": "equipmentCatalog", "--muscle-layers": "muscleLayers",
  };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (flags[argument]) paths[flags[argument]] = path.resolve(argv[++index]);
    else if (argument === "--out") outputDirectory = path.resolve(argv[++index]);
    else if (argument === "--adapter") adapterPath = path.resolve(argv[++index]);
    else if (!exerciseName) exerciseName = argument;
    else throw new Error(`Unexpected argument: ${argument}`);
  }
  if (!exerciseName) throw new Error("Usage: node render-exercise.mjs <exercise-name> [--adapter <module>] [--out <directory>]");
  return { paths, exerciseName, outputDirectory, adapterPath };
}

async function main() {
  const toolsDirectory = path.dirname(fileURLToPath(import.meta.url));
  const repositoryRoot = path.resolve(toolsDirectory, "..", "..", "..");
  const args = parseArguments(process.argv.slice(2), defaultCompilePaths(repositoryRoot));
  const context = await loadCompileContext(args.paths);
  const result = await renderExercise({
    exerciseName: args.exerciseName,
    context,
    outputDirectory: args.outputDirectory,
    adapterPath: args.adapterPath,
  });
  console.log(JSON.stringify({ ok: true, status: result.run.status, outputDirectory: result.outputDirectory }, null, 2));
}

if (isDirectRun(import.meta.url)) {
  main().catch((error) => {
    printPipelineError(error);
    process.exitCode = error instanceof PipelineError ? 2 : 1;
  });
}
