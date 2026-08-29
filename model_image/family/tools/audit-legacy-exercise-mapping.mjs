#!/usr/bin/env node

import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import {
  ExerciseFamilyContractError,
  assertValidExerciseFamilyContract,
  buildFamilyCatalogDocument,
  buildLegacyExerciseMapping,
  loadExerciseFamilyContract,
} from '../../lib/exercise-family-contract.mjs';

function parseArguments(argv, repositoryRoot) {
  const options = {
    contractPath: path.join(repositoryRoot, 'EXERCISE_FAMILY_CATALOG_V1.yaml'),
    legacyPath: path.join(repositoryRoot, 'Fitness_Weight.json'),
    outputPath: path.join(repositoryRoot, 'model_image', 'family', 'data', 'exercise-family-mapping-v1.json'),
    noWrite: false,
    json: false,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === '--contract') options.contractPath = path.resolve(argv[++index]);
    else if (argument === '--legacy') options.legacyPath = path.resolve(argv[++index]);
    else if (argument === '--output') options.outputPath = path.resolve(argv[++index]);
    else if (argument === '--no-write') options.noWrite = true;
    else if (argument === '--json') options.json = true;
    else throw new Error(`Unexpected argument: ${argument}`);
  }
  return options;
}

function summarizeEntries(entries) {
  return entries.map((entry) => ({
    legacyExerciseId: entry.exercise?.id ?? entry.legacyExerciseId,
    nameKo: entry.exercise?.nameKo ?? entry.nameKo,
    familyMatches: entry.familyMatches ?? [],
    error: entry.error ?? null,
  }));
}

function validateAliasTargets(contract, exercises, mapping) {
  const errors = [];
  const exerciseIds = new Set(exercises.map((exercise) => exercise.id));
  const entriesById = new Map(mapping.entries.filter((entry) => entry.status === 'mapped').map((entry) => [entry.legacyExerciseId, entry]));
  const approvedPresetIds = new Set((contract.approvedNewPresets ?? []).map((preset) => preset.presetId));

  for (const merge of contract.canonicalAliasMerges ?? []) {
    for (const legacyId of merge.legacyIds ?? []) {
      if (!exerciseIds.has(legacyId)) errors.push(`canonical alias ${merge.canonicalPresetId} references missing legacy ID ${legacyId}`);
      const mapped = entriesById.get(legacyId);
      if (mapped && mapped.familyId !== merge.familyId) errors.push(`canonical alias ${merge.canonicalPresetId} maps ${legacyId} to ${mapped.familyId}, expected ${merge.familyId}`);
    }
  }

  for (const alias of contract.searchPresetAliases ?? []) {
    if (!approvedPresetIds.has(alias.targetPreset) && !entriesById.has(alias.targetPreset)) {
      errors.push(`search alias ${alias.alias} targets missing preset ${alias.targetPreset}`);
    }
    const target = entriesById.get(alias.targetPreset);
    if (target && target.familyId !== alias.familyId) errors.push(`search alias ${alias.alias} targets ${target.familyId}, expected ${alias.familyId}`);
    if (alias.legacyId) {
      if (!exerciseIds.has(alias.legacyId)) errors.push(`search alias ${alias.alias} references missing legacy ID ${alias.legacyId}`);
      const weighted = entriesById.get(alias.legacyId);
      if (weighted && weighted.familyId !== alias.familyId) errors.push(`search alias ${alias.alias} legacy ID maps to ${weighted.familyId}, expected ${alias.familyId}`);
    }
  }
  return errors;
}

export async function auditLegacyExerciseMapping({ contractPath, legacyPath, outputPath, noWrite = false }) {
  const contract = assertValidExerciseFamilyContract(loadExerciseFamilyContract(contractPath));
  const legacyDocument = JSON.parse(await fs.readFile(legacyPath, 'utf8'));
  const exercises = legacyDocument.exercises;
  if (!Array.isArray(exercises)) throw new Error('Fitness_Weight.json must contain an exercises list.');

  const ids = exercises.map((exercise) => exercise.id);
  const duplicateIds = [...new Set(ids.filter((id, index) => ids.indexOf(id) !== index))];
  const mapping = buildLegacyExerciseMapping(contract, exercises);
  const aliasErrors = validateAliasTargets(contract, exercises, mapping);
  const expectedCount = contract.requiredValidation?.legacyExerciseCountExpected ?? contract.sourceBaseline?.baselineExerciseCount;
  const report = {
    contractVersion: contract.contractVersion,
    legacyCatalog: path.basename(legacyPath),
    expectedLegacyExerciseCount: expectedCount,
    total: mapping.summary.total,
    mapped: mapping.summary.mapped,
    unmappedCount: mapping.summary.unmapped,
    ambiguousCount: mapping.summary.ambiguous,
    canonicalAliasMergeGroups: mapping.summary.canonicalAliasMergeGroups,
    aliasMergedLegacyIds: mapping.summary.aliasMergedLegacyIds,
    duplicateLegacyIdCount: duplicateIds.length,
    duplicateLegacyIds: duplicateIds,
    aliasTargetErrorCount: aliasErrors.length,
    aliasTargetErrors: aliasErrors,
    unmapped: summarizeEntries(mapping.unmapped),
    ambiguous: summarizeEntries(mapping.ambiguous),
    stopped: false,
    outputPath: noWrite ? null : outputPath,
  };

  const blockingErrors = [];
  if (exercises.length !== expectedCount) blockingErrors.push(`legacy exercise count ${exercises.length} !== expected ${expectedCount}`);
  if (duplicateIds.length > 0) blockingErrors.push(`duplicate legacy IDs: ${duplicateIds.join(', ')}`);
  if (mapping.unmapped.length > 0) blockingErrors.push(`unmapped legacy IDs: ${mapping.unmapped.map((entry) => entry.exercise.id).join(', ')}`);
  if (mapping.ambiguous.length > 0) blockingErrors.push(`ambiguous legacy IDs: ${mapping.ambiguous.map((entry) => entry.exercise.id).join(', ')}`);
  blockingErrors.push(...aliasErrors);
  if (blockingErrors.length > 0) {
    report.stopped = true;
    report.blockingErrors = blockingErrors;
    return { report, document: null };
  }

  const document = buildFamilyCatalogDocument(contract, exercises, mapping);
  if (!noWrite) {
    await fs.mkdir(path.dirname(outputPath), { recursive: true });
    await fs.writeFile(outputPath, `${JSON.stringify(document, null, 2)}\n`, 'utf8');
  }
  return { report, document };
}

function printReport(report) {
  console.log(JSON.stringify(report, null, 2));
  if (report.blockingErrors) for (const error of report.blockingErrors) console.error(`ERROR\t${error}`);
}

async function main() {
  const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const options = parseArguments(process.argv.slice(2), repositoryRoot);
  const result = await auditLegacyExerciseMapping(options);
  if (options.json) printReport(result.report);
  else {
    const report = result.report;
    console.log(`LEGACY_TOTAL\t${report.total}`);
    console.log(`MAPPED\t${report.mapped}`);
    console.log(`UNMAPPED\t${report.unmappedCount}`);
    console.log(`AMBIGUOUS\t${report.ambiguousCount}`);
    console.log(`CANONICAL_ALIAS_GROUPS\t${report.canonicalAliasMergeGroups}`);
    console.log(`ALIAS_MERGED_LEGACY_IDS\t${report.aliasMergedLegacyIds}`);
    console.log(`STATUS\t${report.stopped ? 'STOPPED' : 'VALID'}`);
    if (report.blockingErrors) for (const error of report.blockingErrors) console.error(`ERROR\t${error}`);
  }
  if (result.report.stopped) process.exitCode = 2;
}

if (path.resolve(fileURLToPath(import.meta.url)) === path.resolve(process.argv[1] ?? '')) {
  main().catch((error) => {
    if (error instanceof ExerciseFamilyContractError) {
      console.error(error.message);
      for (const detail of error.details ?? []) console.error(`ERROR\t${detail}`);
    } else {
      console.error(error?.stack ?? String(error));
    }
    process.exitCode = 1;
  });
}
