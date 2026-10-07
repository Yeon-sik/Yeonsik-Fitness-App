import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { generateExerciseCatalog } from '../generate-exercise-catalog.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const catalog = generateExerciseCatalog();
const source = JSON.parse(fs.readFileSync(path.join(root, 'Fitness_Weight.json'), 'utf8')).exercises;
const normalized = text => text.toLowerCase().replace(/\s/g, '');

test('every source exercise maps once and runtime output equals the generated projection', () => {
  assert.equal(catalog.summary.total, 340);
  assert.equal(catalog.approvedPresets.length, 28);
  assert.equal(catalog.summary.unmapped, 0);
  assert.equal(catalog.summary.ambiguous, 0);
  assert.equal(new Set(catalog.legacyExercises.map(e => e.legacyExerciseId)).size, source.length);
  assert.deepEqual(JSON.parse(fs.readFileSync(path.join(root,
    'app/src/main/assets-fallback/exercise_family_mapping_v1.json'), 'utf8')), catalog);
});

test('aliases resolve to one declared canonical identity without creating source exercises', () => {
  for (const alias of catalog.searchPresetAliases) {
    const matches = catalog.searchPresetAliases.filter(a => normalized(a.alias) === normalized(alias.alias));
    assert.equal(new Set(matches.map(a => a.targetPreset)).size, 1, alias.alias);
    assert.ok(catalog.legacyExercises.some(e => e.legacyExerciseId === alias.targetPreset), alias.alias);
    if (!alias.legacyId) assert.ok(!source.some(e => e.nameKo === alias.alias), alias.alias);
  }
  for (const merge of catalog.canonicalAliasMerges) {
    const entries = catalog.legacyExercises.filter(e => merge.legacyIds.includes(e.legacyExerciseId));
    assert.equal(entries.length, merge.legacyIds.length);
    assert.deepEqual([...new Set(entries.map(e => e.canonicalPresetId))], [merge.canonicalPresetId]);
    assert.equal(new Set(entries.map(e => e.canonicalVariantKey)).size, 1);
  }
});

test('kettlebell sumo squat shares anatomy and record semantics while equipment separates performance identity', () => {
  const dumbbell = source.find(e => e.id === 'legs_dumbbell_sumo_squat');
  const kettlebell = catalog.approvedPresets.find(e => e.presetId === 'legs_kettlebell_sumo_squat');
  const fields = ['primarySubPart', 'secondarySubParts', 'recordType'];
  for (const key of fields) assert.deepEqual(kettlebell[key], dumbbell[key], key);
  assert.equal(kettlebell.defaultUiPart, dumbbell.uiPart);
  assert.equal(kettlebell.variant.equipment, 'kettlebell');
  assert.equal(kettlebell.laterality, dumbbell.laterality);
  const a = catalog.legacyExercises.find(e => e.legacyExerciseId === dumbbell.id);
  const b = kettlebell;
  assert.equal(a.familyId, 'squat');
  assert.equal(b.familyId, 'squat');
  assert.notEqual(a.canonicalVariantKey, b.canonicalVariantKey);
  assert.equal(a.implementMultiplier, b.implementMultiplier);
});

test('equipment, attachment, posture, and ambiguous candidates retain separate identities', () => {
  const groups = [
    ['arms_machine_preacher_curl', 'arms_dumbbell_preacher_curl', 'arms_barbell_preacher_curl', 'arms_cable_preacher_curl'],
    ['arms_cable_rope_pushdown', 'arms_cable_reverse_grip_pushdown', 'arms_cable_triceps_pushdown_straight_bar'],
    ['legs_machine_lying_leg_curl', 'legs_machine_standing_leg_curl', 'legs_machine_seated_leg_curl'],
    ['legs_dumbbell_romanian_deadlift', 'legs_barbell_romanian_deadlift', 'legs_smith_romanian_deadlift'],
  ];
  for (const ids of groups) {
    const entries = ids.map(id => catalog.legacyExercises.find(e => e.legacyExerciseId === id));
    assert.equal(new Set(entries.map(e => e.familyId+'|'+e.canonicalVariantKey)).size, ids.length);
  }
  for (const excluded of ['바벨 로우', '밀리터리 프레스', '케이블 풀오버', '스컬 크러셔', 'RFESS']) {
    assert.ok(!catalog.searchPresetAliases.some(a => a.alias === excluded));
  }
});
