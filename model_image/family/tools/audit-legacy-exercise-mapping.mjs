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
    imageIdentityPath: path.join(repositoryRoot, 'model_image', 'data', 'exercise-image-identity-v1.json'),
    noWrite: false,
    json: false,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === '--contract') options.contractPath = path.resolve(argv[++index]);
    else if (argument === '--legacy') options.legacyPath = path.resolve(argv[++index]);
    else if (argument === '--output') options.outputPath = path.resolve(argv[++index]);
    else if (argument === '--image-identity') options.imageIdentityPath = path.resolve(argv[++index]);
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

async function validateImageIdentityRegistry(contract, registry, registryPath) {
  const errors = [];
  if (!registry || registry.schemaVersion !== 1) {
    return ['image identity registry schemaVersion must be 1'];
  }
  const expectedFallback = contract.imageIdentity?.fallbackOrder ?? [];
  if (JSON.stringify(registry.fallbackOrder ?? []) !== JSON.stringify(expectedFallback)) {
    errors.push('image identity registry fallbackOrder must match the normative contract');
  }
  if (!Array.isArray(registry.imageVariants)) errors.push('image identity registry imageVariants must be an array');
  if (!Array.isArray(registry.familyDefaults)) errors.push('image identity registry familyDefaults must be an array');
  const familyIds = new Set(Object.keys(contract.families ?? {}));
  const registryRoot = path.resolve(path.dirname(registryPath), '..');
  const validateAssetReference = async (reference, label, expectedBasename = null) => {
    if (typeof reference !== 'string' || reference.trim().length === 0 || path.isAbsolute(reference)) {
      errors.push(`${label} must be a non-empty relative path`);
      return;
    }
    const resolved = path.resolve(path.dirname(registryPath), reference);
    const relativeToRoot = path.relative(registryRoot, resolved);
    if (!relativeToRoot || relativeToRoot.startsWith('..') || path.isAbsolute(relativeToRoot)) {
      errors.push(`${label} must remain inside model_image: ${reference}`);
      return;
    }
    if (expectedBasename && path.basename(resolved) !== expectedBasename) {
      errors.push(`${label} must use ${expectedBasename}`);
    }
    try {
      const stat = await fs.stat(resolved);
      if (!stat.isFile()) errors.push(`${label} does not reference a file: ${reference}`);
    } catch {
      errors.push(`${label} references a missing file: ${reference}`);
    }
  };
  const validateIdentityEntry = async (item, label, requireVisualVariant) => {
    if (!item || !familyIds.has(item.familyId)
      || (requireVisualVariant && (typeof item.visualVariantKey !== 'string' || !item.visualVariantKey.trim()))
      || typeof item.illustrationKey !== 'string' || !item.illustrationKey.trim()) {
      errors.push(`${label} must declare an existing familyId${requireVisualVariant ? ', visualVariantKey' : ''}, and illustrationKey`);
      return;
    }
    if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(item.illustrationKey)) {
      errors.push(`${label}.illustrationKey must be kebab-case`);
    }
    await validateAssetReference(item.sceneFile, `${label}.sceneFile`);
    if (!item.frameFiles || typeof item.frameFiles !== 'object' || Array.isArray(item.frameFiles)) {
      errors.push(`${label}.frameFiles must declare A and B`);
    } else {
      for (const frameId of ['A', 'B']) {
        await validateAssetReference(
          item.frameFiles[frameId],
          `${label}.frameFiles.${frameId}`,
          `${item.illustrationKey}-${frameId.toLowerCase()}.png`,
        );
      }
    }
    if (item.equipmentViews !== undefined
      && (!item.equipmentViews || typeof item.equipmentViews !== 'object' || Array.isArray(item.equipmentViews))) {
      errors.push(`${label}.equipmentViews must be a map when present`);
    }
  };
  const seenVariants = new Set();
  for (const [index, item] of (Array.isArray(registry.imageVariants) ? registry.imageVariants : []).entries()) {
    await validateIdentityEntry(item, `imageVariants[${index}]`, true);
    if (!item || !familyIds.has(item.familyId) || typeof item.visualVariantKey !== 'string' || !item.visualVariantKey.trim()) continue;
    const key = `${item.familyId}\n${item.visualVariantKey}`;
    if (seenVariants.has(key)) errors.push(`imageVariants duplicates ${item.familyId}/${item.visualVariantKey}`);
    seenVariants.add(key);
  }
  const seenDefaults = new Set();
  for (const [index, item] of (Array.isArray(registry.familyDefaults) ? registry.familyDefaults : []).entries()) {
    await validateIdentityEntry(item, `familyDefaults[${index}]`, false);
    if (!item || !familyIds.has(item.familyId) || typeof item.illustrationKey !== 'string' || !item.illustrationKey.trim()) continue;
    if (seenDefaults.has(item.familyId)) errors.push(`familyDefaults duplicates ${item.familyId}`);
    seenDefaults.add(item.familyId);
  }
  return errors;
}

export async function auditLegacyExerciseMapping({ contractPath, legacyPath, outputPath, imageIdentityPath, noWrite = false }) {
  const contract = assertValidExerciseFamilyContract(loadExerciseFamilyContract(contractPath));
  const legacyDocument = JSON.parse(await fs.readFile(legacyPath, 'utf8'));
  const resolvedImageIdentityPath = imageIdentityPath
    ?? path.join(path.dirname(contractPath), 'model_image', 'data', 'exercise-image-identity-v1.json');
  const imageIdentityRegistry = JSON.parse(await fs.readFile(resolvedImageIdentityPath, 'utf8'));
  const exercises = legacyDocument.exercises;
  if (!Array.isArray(exercises)) throw new Error('Fitness_Weight.json must contain an exercises list.');

  const ids = exercises.map((exercise) => exercise.id);
  const duplicateIds = [...new Set(ids.filter((id, index) => ids.indexOf(id) !== index))];
  const mapping = buildLegacyExerciseMapping(contract, exercises);
  const aliasErrors = validateAliasTargets(contract, exercises, mapping);
  const imageIdentityErrors = await validateImageIdentityRegistry(contract, imageIdentityRegistry, resolvedImageIdentityPath);
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
    imageIdentityErrorCount: imageIdentityErrors.length,
    imageIdentityErrors,
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
  blockingErrors.push(...imageIdentityErrors);
  if (blockingErrors.length > 0) {
    report.stopped = true;
    report.blockingErrors = blockingErrors;
    return { report, document: null };
  }

  const document = buildFamilyCatalogDocument(contract, exercises, mapping, imageIdentityRegistry);
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
