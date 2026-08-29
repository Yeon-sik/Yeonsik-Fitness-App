import fs from 'node:fs';
import path from 'node:path';

const CONTRACT_VERSION = 1;
const EXPECTED_LOAD_STATES = [
  'bodyweight',
  'external_load',
  'added_weight',
  'assisted',
  'band_assisted',
  'band_resisted',
];
const EXPECTED_FALLBACK_ORDER = [
  'exact_visual_variant',
  'family_default',
  'placeholder',
];

export class ExerciseFamilyContractError extends Error {
  constructor(message, details = []) {
    super(message);
    this.name = 'ExerciseFamilyContractError';
    this.details = details;
  }
}

function fail(lineNumber, message) {
  throw new ExerciseFamilyContractError(`Unsupported contract YAML at line ${lineNumber}: ${message}`);
}

function isStructuralLine(content) {
  return content === '-' || /^-\s/.test(content) || /^[^:]+:\s*(?:.*)?$/.test(content);
}

function stripInlineComment(value) {
  // The normative contract currently contains no comments or quoted '#' values.
  // Rejecting rather than guessing keeps the loader fail-closed if that changes.
  if (value.includes('#')) {
    throw new ExerciseFamilyContractError('Inline comments and # values are not supported by the contract loader.');
  }
  return value.trim();
}

function parseScalar(raw, lineNumber) {
  const value = stripInlineComment(raw);
  if (value === '') return null;
  if (value === 'true') return true;
  if (value === 'false') return false;
  if (value === 'null') return null;
  if (/^-?(?:0|[1-9]\d*)(?:\.\d+)?$/.test(value)) return Number(value);
  if (/^["']/.test(value)) fail(lineNumber, 'quoted scalars are not supported');
  if (value.startsWith('[') || value.startsWith('{')) fail(lineNumber, 'inline collections are not supported');
  return value;
}

function splitKeyValue(content, lineNumber) {
  const separator = content.indexOf(':');
  if (separator <= 0) fail(lineNumber, 'expected a map entry with a colon');
  const key = content.slice(0, separator).trim();
  if (!/^[A-Za-z0-9_.-]+$/.test(key)) fail(lineNumber, `unsupported map key ${key}`);
  return { key, rawValue: content.slice(separator + 1).trim() };
}

function prepareLines(source) {
  const rawLines = source.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n').split('\n');
  const lines = [];
  for (let index = 0; index < rawLines.length; index += 1) {
    const raw = rawLines[index];
    if (raw.includes('\t')) fail(index + 1, 'tabs are not supported for indentation');
    if (raw.trim() === '') continue;
    const indent = raw.length - raw.trimStart().length;
    const content = raw.trim();
    if (content.startsWith('#')) fail(index + 1, 'comments are not supported');
    lines.push({ indent, content, lineNumber: index + 1 });
  }

  // The supplied contract uses folded plain scalar continuations for a few prose
  // fields. Join only continuation lines that cannot be structural YAML nodes.
  const prepared = [];
  for (const line of lines) {
    const previous = prepared.at(-1);
    if (
      previous
      && line.indent > previous.indent
      && !isStructuralLine(line.content)
      && (previous.content.includes(':') || previous.content.startsWith('- '))
      && !previous.content.endsWith(':')
    ) {
      previous.content = `${previous.content} ${stripInlineComment(line.content)}`;
      continue;
    }
    prepared.push({ ...line });
  }
  return prepared;
}

function parseBlock(lines, startIndex, indent) {
  const first = lines[startIndex];
  if (!first || first.indent !== indent) fail(first?.lineNumber ?? 0, `expected indentation ${indent}`);
  const list = first.content === '-' || first.content.startsWith('- ');
  const value = list ? [] : {};
  let index = startIndex;

  while (index < lines.length && lines[index].indent === indent) {
    const line = lines[index];
    if (list) {
      if (!(line.content === '-' || line.content.startsWith('- '))) break;
      const rest = line.content === '-' ? '' : line.content.slice(2).trim();
      if (rest === '') {
        const child = lines[index + 1];
        if (!child || child.indent <= indent) fail(line.lineNumber, 'empty list item has no child value');
        const parsed = parseBlock(lines, index + 1, child.indent);
        value.push(parsed.value);
        index = parsed.nextIndex;
        continue;
      }

      const mapItem = /^[A-Za-z0-9_.-]+:(?:\s|$)/.test(rest);
      if (mapItem) {
        const entry = splitKeyValue(rest, line.lineNumber);
        const item = {};
        if (entry.rawValue === '') {
          const child = lines[index + 1];
          if (!child || child.indent <= indent) fail(line.lineNumber, 'empty list map value has no child value');
          const parsed = parseBlock(lines, index + 1, child.indent);
          item[entry.key] = parsed.value;
          index = parsed.nextIndex;
        } else {
          item[entry.key] = parseScalar(entry.rawValue, line.lineNumber);
          index += 1;
        }

        if (index < lines.length && lines[index].indent > indent) {
          const child = lines[index];
          const parsed = parseBlock(lines, index, child.indent);
          if (!parsed.value || Array.isArray(parsed.value) || typeof parsed.value !== 'object') {
            fail(child.lineNumber, 'list map continuation must be a map');
          }
          for (const [key, childValue] of Object.entries(parsed.value)) {
            if (Object.prototype.hasOwnProperty.call(item, key)) fail(child.lineNumber, `duplicate key ${key}`);
            item[key] = childValue;
          }
          index = parsed.nextIndex;
        }
        value.push(item);
        continue;
      }

      value.push(parseScalar(rest, line.lineNumber));
      index += 1;
      continue;
    }

    if (line.content.startsWith('-')) break;
    const entry = splitKeyValue(line.content, line.lineNumber);
    if (Object.prototype.hasOwnProperty.call(value, entry.key)) fail(line.lineNumber, `duplicate key ${entry.key}`);
    if (entry.rawValue === '') {
      const child = lines[index + 1];
      const indentationlessList = child
        && child.indent === indent
        && (child.content === '-' || child.content.startsWith('- '));
      if (!child || (child.indent <= indent && !indentationlessList)) {
        value[entry.key] = null;
        index += 1;
      } else {
        const parsed = parseBlock(lines, index + 1, child.indent);
        value[entry.key] = parsed.value;
        index = parsed.nextIndex;
      }
    } else {
      value[entry.key] = parseScalar(entry.rawValue, line.lineNumber);
      index += 1;
    }
  }

  return { value, nextIndex: index };
}

export function parseExerciseFamilyContractYaml(source) {
  const lines = prepareLines(source);
  if (lines.length === 0) throw new ExerciseFamilyContractError('The contract is empty.');
  const parsed = parseBlock(lines, 0, lines[0].indent);
  if (parsed.nextIndex !== lines.length) {
    const line = lines[parsed.nextIndex];
    fail(line.lineNumber, 'unexpected trailing YAML node');
  }
  if (!parsed.value || Array.isArray(parsed.value) || typeof parsed.value !== 'object') {
    throw new ExerciseFamilyContractError('The contract root must be a map.');
  }
  return parsed.value;
}

export function loadExerciseFamilyContract(contractPath) {
  const absolutePath = path.resolve(contractPath);
  return parseExerciseFamilyContractYaml(fs.readFileSync(absolutePath, 'utf8'));
}

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function requireObject(value, label, errors) {
  if (!isObject(value)) errors.push(`${label} must be a map`);
  return isObject(value);
}

function requireArray(value, label, errors) {
  if (!Array.isArray(value)) errors.push(`${label} must be a list`);
  return Array.isArray(value);
}

function equalArray(actual, expected) {
  return Array.isArray(actual)
    && actual.length === expected.length
    && actual.every((value, index) => value === expected[index]);
}

function collectLegacyRuleDetails(contract) {
  const exactOwners = new Map();
  const regexEntries = [];
  for (const [familyId, family] of Object.entries(contract.families ?? {})) {
    const match = family?.legacyMatch;
    if (!isObject(match)) continue;
    for (const legacyId of match.exact ?? []) {
      if (exactOwners.has(legacyId)) {
        exactOwners.get(legacyId).push(familyId);
      } else {
        exactOwners.set(legacyId, [familyId]);
      }
    }
    for (const pattern of match.regex ?? []) regexEntries.push({ familyId, pattern });
  }
  return { exactOwners, regexEntries };
}

export function validateExerciseFamilyContract(contract) {
  const errors = [];
  if (!requireObject(contract, 'root', errors)) return errors;
  if (contract.contractVersion !== CONTRACT_VERSION) errors.push(`contractVersion must be ${CONTRACT_VERSION}`);
  if (contract.status !== 'APPROVED_FOR_IMPLEMENTATION') errors.push('status must be APPROVED_FOR_IMPLEMENTATION');

  const sourceBaseline = contract.sourceBaseline;
  if (requireObject(sourceBaseline, 'sourceBaseline', errors)) {
    if (sourceBaseline.baselineCatalog !== 'Fitness_Weight.json') errors.push('sourceBaseline.baselineCatalog must be Fitness_Weight.json');
    if (sourceBaseline.baselineExerciseCount !== 340) errors.push('sourceBaseline.baselineExerciseCount must be 340');
  }

  const authority = contract.authority;
  if (requireObject(authority, 'authority', errors) && authority.normative !== true) errors.push('authority.normative must be true');

  if (!requireArray(contract.loadStates, 'loadStates', errors) || !equalArray(contract.loadStates, EXPECTED_LOAD_STATES)) {
    errors.push(`loadStates must be exactly ${EXPECTED_LOAD_STATES.join(', ')}`);
  }
  const loadStateRules = contract.loadStateRules;
  if (requireObject(loadStateRules, 'loadStateRules', errors)) {
    if (loadStateRules.bodyweightAddedWeightZeroAllowed !== true) errors.push('bodyweightAddedWeightZeroAllowed must be true');
    if (!requireArray(loadStateRules.derivationForLegacyRows, 'loadStateRules.derivationForLegacyRows', errors)) {
      // The list error is already recorded.
    } else if (!loadStateRules.derivationForLegacyRows.some((rule) => rule.otherwise === 'external_load')) {
      errors.push('loadStateRules must define otherwise: external_load');
    }
  }

  const imageIdentity = contract.imageIdentity;
  if (requireObject(imageIdentity, 'imageIdentity', errors)) {
    if (!requireArray(imageIdentity.fallbackOrder, 'imageIdentity.fallbackOrder', errors)
      || !equalArray(imageIdentity.fallbackOrder, EXPECTED_FALLBACK_ORDER)) {
      errors.push(`imageIdentity.fallbackOrder must be exactly ${EXPECTED_FALLBACK_ORDER.join(', ')}`);
    }
  }

  const families = contract.families;
  if (!requireObject(families, 'families', errors)) return errors;
  const familyIds = new Set(Object.keys(families));
  for (const [familyId, family] of Object.entries(families)) {
    if (!requireObject(family, `families.${familyId}`, errors)) continue;
    for (const field of ['nameKo', 'nameEn', 'defaultUiPart']) {
      if (typeof family[field] !== 'string' || family[field].length === 0) errors.push(`families.${familyId}.${field} must be a non-empty string`);
    }
    // Approved V1 additions may intentionally have no legacy rule yet. They
    // remain selectable presets, but they must never absorb a legacy ID by
    // inference.
    if (family.legacyMatch !== null && family.legacyMatch !== undefined) {
      if (requireObject(family.legacyMatch, `families.${familyId}.legacyMatch`, errors)) {
        for (const exact of family.legacyMatch.exact ?? []) {
          if (typeof exact !== 'string' || exact.length === 0) errors.push(`families.${familyId}.legacyMatch.exact contains an invalid ID`);
        }
        for (const pattern of family.legacyMatch.regex ?? []) {
          if (typeof pattern !== 'string' || pattern.length === 0) {
            errors.push(`families.${familyId}.legacyMatch.regex contains an invalid pattern`);
            continue;
          }
          try {
            // Compile now so the audit cannot silently skip a malformed rule.
            // eslint-disable-next-line no-new
            new RegExp(pattern);
          } catch (error) {
            errors.push(`families.${familyId}.legacyMatch.regex ${pattern} is invalid: ${error.message}`);
          }
        }
      }
    }
    if (!requireArray(family.allowedVariantDimensions, `families.${familyId}.allowedVariantDimensions`, errors)) continue;
    if (!requireArray(family.allowedLoadStates, `families.${familyId}.allowedLoadStates`, errors)) continue;
    for (const loadState of family.allowedLoadStates) {
      if (!EXPECTED_LOAD_STATES.includes(loadState)) errors.push(`families.${familyId} references unknown loadState ${loadState}`);
    }
  }

  const ruleDetails = collectLegacyRuleDetails(contract);
  for (const [legacyId, owners] of ruleDetails.exactOwners.entries()) {
    if (owners.length > 1) errors.push(`exact legacy ID ${legacyId} belongs to multiple families: ${owners.join(', ')}`);
  }

  const aliasMerges = contract.canonicalAliasMerges;
  const aliasLegacyIds = new Set();
  if (requireArray(aliasMerges, 'canonicalAliasMerges', errors)) {
    for (const [index, merge] of aliasMerges.entries()) {
      if (!requireObject(merge, `canonicalAliasMerges[${index}]`, errors)) continue;
      if (!familyIds.has(merge.familyId)) errors.push(`canonicalAliasMerges[${index}] references unknown family ${merge.familyId}`);
      if (typeof merge.canonicalPresetId !== 'string') errors.push(`canonicalAliasMerges[${index}].canonicalPresetId is required`);
      if (!requireArray(merge.legacyIds, `canonicalAliasMerges[${index}].legacyIds`, errors)) continue;
      for (const legacyId of merge.legacyIds) {
        if (aliasLegacyIds.has(legacyId)) errors.push(`legacy ID ${legacyId} appears in multiple canonicalAliasMerges`);
        aliasLegacyIds.add(legacyId);
      }
    }
  }

  const searchAliases = contract.searchPresetAliases;
  if (requireArray(searchAliases, 'searchPresetAliases', errors)) {
    for (const [index, alias] of searchAliases.entries()) {
      if (!requireObject(alias, `searchPresetAliases[${index}]`, errors)) continue;
      if (!familyIds.has(alias.familyId)) errors.push(`searchPresetAliases[${index}] references unknown family ${alias.familyId}`);
      if (typeof alias.targetPreset !== 'string') errors.push(`searchPresetAliases[${index}].targetPreset is required`);
      if (!EXPECTED_LOAD_STATES.includes(alias.defaultLoadState)) errors.push(`searchPresetAliases[${index}] references unknown loadState ${alias.defaultLoadState}`);
    }
  }

  const approvedPresets = contract.approvedNewPresets;
  if (requireArray(approvedPresets, 'approvedNewPresets', errors)) {
    const presetIds = new Set();
    for (const [index, preset] of approvedPresets.entries()) {
      if (!requireObject(preset, `approvedNewPresets[${index}]`, errors)) continue;
      if (typeof preset.presetId !== 'string' || preset.presetId.length === 0) errors.push(`approvedNewPresets[${index}].presetId is required`);
      if (preset.presetId && presetIds.has(preset.presetId)) errors.push(`approvedNewPresets duplicates ${preset.presetId}`);
      if (preset.presetId) presetIds.add(preset.presetId);
      if (!familyIds.has(preset.familyId)) errors.push(`approvedNewPresets[${index}] references unknown family ${preset.familyId}`);
      if (!EXPECTED_LOAD_STATES.includes(preset.defaultLoadState)) errors.push(`approvedNewPresets[${index}] references unknown loadState ${preset.defaultLoadState}`);
    }
  }

  const requiredValidation = contract.requiredValidation;
  if (requireObject(requiredValidation, 'requiredValidation', errors)) {
    for (const field of [
      'mustMapExactlyOnce',
      'mustPreserveLegacyIds',
      'mustRejectFamilyInference',
      'mustValidateAliasTargets',
      'mustValidateNewPresetFamilyExists',
      'mustValidateImageFallback',
    ]) {
      if (requiredValidation[field] !== true) errors.push(`requiredValidation.${field} must be true`);
    }
  }
  return errors;
}

export function assertValidExerciseFamilyContract(contract) {
  const errors = validateExerciseFamilyContract(contract);
  if (errors.length > 0) throw new ExerciseFamilyContractError(`Exercise family contract validation failed (${errors.length} errors).`, errors);
  return contract;
}

function familyMatches(family, legacyId) {
  const match = family?.legacyMatch;
  if (!isObject(match)) return false;
  if ((match.exact ?? []).includes(legacyId)) return true;
  return (match.regex ?? []).some((pattern) => new RegExp(pattern).test(legacyId));
}

export function matchLegacyFamilyRules(contract, legacyId) {
  return Object.entries(contract.families ?? {})
    .filter(([, family]) => familyMatches(family, legacyId))
    .map(([familyId]) => familyId);
}

export function deriveLegacyLoadState(contract, exercise) {
  const rules = contract.loadStateRules?.derivationForLegacyRows ?? [];
  for (const rule of rules) {
    if (rule.whenExactId && rule.whenExactId === exercise.id) return rule.loadState;
    if (rule.whenEquipment && rule.whenEquipment === exercise.equipment) return rule.loadState;
    if (rule.otherwise) return rule.otherwise;
  }
  throw new ExerciseFamilyContractError(`No deterministic loadState rule for legacy exercise ${exercise.id}.`);
}

export function isBodyweightAddedWeightZeroAllowed(contract, addedWeightKg) {
  return contract.loadStateRules?.bodyweightAddedWeightZeroAllowed === true
    && Number.isFinite(addedWeightKg)
    && addedWeightKg === 0;
}

const TOKEN_FIELD_ORDER = [
  'angle',
  'barPosition',
  'carryStyle',
  'direction',
  'gripOrientation',
  'gripWidth',
  'laterality',
  'position',
  'rearFootElevation',
  'stance',
  'support',
  'technique',
  'travelMode',
];

function stableObject(value) {
  if (Array.isArray(value)) return value.map(stableObject);
  if (!isObject(value)) return value;
  return Object.fromEntries(Object.keys(value).sort().map((key) => [key, stableObject(value[key])]));
}

export function stableJson(value) {
  return JSON.stringify(stableObject(value));
}

function getLegacyTokens(legacyId) {
  return legacyId.split('_').filter(Boolean);
}

function normalizeVariantTokens(contract, legacyId) {
  const tokenRules = contract.variantTokenRules?.tokens ?? {};
  const tokenEntries = Object.entries(tokenRules).sort((left, right) => right[0].split('_').length - left[0].split('_').length || left[0].localeCompare(right[0]));
  const tokens = getLegacyTokens(legacyId);
  const consumed = new Set();
  const variant = {};
  const matchedTokens = [];

  for (const [token, dimensions] of tokenEntries) {
    const parts = token.split('_');
    for (let start = 0; start <= tokens.length - parts.length; start += 1) {
      if (parts.every((part, offset) => tokens[start + offset] === part)
        && parts.every((_, offset) => !consumed.has(start + offset))) {
        for (let offset = 0; offset < parts.length; offset += 1) consumed.add(start + offset);
        matchedTokens.push(token);
        for (const [dimension, value] of Object.entries(dimensions ?? {})) {
          if (variant[dimension] !== undefined && variant[dimension] !== value) {
            throw new ExerciseFamilyContractError(`Conflicting deterministic variant tokens for ${legacyId}: ${dimension}`);
          }
          variant[dimension] = value;
        }
        break;
      }
    }
  }

  const residualTokens = tokens.filter((_, index) => !consumed.has(index));
  return {
    variant,
    matchedTokens: matchedTokens.sort(),
    residualTokens,
  };
}

export function normalizeLegacyVariant(contract, exercise) {
  const tokenResult = normalizeVariantTokens(contract, exercise.id);
  const variant = {
    equipment: exercise.equipment ?? null,
    laterality: exercise.laterality ?? null,
    ...tokenResult.variant,
    legacyTokens: tokenResult.residualTokens,
  };
  return {
    variant,
    matchedTokens: tokenResult.matchedTokens,
    residualTokens: tokenResult.residualTokens,
    canonicalVariantKey: stableJson(variant),
    visualVariantKey: stableJson(variant),
  };
}

export function buildCanonicalAliasIndex(contract) {
  const index = new Map();
  for (const merge of contract.canonicalAliasMerges ?? []) {
    for (const legacyId of merge.legacyIds ?? []) {
      index.set(legacyId, {
        canonicalPresetId: merge.canonicalPresetId,
        familyId: merge.familyId,
        nameKo: merge.nameKo,
        aliases: merge.aliases ?? [],
        canonicalAliasMerge: merge.canonicalPresetId,
      });
    }
  }
  return index;
}

export function buildApprovedPresetIndex(contract) {
  return new Map((contract.approvedNewPresets ?? []).map((preset) => [preset.presetId, preset]));
}

export function mapLegacyExercise(contract, exercise) {
  const familyMatches = matchLegacyFamilyRules(contract, exercise.id);
  if (familyMatches.length !== 1) {
    return {
      exercise,
      familyMatches,
      status: familyMatches.length === 0 ? 'unmapped' : 'ambiguous',
    };
  }
  const familyId = familyMatches[0];
  const family = contract.families[familyId];
  const alias = buildCanonicalAliasIndex(contract).get(exercise.id);
  if (alias && alias.familyId !== familyId) {
    return {
      exercise,
      familyMatches,
      status: 'ambiguous',
      error: `canonical alias ${alias.canonicalPresetId} declares family ${alias.familyId}, but legacy rules select ${familyId}`,
    };
  }
  const variant = normalizeLegacyVariant(contract, exercise);
  return {
    status: 'mapped',
    legacyExerciseId: exercise.id,
    familyId,
    familyNameKo: family.nameKo,
    familyNameEn: family.nameEn,
    defaultUiPart: family.defaultUiPart,
    presetId: alias?.canonicalPresetId ?? exercise.id,
    canonicalPresetId: alias?.canonicalPresetId ?? exercise.id,
    presetNameKo: alias?.nameKo ?? exercise.nameKo,
    presetNameEn: exercise.nameEn,
    nameKo: exercise.nameKo,
    nameEn: exercise.nameEn,
    legacyEquipment: exercise.equipment,
    legacyRecordType: exercise.recordType,
    defaultLoadState: deriveLegacyLoadState(contract, exercise),
    variant: variant.variant,
    matchedVariantTokens: variant.matchedTokens,
    residualVariantTokens: variant.residualTokens,
    canonicalVariantKey: variant.canonicalVariantKey,
    visualVariantKey: variant.visualVariantKey,
    illustrationKey: null,
    compatibility: {
      legacyExerciseId: exercise.id,
      canonicalAliasMerge: alias?.canonicalAliasMerge ?? null,
    },
  };
}

export function buildLegacyExerciseMapping(contract, legacyExercises) {
  const aliasIndex = buildCanonicalAliasIndex(contract);
  const entries = legacyExercises.map((exercise) => mapLegacyExercise(contract, exercise));
  const unmapped = entries.filter((entry) => entry.status === 'unmapped');
  const ambiguous = entries.filter((entry) => entry.status === 'ambiguous');
  const mapped = entries.filter((entry) => entry.status === 'mapped');
  const aliasMergedLegacyIds = entries.filter((entry) => entry.status === 'mapped' && aliasIndex.has(entry.legacyExerciseId)).length;
  return {
    entries,
    mapped,
    unmapped,
    ambiguous,
    summary: {
      total: legacyExercises.length,
      mapped: mapped.length,
      unmapped: unmapped.length,
      ambiguous: ambiguous.length,
      canonicalAliasMergeGroups: (contract.canonicalAliasMerges ?? []).length,
      aliasMergedLegacyIds,
    },
  };
}

export function buildFamilyCatalogDocument(contract, legacyExercises, mapping) {
  const families = Object.fromEntries(Object.entries(contract.families).map(([familyId, family]) => [familyId, {
    familyId,
    nameKo: family.nameKo,
    nameEn: family.nameEn,
    defaultUiPart: family.defaultUiPart,
    allowedVariantDimensions: family.allowedVariantDimensions,
    allowedLoadStates: family.allowedLoadStates,
  }]));
  const approvedPresets = (contract.approvedNewPresets ?? []).map((preset) => ({
    ...preset,
    canonicalVariantKey: stableJson(preset.variant ?? {}),
    visualVariantKey: stableJson(preset.variant ?? {}),
    illustrationKey: null,
  }));
  return {
    schemaVersion: 1,
    contractVersion: contract.contractVersion,
    sourceContract: 'EXERCISE_FAMILY_CATALOG_V1.yaml',
    sourceBaseline: contract.sourceBaseline,
    loadStates: contract.loadStates,
    loadStateRules: contract.loadStateRules,
    performance: contract.performanceStats,
    imageIdentity: contract.imageIdentity,
    families,
    legacyExercises: mapping.entries,
    approvedPresets,
    canonicalAliasMerges: contract.canonicalAliasMerges,
    searchPresetAliases: contract.searchPresetAliases,
    deferredOutOfV1Scope: contract.deferredOutOfV1Scope,
    summary: mapping.summary,
  };
}

export function resolveImageIdentity(catalog, { familyId, visualVariantKey, legacyExerciseId } = {}) {
  const fallbackOrder = catalog?.imageIdentity?.fallbackOrder ?? EXPECTED_FALLBACK_ORDER;
  const exact = (catalog?.imageVariants ?? []).find((item) => item.familyId === familyId && item.visualVariantKey === visualVariantKey);
  const familyDefault = (catalog?.familyDefaults ?? []).find((item) => item.familyId === familyId);
  for (const step of fallbackOrder) {
    if (step === 'exact_visual_variant' && exact) return { ...exact, source: step };
    if (step === 'family_default' && familyDefault) return { ...familyDefault, source: step };
    if (step === 'placeholder') return { illustrationKey: 'placeholder', frames: [], source: step, legacyExerciseId: legacyExerciseId ?? null };
  }
  throw new ExerciseFamilyContractError('Image fallback order did not resolve a placeholder.');
}

export const EXERCISE_FAMILY_CONTRACT_CONSTANTS = Object.freeze({
  CONTRACT_VERSION,
  EXPECTED_LOAD_STATES,
  EXPECTED_FALLBACK_ORDER,
  TOKEN_FIELD_ORDER,
});
