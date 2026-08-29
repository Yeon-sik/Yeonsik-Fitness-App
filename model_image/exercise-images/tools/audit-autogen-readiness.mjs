#!/usr/bin/env node

import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";
import {
  isDirectRun,
  PipelineError,
  printPipelineError,
  writeJson,
} from "../../lib/pipeline-contract.mjs";
import {
  defaultCompilePaths,
  inspectExerciseReadiness,
  loadCompileContext,
} from "./compile-exercise.mjs";
import {
  buildArchetypeCandidates,
  findDeterministicMapping,
  resolveArchetypeId,
  resolveExerciseMetadata,
} from "../../lib/metadata-resolution.mjs";

const AUDIT_CODES = Object.freeze({
  MISSING_EXERCISE_METADATA: "MISSING_METADATA",
  MISSING_EQUIPMENT_VIEW: "MISSING_VIEW",
});

export async function auditAutogenReadiness(context) {
  const results = [];
  const manualDecisions = new Set();
  const manualDecisionFields = new Map();
  const exercises = context.exerciseCatalog.exercises ?? [];
  const deterministicMapping = context.deterministicMapping ?? context.deterministicMappings ?? {};
  const candidates = buildArchetypeCandidates(exercises, deterministicMapping);
  const overrides = context.overrides?.exercises ?? {};
  const requiredOverrideIds = new Set();

  function addManualDecision(key, field) {
    manualDecisions.add(key);
    const keys = manualDecisionFields.get(field) ?? new Set();
    keys.add(key);
    manualDecisionFields.set(field, keys);
  }

  for (const exercise of exercises) {
    const override = overrides[exercise.id] ?? {};
    const deterministic = findDeterministicMapping(exercise, deterministicMapping);
    const archetypeId = resolveArchetypeId({ exercise, override, deterministic });
    const archetype = context.archetypes.archetypes?.[archetypeId];
    const metadata = resolveExerciseMetadata({ exercise, override, archetype, deterministic });
    for (const field of metadata.missingFields) addManualDecision(`${exercise.id}.${field}`, field);
    if (deterministic.requiresExerciseOverride === true
      || exercise.overridePolicy === "required_exception"
      || override.required === true
      || override.kind === "exception") {
      requiredOverrideIds.add(exercise.id);
    }
    if (!archetypeId) addManualDecision(`${exercise.id}.archetypeId`, "archetypeId");
    else if (!archetype) addManualDecision(`archetype.${archetypeId}`, "archetypeDefinition");
    const readiness = await inspectExerciseReadiness(exercise.id, context);
    results.push({
      exerciseId: exercise.id,
      nameKo: exercise.nameKo,
      status: readiness.ready ? "READY" : (AUDIT_CODES[readiness.code] ?? readiness.code),
      compilerCode: readiness.code,
      details: readiness.ready ? {} : readiness.details,
    });
  }
  for (const asset of context.equipmentCatalog.assets ?? []) {
    if (asset.status === null || asset.status === undefined || asset.status === "") addManualDecision(`equipment.${asset.id}.status`, "equipmentStatus");
    if (asset.renderClass === null || asset.renderClass === undefined || asset.renderClass === "") addManualDecision(`equipment.${asset.id}.renderClass`, "equipmentRenderClass");
  }
  const counts = {};
  for (const result of results) counts[result.status] = (counts[result.status] ?? 0) + 1;
  const ready = counts.READY ?? 0;
  const total = results.length;
  const overrideIds = Object.keys(overrides).filter((id) => exercises.some((exercise) => exercise.id === id));
  return {
    schemaVersion: 1,
    ready: ready === total && total > 0,
    total,
    readyCount: ready,
    readinessPercent: total === 0 ? 0 : Number(((ready / total) * 100).toFixed(2)),
    counts: Object.fromEntries(Object.entries(counts).sort(([left], [right]) => left.localeCompare(right))),
    manualDecisions: {
      unit: "exercise_or_equipment_field",
      count: manualDecisions.size,
      byField: Object.fromEntries(
        [...manualDecisionFields.entries()]
          .map(([field, keys]) => [field, keys.size])
          .sort(([left], [right]) => left.localeCompare(right)),
      ),
    },
    archetypeCandidateCount: candidates.length,
    exerciseSpecificOverrideCount: overrideIds.length,
    exerciseSpecificOverrideIds: overrideIds.sort(),
    exerciseSpecificOverrideRequiredCount: requiredOverrideIds.size,
    exerciseSpecificOverrideRequiredIds: [...requiredOverrideIds].sort(),
    results,
  };
}

function parseArguments(argv, defaults) {
  const paths = { ...defaults };
  let jsonOutput = null;
  let table = false;
  const flags = {
    "--exercise-catalog": "exerciseCatalog", "--name-index": "nameIndex",
    "--overrides": "overrides", "--archetypes": "archetypes",
    "--deterministic-mapping": "deterministicMapping",
    "--equipment-catalog": "equipmentCatalog", "--muscle-layers": "muscleLayers",
  };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (flags[argument]) paths[flags[argument]] = path.resolve(argv[++index]);
    else if (argument === "--json-output") jsonOutput = path.resolve(argv[++index]);
    else if (argument === "--table") table = true;
    else throw new Error(`Unexpected argument: ${argument}`);
  }
  return { paths, jsonOutput, table };
}

function printTable(report) {
  console.log(`READY ${report.readyCount}/${report.total} (${report.readinessPercent}%)`);
  console.log(`ARCHETYPE_CANDIDATES\t${report.archetypeCandidateCount}`);
  console.log(`MANUAL_DECISIONS\t${report.manualDecisions.count}`);
  console.log(`EXERCISE_OVERRIDES\t${report.exerciseSpecificOverrideCount}`);
  console.log(`REQUIRED_EXERCISE_OVERRIDES\t${report.exerciseSpecificOverrideRequiredCount}`);
  for (const [status, count] of Object.entries(report.counts)) console.log(`${status}\t${count}`);
  console.log("\nSTATUS\tEXERCISE_ID\tNAME_KO");
  for (const result of report.results) console.log(`${result.status}\t${result.exerciseId}\t${result.nameKo}`);
}

async function main() {
  const toolsDirectory = path.dirname(fileURLToPath(import.meta.url));
  const repositoryRoot = path.resolve(toolsDirectory, "..", "..", "..");
  const args = parseArguments(process.argv.slice(2), defaultCompilePaths(repositoryRoot));
  const context = await loadCompileContext(args.paths);
  const report = await auditAutogenReadiness(context);
  if (args.jsonOutput) await writeJson(args.jsonOutput, report);
  if (args.table) printTable(report);
  else console.log(JSON.stringify(report, null, 2));
  if (!report.ready) process.exitCode = 2;
}

if (isDirectRun(import.meta.url)) {
  main().catch((error) => {
    printPipelineError(error);
    process.exitCode = error instanceof PipelineError ? 2 : 1;
  });
}
