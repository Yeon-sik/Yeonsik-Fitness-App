#!/usr/bin/env node

import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import {
  ExerciseFamilyContractError,
  loadExerciseFamilyContract,
  validateExerciseFamilyContract,
} from '../../lib/exercise-family-contract.mjs';

function parseArguments(argv, repositoryRoot) {
  const options = {
    contractPath: path.join(repositoryRoot, 'EXERCISE_FAMILY_CATALOG_V1.yaml'),
    json: false,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === '--contract') options.contractPath = path.resolve(argv[++index]);
    else if (argument === '--json') options.json = true;
    else throw new Error(`Unexpected argument: ${argument}`);
  }
  return options;
}

export function validateContractFile(contractPath) {
  const contract = loadExerciseFamilyContract(contractPath);
  const errors = validateExerciseFamilyContract(contract);
  return {
    valid: errors.length === 0,
    contractVersion: contract.contractVersion,
    familyCount: Object.keys(contract.families ?? {}).length,
    approvedNewPresetCount: (contract.approvedNewPresets ?? []).length,
    canonicalAliasMergeCount: (contract.canonicalAliasMerges ?? []).length,
    errors,
  };
}

async function main() {
  const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const options = parseArguments(process.argv.slice(2), repositoryRoot);
  const report = validateContractFile(options.contractPath);
  if (options.json) console.log(JSON.stringify(report, null, 2));
  else {
    console.log(`CONTRACT\t${report.valid ? 'VALID' : 'INVALID'}`);
    console.log(`FAMILIES\t${report.familyCount}`);
    console.log(`APPROVED_NEW_PRESETS\t${report.approvedNewPresetCount}`);
    console.log(`CANONICAL_ALIAS_MERGES\t${report.canonicalAliasMergeCount}`);
    for (const error of report.errors) console.error(`ERROR\t${error}`);
  }
  if (!report.valid) process.exitCode = 2;
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
