import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import {
  loadExerciseFamilyContract,
  matchLegacyFamilyRules,
  resolveImageIdentity,
  validateExerciseFamilyContract,
} from '../lib/exercise-family-contract.mjs';
import { auditLegacyExerciseMapping } from '../family/tools/audit-legacy-exercise-mapping.mjs';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const contractPath = path.join(repositoryRoot, 'EXERCISE_FAMILY_CATALOG_V1.yaml');
const legacyPath = path.join(repositoryRoot, 'Fitness_Weight.json');

test('normative exercise family contract validates without taxonomy edits', () => {
  const contract = loadExerciseFamilyContract(contractPath);
  assert.deepEqual(validateExerciseFamilyContract(contract), []);
  assert.equal(Object.keys(contract.families).length, 103);
  assert.equal(contract.approvedNewPresets.length, 27);
  assert.equal(contract.canonicalAliasMerges.length, 4);
  assert.equal(contract.loadAccounting.defaultImplementMultiplier, 1);
  assert.equal(contract.loadAccounting.implementMultiplierOverrides.chest_dumbbell_flat_bench_press, 2);
  assert.equal(contract.loadAccounting.lateralityOverrides.legs_dumbbell_bulgarian_split_squat, 'unilateral');
});

test('the three resolved legacy IDs match exactly one intended family', () => {
  const contract = loadExerciseFamilyContract(contractPath);
  assert.deepEqual(matchLegacyFamilyRules(contract, 'chest_cable_crossover'), ['chest_fly']);
  assert.deepEqual(matchLegacyFamilyRules(contract, 'arms_cable_triceps_pushdown_straight_bar'), ['triceps_pushdown']);
  assert.deepEqual(matchLegacyFamilyRules(contract, 'legs_bodyweight_wall_sit'), ['wall_sit']);
  assert.equal(contract.variantTokenRules.tokens.straight_bar.handle, 'straight_bar');
});

test('legacy audit maps the complete catalog and preserves wall-sit time semantics', async () => {
  const result = await auditLegacyExerciseMapping({ contractPath, legacyPath, noWrite: true });
  assert.equal(result.report.total, 340);
  assert.equal(result.report.mapped, 340);
  assert.equal(result.report.unmappedCount, 0);
  assert.equal(result.report.ambiguousCount, 0);
  assert.equal(result.report.stopped, false);
  const wallSit = result.document.legacyExercises.find((entry) => entry.legacyExerciseId === 'legs_bodyweight_wall_sit');
  assert.equal(wallSit.familyId, 'wall_sit');
  assert.equal(wallSit.legacyRecordType, 'time');
  assert.equal(wallSit.defaultLoadState, 'bodyweight');
  const wallSitFamily = Object.values(result.document.families).find((family) => family.familyId === 'wall_sit');
  assert.deepEqual(wallSitFamily.allowedLoadStates, ['bodyweight', 'added_weight']);
  const straightBar = result.document.legacyExercises.find(
    (entry) => entry.legacyExerciseId === 'arms_cable_triceps_pushdown_straight_bar',
  );
  assert.equal(straightBar.variant.handle, 'straight_bar');
  const flatDumbbellPress = result.document.legacyExercises.find(
    (entry) => entry.legacyExerciseId === 'chest_dumbbell_flat_bench_press',
  );
  const dumbbellPullover = result.document.legacyExercises.find(
    (entry) => entry.legacyExerciseId === 'chest_dumbbell_pullover',
  );
  const singleArmRow = result.document.legacyExercises.find(
    (entry) => entry.legacyExerciseId === 'back_dumbbell_one_arm_row',
  );
  const bulgarianSplitSquat = result.document.legacyExercises.find(
    (entry) => entry.legacyExerciseId === 'legs_dumbbell_bulgarian_split_squat',
  );
  assert.equal(flatDumbbellPress.implementMultiplier, 2);
  assert.equal(dumbbellPullover.implementMultiplier, 1);
  assert.equal(singleArmRow.laterality, 'unilateral');
  assert.equal(singleArmRow.implementMultiplier, 1);
  assert.equal(bulgarianSplitSquat.laterality, 'unilateral');
  assert.equal(bulgarianSplitSquat.implementMultiplier, 2);
  for (const merge of loadExerciseFamilyContract(contractPath).canonicalAliasMerges) {
    const mergedEntries = merge.legacyIds.map((legacyId) => result.document.legacyExercises.find(
      (entry) => entry.legacyExerciseId === legacyId,
    ));
    assert.ok(mergedEntries.every((entry) => entry?.canonicalVariantKey === mergedEntries[0]?.canonicalVariantKey));
  }
});

test('image identity fallback is exact variant, family default, then placeholder', async () => {
  const registry = JSON.parse(await fs.readFile(
    path.join(repositoryRoot, 'model_image', 'data', 'exercise-image-identity-v1.json'),
    'utf8',
  ));
  const exact = resolveImageIdentity(registry, {
    familyId: 'biceps_curl',
    visualVariantKey: '{"equipment":"dumbbell","laterality":"bilateral","legacyTokens":["arms","dumbbell","curl"]}',
  });
  assert.equal(exact.source, 'exact_visual_variant');
  assert.equal(exact.illustrationKey, 'dumbbell-curl');
  const familyDefault = resolveImageIdentity(registry, {
    familyId: 'biceps_curl',
    visualVariantKey: '{"equipment":"barbell","laterality":"bilateral"}',
  });
  assert.equal(familyDefault.source, 'family_default');
  assert.equal(familyDefault.illustrationKey, 'dumbbell-curl');
  const placeholder = resolveImageIdentity(registry, {
    familyId: 'deadlift',
    visualVariantKey: '{"equipment":"barbell"}',
  });
  assert.equal(placeholder.source, 'placeholder');
  assert.equal(placeholder.illustrationKey, 'placeholder');
});
