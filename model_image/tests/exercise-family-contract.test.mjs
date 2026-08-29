import assert from 'node:assert/strict';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import {
  loadExerciseFamilyContract,
  matchLegacyFamilyRules,
  validateExerciseFamilyContract,
} from '../lib/exercise-family-contract.mjs';
import { auditLegacyExerciseMapping } from '../family/tools/audit-legacy-exercise-mapping.mjs';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const contractPath = path.join(repositoryRoot, 'EXERCISE_FAMILY_CATALOG_V1.yaml');
const legacyPath = path.join(repositoryRoot, 'Fitness_Weight.json');

test('normative exercise family contract validates without taxonomy edits', () => {
  const contract = loadExerciseFamilyContract(contractPath);
  assert.deepEqual(validateExerciseFamilyContract(contract), []);
  assert.equal(Object.keys(contract.families).length, 102);
  assert.equal(contract.approvedNewPresets.length, 27);
  assert.equal(contract.canonicalAliasMerges.length, 4);
});

test('legacy audit fails closed for every contract-unmapped ID', async () => {
  const result = await auditLegacyExerciseMapping({ contractPath, legacyPath, noWrite: true });
  assert.equal(result.report.total, 340);
  assert.equal(result.report.mapped, 337);
  assert.equal(result.report.unmappedCount, 3);
  assert.equal(result.report.ambiguousCount, 0);
  assert.equal(result.report.stopped, true);
  assert.deepEqual(
    result.report.unmapped.map((entry) => entry.legacyExerciseId),
    [
      'chest_cable_crossover',
      'legs_bodyweight_wall_sit',
      'arms_cable_triceps_pushdown_straight_bar',
    ],
  );
  assert.deepEqual(matchLegacyFamilyRules(loadExerciseFamilyContract(contractPath), 'legs_bodyweight_wall_sit'), []);
});
