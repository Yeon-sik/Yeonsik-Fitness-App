import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const readJson = (relativePath) => JSON.parse(fs.readFileSync(path.join(root, relativePath), 'utf8'));
const fitness = readJson('Fitness_Weight.json');
const layers = readJson('model_image/style-4/muscle-layers.json');
const equipmentCatalog = readJson('model_image/equipment/equipment-catalog.json');
const familyMapping = readJson('model_image/family/data/exercise-family-mapping-v1.json');
const identity = readJson('model_image/data/exercise-image-identity-v1.json');
const bookmarkCatalog = readJson('model_image/equipment/equipment-catalog(2).json');

const byExerciseId = new Map(familyMapping.legacyExercises.map((row) => [row.legacyExerciseId, row]));
const layerById = new Map(layers.layers.map((layer) => [layer.id, layer]));
const groupToLayers = layers.exerciseGroups;
const assetByType = new Map();
for (const asset of equipmentCatalog.assets) {
  const list = assetByType.get(asset.type) ?? [];
  list.push(asset);
  assetByType.set(asset.type, list);
}

const sourceRoot = path.join(root, 'model_image/equipment/source');
const sourceFiles = fs.readdirSync(sourceRoot, { recursive: true })
  .filter((file) => String(file).toLowerCase().endsWith('.png'))
  .map((file) => String(file).replaceAll('\\', '/'));
const sourceByBasename = new Map(sourceFiles.map((file) => [path.posix.basename(file), `source/${file}`]));
const bookmarkByBasename = new Map((bookmarkCatalog.assets ?? []).map((asset) => [path.posix.basename(asset.file), asset]));

const canonicalAssetForType = (type) => (assetByType.get(type) ?? [])[0] ?? null;
const sourceAssetFor = (basename) => {
  const sourceKey = path.posix.basename(basename);
  const sourcePng = sourceByBasename.get(sourceKey) ?? null;
  const bookmark = bookmarkByBasename.get(sourceKey) ?? null;
  const canonical = canonicalAssetForType(bookmark?.type);
  return {
    assetId: canonical?.id ?? bookmark?.id ?? null,
    assetType: bookmark?.type ?? null,
    currentPng: canonical?.file ?? null,
    currentViewId: canonical?.viewId ?? null,
    sourcePng,
    sourceViewId: bookmark?.viewId ?? null,
    bookmark: bookmark ? { id: bookmark.id, type: bookmark.type, viewId: bookmark.viewId, file: bookmark.file } : null,
    status: canonical?.status === 'approved' ? 'APPROVED_CANONICAL' : sourcePng ? 'NOT_YET_PROMOTED' : 'MISSING_ASSET',
    renderClass: canonical?.renderClass ?? 'TODO_USER_APPROVAL',
  };
};

const canonicalRawEquipment = (equipmentType) => {
  const canonical = canonicalAssetForType(equipmentType);
  if (canonical) return {
    assetId: canonical.id,
    assetType: canonical.type,
    currentPng: canonical.file,
    currentViewId: canonical.viewId,
    sourcePng: null,
    sourceViewId: null,
    bookmark: null,
    status: canonical.status === 'approved' ? 'APPROVED_CANONICAL' : 'MISSING_ASSET',
    renderClass: canonical.renderClass,
  };
  return { assetId: null, assetType: equipmentType, currentPng: null, currentViewId: null, sourcePng: null, sourceViewId: null, bookmark: null, status: 'MISSING_ASSET', renderClass: 'TODO_USER_APPROVAL' };
};

const sourceRef = (basename, role, notes = null, canonicalType = null) => {
  const source = sourceAssetFor(basename);
  const canonical = canonicalType ? canonicalAssetForType(canonicalType) : null;
  return { role, ...source, ...(canonical ? { assetId: canonical.id, assetType: canonical.type, currentPng: canonical.file, currentViewId: canonical.viewId, status: canonical.status === 'approved' ? 'APPROVED_CANONICAL' : source.status } : {}), notes };
};
const machineBasenameFor = (id) => {
  const rules = [
    [/lat_pulldown/, 'lat-pulldown-machine-rear-three-quarter-generated.png'],
    [/assisted_(pull_up|dip)/, '21_assisted_pullup_dip_machine.png'],
    [/(pec_deck|machine_chest_fly)/, '20_pec_deck.png'],
    [/seated_row/, '22_seated_row_machine.png'],
    [/leg_press/, '16_leg_press.png'],
    [/leg_extension/, '17_leg_extension.png'],
    [/(seated_leg_curl|single_leg_seated_curl)/, '18_seated_leg_curl.png'],
    [/(lying_leg_curl|single_leg_lying_curl)/, '19_lying_leg_curl.png'],
    [/glute_ham_raise/, '15_ghd_nordic_support.png'],
    [/hip_abduction|hip_adduction/, '23_hip_abduction_adduction_machine.png'],
    [/standing_calf_raise/, '24_standing_calf_raise_machine.png'],
    [/seated_calf_raise/, '25_seated_calf_raise_machine.png'],
    [/captains_chair/, '13_captains_chair.png'],
    [/roman_chair/, '14_roman_chair.png'],
    [/plate_loaded/, '28_plate_loaded_press_frame.png'],
  ];
  return rules.find(([pattern]) => pattern.test(id))?.[1] ?? null;
};
const cableAttachmentFor = (id) => {
  if (id.includes('rope')) return 'B_rope_attachment.png';
  if (id.includes('straight_bar')) return 'C_straight_bar.png';
  if (id.includes('wide_grip') || id.includes('lat_pulldown')) return 'D_wide_lat_bar.png';
  if (id.includes('single_arm') || id.includes('external_rotation')) return 'A_single_d_handle.png';
  if (id.includes('kickback') || id.includes('hip_abduction') || id.includes('hip_adduction') || id.includes('leg_curl')) return 'F_ankle_cuff.png';
  return null;
};

const equipmentResolutionFor = (exercise) => {
  const id = exercise.id;
  if (exercise.equipment === 'bodyweight') return [{ role: 'none', equipmentType: 'bodyweight', status: 'NOT_REQUIRED', assetId: null, currentPng: null, currentViewId: null, sourcePng: null, sourceViewId: null, renderClass: 'none', notes: 'Bodyweight exercises do not require a canonical equipment asset.' }];
  if (exercise.equipment === 'cable') {
    const attachmentBasename = cableAttachmentFor(id);
    return [
      sourceRef('02_functional_trainer.png', 'functional_trainer', 'Functional trainer source exists; promotion and final approval are pending.'),
      sourceRef('09_cable_attachment_set.png', 'attachment_set', 'Attachment selection remains TODO; cable path is a separate placement decision.'),
      ...(attachmentBasename ? [sourceRef(`cable_attachments/${attachmentBasename}`, 'attachment_candidate', 'Candidate selected from explicit exercise ID token; user approval still required.')] : []),
    ];
  }
  if (exercise.equipment === 'machine') {
    const basename = machineBasenameFor(id);
    const canonicalType = basename?.startsWith('lat-pulldown-machine-') ? 'lat_pulldown_machine' : null;
    return basename ? [sourceRef(basename, 'specific_machine', 'Specific machine source match; promotion/final approval is pending unless an approved canonical exists.', canonicalType)] : [{ role: 'specific_machine', equipmentType: 'machine_specific', assetId: null, assetType: 'machine_specific', currentPng: null, currentViewId: null, sourcePng: null, sourceViewId: null, status: 'MISSING_ASSET', renderClass: 'TODO_USER_APPROVAL', notes: 'No exact specific-machine source/bookmark match was found for this exercise.' }];
  }
  if (exercise.equipment === 'other') {
    if (id.includes('ab_wheel')) return [sourceRef('26_ab_wheel.png', 'concrete_asset')];
    if (id.includes('gripper')) return [sourceRef('27_hand_gripper.png', 'concrete_asset')];
    if (id.includes('roman_chair')) return [sourceRef('14_roman_chair.png', 'concrete_asset')];
    return [{ role: 'concrete_asset', equipmentType: 'other', assetId: null, assetType: 'other', currentPng: null, currentViewId: null, sourcePng: null, sourceViewId: null, status: 'MISSING_ASSET', renderClass: 'TODO_USER_APPROVAL', notes: 'Concrete equipment identity is unresolved.' }];
  }
  if (exercise.equipment === 'band') return [sourceRef('06_resistance_band.png', 'resistance_band')];
  if (exercise.equipment === 'plate') return [sourceRef('07_weight_plate.png', 'weight_plate')];
  if (exercise.equipment === 'smith_machine') return [sourceRef('03_smith_machine.png', 'smith_machine')];
  if (exercise.equipment === 'barbell') {
    const result = [canonicalRawEquipment('barbell')];
    if (id.includes('ez_')) result.push(sourceRef('08_ez_curl_bar.png', 'bar_variant', 'EZ bar source exists but is not promoted.'));
    if (id.includes('landmine')) result.push(sourceRef('11_landmine_base.png', 'support_accessory', 'Landmine base source exists but is not promoted.'));
    return result;
  }
  if (exercise.equipment === 'dumbbell') return [canonicalRawEquipment('dumbbell')];
  if (exercise.equipment === 'kettlebell') return [canonicalRawEquipment('kettlebell')];
  return [{ role: 'equipment', equipmentType: exercise.equipment, assetId: null, assetType: exercise.equipment, currentPng: null, currentViewId: null, sourcePng: null, sourceViewId: null, status: 'MISSING_ASSET', renderClass: 'TODO_USER_APPROVAL', notes: 'No equipment resolution rule exists.' }];
};

const renderStrategyFor = (exercise, resolution) => {
  if (exercise.equipment === 'bodyweight') return 'none';
  if (exercise.equipment === 'cable') return `cable.functional_trainer.${cableAttachmentFor(exercise.id)?.replace('.png', '') ?? 'attachment_TODO'}.cable_path_TODO`;
  const primary = resolution.find((item) => item.role !== 'support_accessory' && item.role !== 'bar_variant' && item.status !== 'NOT_REQUIRED');
  return `${exercise.equipment}.${primary?.assetId ?? primary?.sourcePng?.replace(/^source\//, '') ?? 'asset_TODO'}`;
};
const sceneFiles = fs.readdirSync(path.join(root, 'model_image/exercise-images/scenes'))
  .filter((file) => file.endsWith('.scene.json'));
const scenesByExerciseId = new Map();
for (const file of sceneFiles) {
  const scene = readJson(`model_image/exercise-images/scenes/${file}`);
  scenesByExerciseId.set(scene.exerciseId, {
    file: `model_image/exercise-images/scenes/${file}`,
    catalogArchetypeId: scene.catalogArchetypeId ?? null,
    variantTags: scene.variantTags ?? [],
    equipment: scene.equipment ?? [],
    hasVisualContract: Boolean(scene.visualContract),
    hasGenerationContract: Boolean(scene.generationContract),
    hasAnatomyReview: Boolean(scene.anatomyReview),
  });
}

const stableStringify = (value) => JSON.stringify(value, Object.keys(value ?? {}).sort());
const unique = (values) => [...new Set(values)];
const sorted = (values) => [...values].sort((a, b) => String(a).localeCompare(String(b)));
const countBy = (rows, selector) => Object.fromEntries(
  [...rows.reduce((map, row) => {
    const key = selector(row);
    map.set(key, (map.get(key) ?? 0) + 1);
    return map;
  }, new Map())].sort(([a], [b]) => String(a).localeCompare(String(b))),
);

const expandGroup = (groupId) => {
  const groupLayers = groupToLayers[groupId];
  if (!groupLayers) return { groupId, layerIds: [], missing: true };
  const missingLayers = groupLayers.filter((layerId) => !layerById.has(layerId));
  return { groupId, layerIds: [...groupLayers], missing: missingLayers.length > 0, missingLayers };
};

const muscleDecisionFor = (exercise) => {
  const primary = expandGroup(exercise.primarySubPart);
  const secondary = (exercise.secondarySubParts ?? []).map(expandGroup);
  const primaryLayerIds = unique(primary.layerIds);
  const secondaryLayerIdsBeforeSubtraction = unique(secondary.flatMap((group) => group.layerIds));
  const secondaryLayerIds = secondaryLayerIdsBeforeSubtraction.filter((id) => !primaryLayerIds.includes(id));
  const missingGroups = [primary, ...secondary].filter((group) => group.missing);
  const deepOrLandmark = [...primaryLayerIds, ...secondaryLayerIds]
    .filter((id) => ['deep_projection', 'anatomical_landmark'].includes(layerById.get(id)?.kind));
  return {
    primaryGroup: exercise.primarySubPart,
    secondaryGroups: exercise.secondarySubParts ?? [],
    primaryLayerIds,
    secondaryLayerIds,
    overlapResolved: secondaryLayerIdsBeforeSubtraction.filter((id) => primaryLayerIds.includes(id)),
    missingGroups,
    deepOrLandmarkLayers: unique(deepOrLandmark),
  };
};

const visualVariantFor = (exercise) => {
  const variant = byExerciseId.get(exercise.id)?.variant ?? {};
  return Object.fromEntries(Object.entries(variant)
    .filter(([key]) => !['equipment', 'laterality', 'legacyTokens', 'canonicalAlias', 'loadState'].includes(key))
    .sort(([a], [b]) => a.localeCompare(b)));
};
const groupingKeyFor = (exercise) => {
  const mapping = byExerciseId.get(exercise.id);
  const resolution = equipmentResolutionFor(exercise);
  return JSON.stringify({
    familyId: mapping?.familyId ?? 'MISSING_FAMILY_MAPPING',
    movementPattern: exercise.movementPattern,
    motionType: exercise.motionType,
    renderStrategy: renderStrategyFor(exercise, resolution),
    laterality: exercise.laterality,
    visualVariant: visualVariantFor(exercise),
  });
};

const groups = new Map();
for (const exercise of fitness.exercises) {
  const key = groupingKeyFor(exercise);
  const list = groups.get(key) ?? [];
  list.push(exercise);
  groups.set(key, list);
}

const candidateGroups = [...groups.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([groupingKey, exercises]) => {
  const parsedKey = JSON.parse(groupingKey);
  const familyId = parsedKey.familyId;
  const familyVariants = exercises.map((exercise) => {
    const mapping = byExerciseId.get(exercise.id);
    return { exerciseId: exercise.id, nameKo: exercise.nameKo, canonicalVariantKey: mapping?.canonicalVariantKey ?? null, visualVariantKey: mapping?.visualVariantKey ?? null, variant: mapping?.variant ?? null };
  });
  const resolutions = exercises.flatMap(equipmentResolutionFor);
  const resolutionMap = new Map();
  for (const item of resolutions) {
    const key = JSON.stringify([item.role, item.assetId, item.sourcePng, item.status]);
    if (!resolutionMap.has(key)) resolutionMap.set(key, item);
  }
  const resolvedEquipment = [...resolutionMap.values()];
  const sceneEvidence = exercises.map((exercise) => ({ exerciseId: exercise.id, scene: scenesByExerciseId.get(exercise.id) ?? null })).filter((entry) => entry.scene);
  const equipmentViews = resolvedEquipment.map((item) => ({ role: item.role, assetId: item.assetId, currentViewId: item.currentViewId, sourceViewId: item.sourceViewId, status: item.status }));
  const slugVariant = Object.entries(parsedKey.visualVariant).map(([key, value]) => `${key}_${String(value)}`).join('_') || 'base';
  const archetypeIdCandidate = `candidate.${familyId}.${parsedKey.movementPattern}.${parsedKey.motionType}.${parsedKey.renderStrategy}.${parsedKey.laterality}.${slugVariant}`.replace(/[^a-zA-Z0-9._-]+/g, '_');
  return {
    archetypeIdCandidate,
    groupingKey: parsedKey,
    exerciseCount: exercises.length,
    exercises: exercises.map((exercise) => ({ id: exercise.id, nameKo: exercise.nameKo, nameEn: exercise.nameEn })),
    familyAndMajorVariants: [{ familyId, exerciseCount: familyVariants.length, majorVariants: familyVariants }],
    requiredEquipment: resolvedEquipment,
    requiredEquipmentViews: equipmentViews,
    cameraViewTodo: 'TODO_USER_APPROVAL: choose camera/view after support, contact, and equipment review.',
    aPoseTodo: 'TODO_USER_APPROVAL: define A representative pose; do not infer from name.',
    bPoseTodo: 'TODO_USER_APPROVAL: define B as edit_from_A or approve a static-only representation.',
    lockedJointsTodo: 'CODEX_DRAFT_AFTER_POSE_APPROVAL: derive locked joints and pixel anchors from approved A.',
    animatedJointsTodo: 'CODEX_DRAFT_AFTER_POSE_APPROVAL: derive only joints that change between approved A and B.',
    placementRecipeTodo: 'CODEX_DRAFT_AFTER_EQUIPMENT_APPROVAL: derive asset-local anchor transforms, z-order, and cable path.',
    visualVariantDimensions: Object.keys(parsedKey.visualVariant),
    currentSceneEvidence: sceneEvidence,
  };
});

const allMuscleDecisions = fitness.exercises.map((exercise) => ({ exercise, decision: muscleDecisionFor(exercise) }));
const missingMuscleReferences = allMuscleDecisions.filter(({ decision }) => decision.missingGroups.length).map(({ exercise, decision }) => ({
  exerciseId: exercise.id,
  missingGroups: decision.missingGroups,
}));
const broadMuscleReview = allMuscleDecisions.filter(({ exercise }) => (
  exercise.primarySubPart.startsWith('overall_') || exercise.primarySubPart === 'core_stability'
)).map(({ exercise, decision }) => ({
  exerciseId: exercise.id,
  primaryGroup: decision.primaryGroup,
  primaryLayerIds: decision.primaryLayerIds,
  reason: 'Product group is intentionally broad; confirm that the exercise image should not narrow it before production approval.',
}));
const deepLayerReview = allMuscleDecisions.filter(({ decision }) => decision.deepOrLandmarkLayers.length).map(({ exercise, decision }) => ({
  exerciseId: exercise.id,
  layers: decision.deepOrLandmarkLayers,
  reason: 'Use deep projection/landmark rendering rules; do not paint these as ordinary surface muscle.',
}));
const overlapResolution = allMuscleDecisions.filter(({ decision }) => decision.overlapResolved.length).map(({ exercise, decision }) => ({
  exerciseId: exercise.id,
  resolvedLayerIds: decision.overlapResolved,
  reason: 'Secondary layer set must subtract primary layers per the existing muscle-layers contract.',
}));

const allEquipmentResolutions = fitness.exercises.flatMap((exercise) => equipmentResolutionFor(exercise).map((resolution) => ({ exercise, resolution })));
const equipmentDecisionMap = new Map();
for (const { exercise, resolution } of allEquipmentResolutions) {
  const key = JSON.stringify([resolution.role, resolution.assetId, resolution.assetType, resolution.sourcePng, resolution.status]);
  const row = equipmentDecisionMap.get(key) ?? {
    assetId: resolution.assetId ?? 'TODO_USER_APPROVAL',
    assetType: resolution.assetType ?? resolution.equipmentType ?? 'TODO_USER_APPROVAL',
    currentPng: resolution.currentPng ?? 'NOT_APPLICABLE',
    currentViewId: resolution.currentViewId ?? resolution.sourceViewId ?? 'TODO_USER_APPROVAL',
    sourcePng: resolution.sourcePng ?? null,
    sourceViewId: resolution.sourceViewId ?? null,
    bookmark: resolution.bookmark ?? null,
    status: resolution.status,
    renderClass: resolution.renderClass,
    exerciseCount: 0,
    roles: [],
    notes: resolution.notes ?? null,
  };
  row.exerciseCount += 1;
  row.roles = unique([...row.roles, resolution.role]);
  equipmentDecisionMap.set(key, row);
}
const equipmentDecisions = [...equipmentDecisionMap.values()].sort((a, b) => `${a.assetType}|${a.assetId}`.localeCompare(`${b.assetType}|${b.assetId}`)).map((row) => ({
  ...row,
  needsAdditionalViewOrVariant: row.status === 'APPROVED_CANONICAL'
    ? 'TODO_USER_DECISION: verify this current view across every grouped support/pose context.'
    : row.status === 'NOT_YET_PROMOTED'
      ? 'NOT_YET_PROMOTED: source/bookmark exists; promote only after user approval.'
      : row.status === 'NOT_REQUIRED'
        ? 'NO_CANONICAL_EQUIPMENT_REQUIRED'
        : 'MISSING_ASSET: no matching source/bookmark was found.',
}))
  .filter((row) => row.status !== 'NOT_REQUIRED');
const bodyweightDecision = { assetId: null, assetType: 'bodyweight', currentPng: 'NOT_APPLICABLE', currentViewId: 'NOT_APPLICABLE', sourcePng: null, sourceViewId: null, status: 'NOT_REQUIRED', renderClass: 'none', exerciseCount: fitness.exercises.filter((exercise) => exercise.equipment === 'bodyweight').length, roles: ['none'], needsAdditionalViewOrVariant: 'NO_CANONICAL_EQUIPMENT_REQUIRED' };
equipmentDecisions.unshift(bodyweightDecision);
const canonicalEquipmentCatalog = equipmentCatalog.assets.map((asset) => ({ assetId: asset.id, assetType: asset.type, currentPng: asset.file, currentViewId: asset.viewId, status: asset.status, renderClass: asset.renderClass }));

const overrideCandidates = [];

const pack = {
  packVersion: 1,
  approvalState: 'EQUIPMENT_APPROVED_ARCHETYPE_PENDING',
  source: {
    branch: 'feat/fitness-image-3d-pipeline',
    commit: '520d88157983cadc301f6cc7497b27c93690083d',
    baselineExerciseCount: fitness.exercises.length,
    familyVariantContract: 'model_image/family/data/exercise-family-mapping-v1.json',
    groupingRule: 'familyId|movementPattern|motionType|renderStrategy|laterality|visualVariant(excluding loadState)',
    note: 'Equipment approval has been applied to equipment-catalog.json. Archetype, deterministic mapping, identity, and override production files remain unchanged.',
  },
  auditSnapshot: {
    exerciseCount: fitness.exercises.length,
    candidateCount: candidateGroups.length,
    sceneCount: sceneFiles.length,
    coveredExerciseCount: [...scenesByExerciseId.keys()].filter((id) => byExerciseId.has(id)).length,
    missingSceneCount: fitness.exercises.filter((exercise) => !scenesByExerciseId.has(exercise.id)).length,
    target: 'READY 100%, MISSING_* 0',
  },
  canonicalEquipmentCatalog,
  equipmentDecisions,
  archetypeDecisions: candidateGroups,
  exerciseOverrideCandidates: overrideCandidates,
  muscleMappingExceptions: {
    missingOrInvalidReferences: missingMuscleReferences,
    broadProductGroupReview: broadMuscleReview,
    deepProjectionOrLandmarkReview: deepLayerReview,
    primarySecondaryOverlapResolvedByExistingRule: overlapResolution,
    contract: 'Fitness_Weight primarySubPart/secondarySubParts -> muscle-layers exerciseGroups -> layer IDs; secondary subtracts primary.',
  },
  existingImageIdentityEvidence: {
    schemaVersion: identity.schemaVersion,
    familyDefaultCount: identity.familyDefaults.length,
    exactVisualVariantCount: identity.imageVariants.length,
    note: 'Existing identity mappings are evidence only and remain unchanged until approval.',
  },
};

const outputJson = path.join(root, 'model_image/archetypes/DECISION_PACK.json');
const outputMarkdown = path.join(root, 'model_image/archetypes/DECISION_PACK.md');
fs.writeFileSync(outputJson, JSON.stringify(pack, null, 2) + '\n');

const md = [];
md.push('# Refined Production Image Decision Pack');
md.push('');
md.push(`- 상태: **${pack.approvalState}**`);
md.push(`- 기준: \`${pack.source.branch}\` / \`${pack.source.commit.slice(0, 7)}\``);
md.push(`- 전체 운동: **${pack.auditSnapshot.exerciseCount}**, refined 후보: **${pack.auditSnapshot.candidateCount}**`);
md.push('- Family-first grouping이며 loadState는 grouping key에서 제외했다.');
md.push('- Equipment approval은 반영되었고, archetype camera/A/B 및 나머지 production JSON은 아직 변경하지 않았다.');
md.push('');
md.push('## A. Canonical equipment / additional views');
md.push('');
md.push('| asset/type | current PNG | viewId | status | source/bookmark | decision |');
md.push('|---|---|---|---|---|---|');
for (const row of [...canonicalEquipmentCatalog, ...equipmentDecisions]) {
  const source = row.sourcePng ?? row.bookmark?.file ?? '-';
  md.push(`| ${row.assetId ?? '-'} / ${row.assetType} | ${row.currentPng} | ${row.currentViewId} | ${row.status} / ${row.renderClass} | ${source} | ${row.needsAdditionalViewOrVariant ?? '-'} |`);
}
md.push('');
md.push('## B. Refined archetype list');
md.push('');
const byFamilyForMarkdown = new Map();
for (const candidate of candidateGroups) {
  const familyId = candidate.groupingKey.familyId;
  const list = byFamilyForMarkdown.get(familyId) ?? [];
  list.push(candidate);
  byFamilyForMarkdown.set(familyId, list);
}
for (const [familyId, candidates] of [...byFamilyForMarkdown.entries()].sort(([a], [b]) => a.localeCompare(b))) {
  md.push(`### \`${familyId}\` (${candidates.reduce((sum, candidate) => sum + candidate.exerciseCount, 0)} exercises / ${candidates.length} candidates)`);
  for (const candidate of candidates) md.push(`- \`${candidate.archetypeIdCandidate}\` — ${candidate.exerciseCount}개; variant=${JSON.stringify(candidate.groupingKey.visualVariant)}; equipment=${candidate.requiredEquipment.map((item) => item.assetId ?? item.assetType ?? item.role).join(', ')}`);
  md.push('');
}
md.push('## C. Camera / A / B');
md.push('');
md.push(`모든 ${candidateGroups.length}개 refined archetype은 camera/view, A pose, B pose를 아직 확정하지 않았다. 각 후보의 JSON 항목에 cameraViewTodo/aPoseTodo/bPoseTodo가 있고, locked/animated joints 및 placement는 pose/equipment 승인 뒤 Codex 초안 계산 대상으로 표시했다.`);
md.push('');
md.push('| archetype candidate | camera | A | B |');
md.push('|---|---|---|---|');
for (const candidate of candidateGroups) md.push(`| ${candidate.archetypeIdCandidate} | TODO | TODO | TODO |`);
md.push('');
md.push('## D. True exercise-specific overrides');
md.push('');
md.push(`**${overrideCandidates.length}개**. Family-first + visual variant 분리로 현재는 coarse Family 충돌만으로 override를 만들지 않으며, 단일 운동은 별도 archetype 후보로 남긴다.`);
md.push('');
md.push('## E. Muscle mapping exceptions');
md.push('');
md.push(`- invalid/missing group or layer reference: **${missingMuscleReferences.length}**`);
md.push(`- broad product group review: **${broadMuscleReview.length}**`);
md.push(`- deep projection/landmark review: **${deepLayerReview.length}**`);
md.push(`- primary-secondary overlap resolved by existing subtraction: **${overlapResolution.length}**`);
md.push('');
md.push('## Approval boundary');
md.push('');
md.push('Equipment 승인으로 equipment-catalog.json만 갱신했다. archetype-registry.json, deterministic-mapping.json, exercise-overrides.json, exercise-image-identity-v1.json은 아직 갱신하지 않는다. 실제 image generation API/adapter도 구현하지 않는다.');
md.push('');
md.push(`현재 scene coverage는 ${pack.auditSnapshot.coveredExerciseCount}/${pack.auditSnapshot.exerciseCount}, missing scene은 ${pack.auditSnapshot.missingSceneCount}개다. READY 100%는 아직 아니다.`);
md.push('');
fs.writeFileSync(outputMarkdown, md.join('\n') + '\n');
console.log(JSON.stringify({ outputJson, outputMarkdown, candidateCount: candidateGroups.length, overrideCandidateCount: overrideCandidates.length, missingMuscleReferenceCount: missingMuscleReferences.length }, null, 2));













