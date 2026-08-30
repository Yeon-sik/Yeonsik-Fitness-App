import { slugFromExerciseId } from "./pipeline-contract.mjs";

export const RESOLVED_METADATA_FIELDS = Object.freeze([
  "supportMode",
  "bodyOrientation",
  "equipmentKinematics",
  "gripVariant",
  "canonicalView",
]);

export const CANDIDATE_KEY_FIELDS = Object.freeze([
  "movementPattern",
  "motionType",
  "equipmentKinematics",
  "laterality",
]);

const MISSING = Symbol("missing");

export function isPresent(value) {
  return value !== null && value !== undefined && value !== "";
}

function firstPresent(...values) {
  return values.find((value) => isPresent(value));
}

function archetypeDefault(archetype, field) {
  if (isPresent(archetype?.metadata?.[field])) return archetype.metadata[field];
  return archetype?.[field];
}

function cameraView(archetype) {
  return firstPresent(
    archetype?.camera?.viewId,
    archetype?.camera?.canonicalView,
    archetype?.camera?.view,
  );
}

function exerciseValue(exercise, field) {
  if (field === "equipmentKinematics") return firstPresent(exercise?.equipmentKinematics, exercise?.resistanceType);
  if (field === "equipmentType") return firstPresent(exercise?.equipmentType, exercise?.equipment);
  return exercise?.[field];
}

function ruleValue(rule) {
  return rule?.values ?? rule?.metadata ?? rule?.defaults ?? {};
}

function ruleConditions(rule) {
  return rule?.when ?? rule?.match ?? rule?.if ?? {};
}

function matchesCondition(actual, expected) {
  if (Array.isArray(expected)) return expected.includes(actual);
  return actual === expected;
}

function matchesRule(exercise, rule) {
  return Object.entries(ruleConditions(rule)).every(([field, expected]) => {
    const actual = field === "equipment" ? exerciseValue(exercise, "equipmentType") : exerciseValue(exercise, field);
    return matchesCondition(actual, expected);
  });
}

function rulesFromMapping(mapping) {
  const rules = [
    ...(Array.isArray(mapping?.rules) ? mapping.rules : []),
    ...(Array.isArray(mapping?.mappings) ? mapping.mappings : []),
  ];
  for (const [exerciseId, value] of Object.entries(mapping?.byExerciseId ?? {})) {
    rules.push({ id: `exercise:${exerciseId}`, when: { id: exerciseId }, values: value });
  }
  return rules;
}

export function findDeterministicMapping(exercise, mapping = {}) {
  const candidates = rulesFromMapping(mapping)
    .map((rule, index) => ({
      rule,
      index,
      specificity: Object.keys(ruleConditions(rule)).length,
      priority: Number.isFinite(rule.priority) ? rule.priority : 0,
    }))
    .filter(({ rule }) => matchesRule(exercise, rule))
    .sort((left, right) => right.specificity - left.specificity || right.priority - left.priority || left.index - right.index);

  if (candidates.length === 0) return { rule: null, values: {}, conflicts: [] };

  const best = candidates.filter((candidate) =>
    candidate.specificity === candidates[0].specificity && candidate.priority === candidates[0].priority,
  );
  const values = {};
  const conflicts = new Set();
  const fields = new Set(best.flatMap(({ rule }) => Object.keys(ruleValue(rule))));
  for (const field of fields) {
    const defined = best
      .map(({ rule }) => ruleValue(rule)[field])
      .filter(isPresent);
    if (defined.length === 0) continue;
    const serialized = [...new Set(defined.map((value) => JSON.stringify(value)))];
    if (serialized.length > 1) conflicts.add(field);
    else values[field] = defined[0];
  }

  return {
    rule: best[0].rule,
    values,
    conflicts: [...conflicts],
    requiresExerciseOverride: best.some(({ rule }) => rule.requiresExerciseOverride === true),
  };
}

export function resolveArchetypeId({ exercise, override, deterministic }) {
  return firstPresent(
    override?.archetypeId,
    exercise?.archetypeId,
    deterministic?.rule?.archetypeId,
    deterministic?.values?.archetypeId,
  ) ?? null;
}

export function resolveExerciseMetadata({ exercise, override = {}, archetype = null, deterministic = {} }) {
  const metadata = {};
  const sources = {};
  const missingFields = [];
  const conflicts = new Set(
    (deterministic.conflicts ?? []).filter((field) => RESOLVED_METADATA_FIELDS.includes(field)),
  );

  for (const field of RESOLVED_METADATA_FIELDS) {
    let value = MISSING;
    let source = null;

    if (isPresent(override[field])) {
      value = override[field];
      source = "exercise_override";
    } else if (field === "canonicalView" && isPresent(cameraView(archetype))) {
      // The archetype camera is the canonical view default. Keeping this
      // ahead of a duplicate metadata.canonicalView avoids two defaults that
      // can silently diverge.
      value = cameraView(archetype);
      source = "archetype_camera";
    } else if (isPresent(archetypeDefault(archetype, field))) {
      value = archetypeDefault(archetype, field);
      source = "archetype_default";
    } else if (!conflicts.has(field) && isPresent(deterministic.values?.[field])) {
      value = deterministic.values[field];
      source = "deterministic_mapping";
    } else if (field === "equipmentKinematics" && isPresent(exerciseValue(exercise, field))) {
      // resistanceType/equipmentKinematics is an explicit source-owned base
      // field in Fitness_Weight, unlike the nullable reviewed fields above.
      value = exerciseValue(exercise, field);
      source = "source_catalog";
    }

    if (value === MISSING) {
      metadata[field] = null;
      sources[field] = null;
      missingFields.push(field);
    } else {
      metadata[field] = value;
      sources[field] = source;
    }
  }

  return {
    metadata,
    sources,
    missingFields,
    conflicts: [...conflicts],
    canonicalViewFromCamera: sources.canonicalView === "archetype_camera",
  };
}

function clone(value) {
  return value === undefined ? undefined : JSON.parse(JSON.stringify(value));
}

function recipeFrames(archetype) {
  const recipe = archetype?.placementRecipe
    ?? archetype?.equipmentPlacements
    ?? archetype?.placementDefaults
    ?? {};
  return {
    A: recipe.frames?.A ?? recipe.A,
    B: recipe.frames?.B ?? recipe.B,
  };
}

function mergePlacementFrame(base, patch) {
  if (patch === undefined) return clone(base ?? []);
  if (!Array.isArray(patch)) return null;
  if (patch.length === 0) return [];

  const merged = clone(base ?? []);
  for (const [index, partial] of patch.entries()) {
    if (!partial || typeof partial !== "object" || Array.isArray(partial)) return null;
    const targetIndex = isPresent(partial.instanceId)
      ? merged.findIndex((item) => item?.instanceId === partial.instanceId)
      : index;
    if (targetIndex >= 0 && targetIndex < merged.length) merged[targetIndex] = { ...merged[targetIndex], ...clone(partial) };
    else merged.push(clone(partial));
  }
  return merged;
}

export function resolveEquipmentPlacements({ exercise, override = {}, archetype = null, deterministic = {} }) {
  const recipe = recipeFrames(archetype);
  const overrideFrames = override.equipmentPlacements ?? override.placements;
  const frames = {
    A: mergePlacementFrame(recipe.A, overrideFrames?.A),
    B: mergePlacementFrame(recipe.B, overrideFrames?.B),
  };
  const invalid = ["A", "B"].filter((frameId) => frames[frameId] === null);
  const hasCompleteRecipe = frames.A !== undefined && frames.B !== undefined;
  const bodyweight = [exercise?.equipmentType, exercise?.equipment].includes("bodyweight");

  return {
    frames: {
      A: frames.A ?? [],
      B: frames.B ?? [],
    },
    hasAnyRecipe: frames.A !== undefined || frames.B !== undefined,
    invalid,
    missingRecipe: !bodyweight && !hasCompleteRecipe,
    source: overrideFrames ? "exercise_override" : (hasCompleteRecipe ? "archetype_default" : "none"),
  };
}

export function buildArchetypeCandidates(exercises, mapping = {}) {
  const fields = Array.isArray(mapping.candidateKeyFields) && mapping.candidateKeyFields.length > 0
    ? mapping.candidateKeyFields
    : CANDIDATE_KEY_FIELDS;
  const candidates = new Map();
  for (const exercise of exercises ?? []) {
    const deterministic = findDeterministicMapping(exercise, mapping);
    const values = fields.map((field) => exerciseValue(exercise, field) ?? "MISSING");
    const key = values.join(".");
    const id = deterministic.rule?.archetypeId
      ?? deterministic.values?.archetypeId
      ?? exercise.archetypeId
      ?? `candidate.${slugFromExerciseId(key.replaceAll(".", "_"))}`;
    const existing = candidates.get(key) ?? {
      candidateId: id,
      key,
      fields: Object.fromEntries(fields.map((field, index) => [field, values[index]])),
      exerciseIds: [],
      requiresExerciseOverride: false,
    };
    existing.exerciseIds.push(exercise.id);
    existing.requiresExerciseOverride ||= deterministic.requiresExerciseOverride === true;
    candidates.set(key, existing);
  }
  return [...candidates.values()]
    .map((candidate) => ({ ...candidate, exerciseIds: [...candidate.exerciseIds].sort() }))
    .sort((left, right) => left.key.localeCompare(right.key));
}
