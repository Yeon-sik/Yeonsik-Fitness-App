#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const packPath = path.join(root, 'model_image/archetypes/DECISION_PACK.json');
const catalogPath = path.join(root, 'model_image/equipment/equipment-catalog.json');
const outputJson = path.join(root, 'model_image/archetypes/MACHINE_FAMILY_PLAN.json');
const outputMarkdown = path.join(root, 'model_image/archetypes/MACHINE_FAMILY_PLAN.md');

const plans = {
  chest_press_machine: { label: 'chest press machine family', assets: [
    ['chest_press_machine_flat_v1', 'chest_press_machine', 'front_three_quarter', 'fixed_machine'],
    ['chest_press_machine_incline_v1', 'chest_press_machine', 'front_three_quarter', 'fixed_machine'],
    ['chest_press_machine_decline_v1', 'chest_press_machine', 'front_three_quarter', 'fixed_machine'],
    ['iso_lateral_chest_press_v1', 'iso_lateral_chest_press', 'front_three_quarter', 'fixed_machine'],
  ]},
  machine_curl: { label: 'machine curl family', assets: [
    ['machine_curl_v1', 'machine_curl', 'front_three_quarter', 'fixed_machine'],
    ['preacher_curl_machine_v1', 'preacher_curl_machine', 'front_three_quarter', 'fixed_machine'],
  ]},
  machine_row: { label: 'machine row family', assets: [
    ['seated_row_machine_front_side_three_quarter_v1', 'seated_row_machine', 'front_side_three_quarter', 'fixed_machine'],
    ['high_row_machine_v1', 'high_row_machine', 'front_side_three_quarter', 'fixed_machine'],
    ['t_bar_row_machine_v1', 't_bar_row_machine', 'front_side_three_quarter', 'fixed_machine'],
    ['chest_supported_row_machine_v1', 'chest_supported_row_machine', 'front_side_three_quarter', 'fixed_machine'],
    ['iso_lateral_row_machine_v1', 'iso_lateral_row_machine', 'front_side_three_quarter', 'fixed_machine'],
  ]},
  machine_squat: { label: 'machine squat family', assets: [
    ['belt_squat_machine_v1', 'belt_squat_machine', 'front_three_quarter', 'fixed_machine'],
    ['v_squat_machine_v1', 'v_squat_machine', 'front_three_quarter', 'fixed_machine'],
    ['pendulum_squat_machine_v1', 'pendulum_squat_machine', 'side_three_quarter', 'fixed_machine'],
    ['hack_squat_machine_v1', 'hack_squat_machine', 'side_three_quarter', 'fixed_machine'],
  ]},
  machine_shoulder_press: { label: 'machine shoulder press family', assets: [['machine_shoulder_press_v1', 'machine_shoulder_press', 'front_three_quarter', 'fixed_machine']] },
  machine_triceps_extension: { label: 'machine triceps extension family', assets: [['machine_triceps_extension_v1', 'machine_triceps_extension', 'front_three_quarter', 'fixed_machine']] },
  machine_pullover: { label: 'machine pullover', assets: [['machine_pullover_v1', 'machine_pullover', 'front_side_three_quarter', 'fixed_machine']] },
  ab_crunch_machine: { label: 'ab crunch machine', assets: [['ab_crunch_machine_v1', 'ab_crunch_machine', 'front_three_quarter', 'fixed_machine']] },
  back_extension_machine: { label: 'back extension machine', assets: [['back_extension_machine_v1', 'back_extension_machine', 'side_three_quarter', 'fixed_machine']] },
  reverse_hyper_machine: { label: 'reverse hyper machine', assets: [['reverse_hyper_machine_v1', 'reverse_hyper_machine', 'side_three_quarter', 'fixed_machine']] },
  glute_kickback_machine: { label: 'glute kickback machine', assets: [['glute_kickback_machine_v1', 'glute_kickback_machine', 'side_three_quarter', 'fixed_machine']] },
  torso_rotation_machine: { label: 'torso rotation machine', assets: [['torso_rotation_machine_v1', 'torso_rotation_machine', 'front_three_quarter', 'fixed_machine']] },
  shrug_machine: { label: 'shrug machine', assets: [['shrug_machine_v1', 'shrug_machine', 'front_three_quarter', 'fixed_machine']] },
  other_machine_leg_curl: { label: '기타 / machine leg curl family', assets: [['standing_leg_curl_machine_v1', 'standing_leg_curl_machine', 'front_side_three_quarter', 'fixed_machine']] },
  other_reverse_pec_deck: { label: '기타 / reverse pec-deck family', assets: [['pec_deck_front_v1', 'pec_deck', 'front', 'fixed_machine'], ['reverse_pec_deck_v1', 'reverse_pec_deck', 'rear_three_quarter', 'fixed_machine']] },
  other_machine_dip_press: { label: '기타 / machine dip press family', assets: [['machine_dip_press_v1', 'machine_dip_press', 'front_three_quarter', 'fixed_machine']] },
  other_machine_lateral_raise: { label: '기타 / machine lateral raise family', assets: [['machine_lateral_raise_v1', 'machine_lateral_raise', 'front_three_quarter', 'fixed_machine']] },
  other_machine_hip_thrust: { label: '기타 / machine hip thrust family', assets: [['TODO_USER_APPROVAL_machine_hip_thrust', 'machine_hip_thrust', 'TODO_USER_APPROVAL', 'fixed_machine']] },
};

const existingById = new Map(JSON.parse(fs.readFileSync(catalogPath, 'utf8')).assets.map((asset) => [asset.id, asset]));
const pack = JSON.parse(fs.readFileSync(packPath, 'utf8'));

function familyIdFor(familyId) {
  if (familyId === 'chest_press') return 'chest_press_machine';
  if (familyId === 'biceps_curl') return 'machine_curl';
  if (familyId === 'row') return 'machine_row';
  if (familyId === 'machine_squat') return 'machine_squat';
  if (familyId === 'overhead_press') return 'machine_shoulder_press';
  if (familyId === 'machine_triceps_extension') return 'machine_triceps_extension';
  if (familyId === 'pullover') return 'machine_pullover';
  if (familyId === 'crunch') return 'ab_crunch_machine';
  if (familyId === 'back_extension') return 'back_extension_machine';
  if (familyId === 'reverse_hyperextension') return 'reverse_hyper_machine';
  if (familyId === 'glute_kickback') return 'glute_kickback_machine';
  if (familyId === 'torso_rotation') return 'torso_rotation_machine';
  if (familyId === 'shrug') return 'shrug_machine';
  if (familyId === 'leg_curl') return 'other_machine_leg_curl';
  if (familyId === 'rear_delt_fly') return 'other_reverse_pec_deck';
  if (familyId === 'machine_dip_press') return 'other_machine_dip_press';
  if (familyId === 'lateral_raise') return 'other_machine_lateral_raise';
  if (familyId === 'hip_thrust') return 'other_machine_hip_thrust';
  throw new Error("UNCLASSIFIED_MACHINE_FAMILY: " + familyId);
}

function assetKeyFor(familyId, exerciseId) {
  if (familyId === 'chest_press') {
    if (exerciseId.includes('iso_lateral')) return 'iso_lateral_chest_press_v1';
    if (exerciseId.includes('incline')) return 'chest_press_machine_incline_v1';
    if (exerciseId.includes('decline')) return 'chest_press_machine_decline_v1';
    return 'chest_press_machine_flat_v1';
  }
  if (familyId === 'biceps_curl') return exerciseId.includes('preacher') ? 'preacher_curl_machine_v1' : 'machine_curl_v1';
  if (familyId === 'row') {
    if (exerciseId.includes('high_row')) return 'high_row_machine_v1';
    if (exerciseId.includes('t_bar')) return 't_bar_row_machine_v1';
    if (exerciseId.includes('chest_supported')) return 'chest_supported_row_machine_v1';
    if (exerciseId.includes('iso_lateral')) return 'iso_lateral_row_machine_v1';
    return 'seated_row_machine_front_side_three_quarter_v1';
  }
  if (familyId === 'machine_squat') {
    if (exerciseId.includes('belt')) return 'belt_squat_machine_v1';
    if (exerciseId.includes('v_squat')) return 'v_squat_machine_v1';
    if (exerciseId.includes('pendulum')) return 'pendulum_squat_machine_v1';
    return 'hack_squat_machine_v1';
  }
  if (familyId === 'overhead_press') return 'machine_shoulder_press_v1';
  if (familyId === 'machine_triceps_extension') return 'machine_triceps_extension_v1';
  if (familyId === 'pullover') return 'machine_pullover_v1';
  if (familyId === 'crunch') return 'ab_crunch_machine_v1';
  if (familyId === 'back_extension') return 'back_extension_machine_v1';
  if (familyId === 'reverse_hyperextension') return 'reverse_hyper_machine_v1';
  if (familyId === 'glute_kickback') return 'glute_kickback_machine_v1';
  if (familyId === 'torso_rotation') return 'torso_rotation_machine_v1';
  if (familyId === 'shrug') return 'shrug_machine_v1';
  if (familyId === 'leg_curl') return 'standing_leg_curl_machine_v1';
  if (familyId === 'rear_delt_fly') return exerciseId.includes('reverse_pec_deck') ? 'pec_deck_front_v1' : 'pec_deck_front_v1';
  if (familyId === 'machine_dip_press') return 'machine_dip_press_v1';
  if (familyId === 'lateral_raise') return 'machine_lateral_raise_v1';
  if (familyId === 'hip_thrust') return 'TODO_USER_APPROVAL_machine_hip_thrust';
  throw new Error(`UNCLASSIFIED_MACHINE_FAMILY: ${familyId}`);
}

const missingRows = [];
for (const candidate of pack.archetypeDecisions) {
  if (!candidate.requiredEquipment.some((item) => item.status === 'MISSING_ASSET')) continue;
  const machineFamilyId = familyIdFor(candidate.groupingKey.familyId);
  for (const exercise of candidate.exercises) missingRows.push({
    exerciseId: exercise.id,
    exerciseName: exercise.nameKo,
    familyId: candidate.groupingKey.familyId,
    machineFamilyId,
    canonicalAssetId: assetKeyFor(candidate.groupingKey.familyId, exercise.id),
  });
}

const assetPlans = [];
for (const [machineFamilyId, plan] of Object.entries(plans)) {
  for (const [assetId, type, recommendedViewId, renderClass] of plan.assets) {
    const existing = existingById.get(assetId);
    const reuse = Boolean(existing);
    assetPlans.push({
      machineFamilyId,
      label: plan.label,
      assetId,
      type,
      recommendedViewId: existing?.viewId ?? recommendedViewId,
      renderClass: existing?.renderClass ?? renderClass,
      sourceAction: assetId.startsWith('TODO_') ? 'UNRESOLVED_NOT_IN_APPROVED_PLAN' : (reuse ? 'REUSE_EXISTING_CANONICAL' : 'SOURCE_REQUIRED_NO_IMAGE_YET'),
      sourcePath: existing?.file ?? `source/${assetId.replace(/_v1$/, '')}.png`,
      finalPath: existing?.file ?? `final/${assetId.replace(/_v1$/, '')}.png`,
      promotionStatus: assetId.startsWith('TODO_') ? 'NOT_IN_APPROVED_PLAN' : (reuse ? 'ALREADY_PROMOTED' : 'PLANNED_AFTER_SOURCE_REVIEW'),
      reversePecDeckFallback: assetId === 'reverse_pec_deck_v1' ? 'CREATE_ONLY_IF_pec_deck_reverse_test_fails' : null,
    });
  }
}

const familyPlans = Object.entries(plans)
  .map(([machineFamilyId, plan]) => ({
    machineFamilyId,
    label: plan.label,
    assets: assetPlans.filter((asset) => asset.machineFamilyId === machineFamilyId),
    exercises: missingRows.filter((row) => row.machineFamilyId === machineFamilyId),
  }))
  .filter((plan) => plan.exercises.length > 0);

const output = {
  schemaVersion: 1,
  status: 'MACHINE_FAMILY_PLAN_APPROVED_SOURCE_PENDING',
  source: 'MISSING_ASSET machine family approval',
  constraints: {
    noImageGenerationAdapter: true,
    noArchetypeCameraPoseChanges: true,
    noExerciseSpecificAssetPerExercise: true,
    existingViewIdsPreserved: true,
  },
  familyPlans,
  summary: {
    missingExerciseCount: missingRows.length,
    familyCount: familyPlans.length,
    assetPlanCount: assetPlans.length,
    sourceRequiredCount: assetPlans.filter((asset) => asset.sourceAction === 'SOURCE_REQUIRED_NO_IMAGE_YET').length,
    existingCanonicalReuseCount: assetPlans.filter((asset) => asset.sourceAction === 'REUSE_EXISTING_CANONICAL').length,
  },
};

fs.writeFileSync(outputJson, `${JSON.stringify(output, null, 2)}\n`);

const md = [];
md.push('# Canonical Machine Family Plan');
md.push('');
md.push('- 상태: **MACHINE_FAMILY_PLAN_APPROVED_SOURCE_PENDING**');
md.push('- 목적: 운동별 신규 자산이 아니라 reusable canonical machine family를 계획한다.');
md.push('- 실제 image generation API/adapter와 archetype camera/A/B는 아직 변경하지 않는다.');
md.push('');
md.push(`- MISSING_ASSET 운동: **${output.summary.missingExerciseCount}**`);
md.push(`- machine family: **${output.summary.familyCount}**`);
md.push(`- asset plan: **${output.summary.assetPlanCount}** (기존 canonical 재사용 ${output.summary.existingCanonicalReuseCount}, source 필요 ${output.summary.sourceRequiredCount})`);
md.push('');
md.push('| family | canonical asset | source action | recommended viewId | promotion | shared exercises |');
md.push('|---|---|---|---|---|---|');
for (const family of familyPlans) {
  for (const asset of family.assets) {
    const exercises = family.exercises.filter((exercise) => exercise.canonicalAssetId === asset.assetId).map((exercise) => `${exercise.exerciseName} (${exercise.exerciseId})`).join('<br>') || '-';
    md.push(`| ${family.label} | ${asset.assetId} | ${asset.sourceAction} | ${asset.recommendedViewId} | ${asset.promotionStatus} | ${exercises} |`);
  }
}
md.push('');
md.push('## Exercise mapping');
md.push('');
md.push('| exercise | familyId | canonical asset |');
md.push('|---|---|---|');
for (const exercise of missingRows) md.push(`| ${exercise.exerciseName} (${exercise.exerciseId}) | ${exercise.familyId} | ${exercise.canonicalAssetId} |`);
md.push('');
md.push('## Promotion boundary');
md.push('');
md.push('- `SOURCE_REQUIRED_NO_IMAGE_YET` 자산은 source PNG, anchors, SHA-256, 검수 후에만 `final/`과 `equipment-catalog.json`에 등록한다.');
md.push('- `pec_deck_front_v1`은 reverse orientation 합성 테스트에 먼저 사용하며, 부자연스러울 때만 `reverse_pec_deck_v1`을 source 생성 대상으로 전환한다.');
md.push('- 승인된 viewId는 계획값으로 제시했으며, 실제 camera/A/B 결정과 혼동하지 않는다.');
fs.writeFileSync(outputMarkdown, `${md.join('\n')}\n`);
console.log(JSON.stringify(output.summary, null, 2));
