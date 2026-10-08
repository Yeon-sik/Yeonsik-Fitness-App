#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  assertValidExerciseFamilyContract, buildFamilyCatalogDocument,
  buildLegacyExerciseMapping, loadExerciseFamilyContract,
} from './lib/exercise-family-contract.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const output = path.join(root, 'app/src/main/assets-fallback/exercise_family_mapping_v1.json');

/** Catalog source owns identities; the approved bundled image export is carried through unchanged. */
export function generateExerciseCatalog() {
  const contract = assertValidExerciseFamilyContract(loadExerciseFamilyContract(
    path.join(root, 'EXERCISE_FAMILY_CATALOG_V1.yaml')));
  const exercises = JSON.parse(fs.readFileSync(path.join(root, 'Fitness_Weight.json'), 'utf8')).exercises;
  const ids = new Set(exercises.map(exercise => exercise.id));
  if (ids.size !== exercises.length) throw new Error('Duplicate exercise source IDs');
  const mapping = buildLegacyExerciseMapping(contract, exercises);
  if (mapping.unmapped.length || mapping.ambiguous.length) {
    throw new Error(`Invalid family mapping: ${JSON.stringify([...mapping.unmapped, ...mapping.ambiguous])}`);
  }
  for (const merge of contract.canonicalAliasMerges) {
    for (const id of merge.legacyIds) {
      const entry = mapping.mapped.find(entry => entry.legacyExerciseId === id);
      if (!entry || entry.familyId !== merge.familyId) throw new Error(`Invalid canonical alias target: ${id}`);
    }
  }
  for (const alias of contract.searchPresetAliases) {
    const entry = mapping.mapped.find(entry => entry.legacyExerciseId === alias.targetPreset);
    const approved = contract.approvedNewPresets.find(entry => entry.presetId === alias.targetPreset);
    if ((entry ?? approved)?.familyId !== alias.familyId) throw new Error(`Invalid search alias: ${alias.alias}`);
  }
  const images = JSON.parse(fs.readFileSync(output, 'utf8')).imageIdentity;
  return buildFamilyCatalogDocument(contract, exercises, mapping, images);
}

if (path.resolve(process.argv[1] ?? '') === fileURLToPath(import.meta.url)) {
  const document = generateExerciseCatalog();
  const expected = JSON.stringify(document, null, 2) + '\n';
  if (process.argv.includes('--check')) {
    const actual = JSON.parse(fs.readFileSync(output, 'utf8'));
    if (JSON.stringify(actual) !== JSON.stringify(document)) throw new Error('Exercise runtime projection is stale');
  } else fs.writeFileSync(output, expected);
  console.log(`Exercise catalog valid: ${document.summary.mapped} mapped, ${document.approvedPresets.length} approved presets`);
}
