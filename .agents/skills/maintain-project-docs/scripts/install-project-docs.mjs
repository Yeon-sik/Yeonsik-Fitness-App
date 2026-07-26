#!/usr/bin/env node

import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { validateProjectDocsConfig } from "./project-docs-config.mjs";

const SCRIPT_DIRECTORY = path.dirname(fileURLToPath(import.meta.url));
const SKILL_DIRECTORY = path.resolve(SCRIPT_DIRECTORY, "..");
const ASSET_DIRECTORY = path.join(SKILL_DIRECTORY, "assets");

const LOCALES = {
  en: {
    config: "project-docs.config.json",
    templates: [
      ["PROJECT_OVERVIEW_TEMPLATE.md", "docs/templates/PROJECT_OVERVIEW_TEMPLATE.md"],
      ["PROJECT_CASE_STUDY_TEMPLATE.md", "docs/templates/PROJECT_CASE_STUDY_TEMPLATE.md"],
    ],
  },
  ko: {
    config: "project-docs.config.ko.json",
    templates: [
      ["PROJECT_OVERVIEW_TEMPLATE.ko.md", "docs/templates/PROJECT_INTRO_TEMPLATE.md"],
      ["PROJECT_CASE_STUDY_TEMPLATE.ko.md", "docs/templates/PROJECT_DETAIL_TEMPLATE.md"],
    ],
  },
};

function parseArguments(argv) {
  const options = {
    apply: false,
    canonicalBranch: "main",
    environment: "notion-production",
    locale: "en",
    publicationMode: "manual",
    target: process.cwd(),
  };

  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === "--apply") options.apply = true;
    else if (argument === "--canonical-branch") {
      options.canonicalBranch = argv[index + 1];
      if (!options.canonicalBranch) throw new Error("--canonical-branch requires a value.");
      index += 1;
    } else if (argument === "--environment") {
      options.environment = argv[index + 1];
      if (!options.environment) throw new Error("--environment requires a value.");
      index += 1;
    } else if (argument === "--locale") {
      options.locale = argv[index + 1];
      if (!options.locale) throw new Error("--locale requires en or ko.");
      index += 1;
    } else if (argument === "--publication-mode") {
      options.publicationMode = argv[index + 1];
      if (!options.publicationMode) {
        throw new Error("--publication-mode requires manual or on-main-push.");
      }
      index += 1;
    } else if (argument === "--target") {
      options.target = argv[index + 1];
      if (!options.target) throw new Error("--target requires a path.");
      index += 1;
    } else if (argument === "--help" || argument === "-h") {
      options.help = true;
    } else {
      throw new Error(`Unknown option: ${argument}`);
    }
  }
  return options;
}

function sha256(content) {
  return createHash("sha256").update(content).digest("hex");
}

function normalizeLineEndings(content) {
  return content.replace(/\r\n?/g, "\n");
}

function readAsset(name) {
  return readFileSync(path.join(ASSET_DIRECTORY, name), "utf8");
}

function renderedConfig(assetName, canonicalBranch, publicationMode) {
  const config = JSON.parse(readAsset(assetName));
  config.canonicalBranch = canonicalBranch;
  config.publicationMode = publicationMode;
  validateProjectDocsConfig(config);
  return `${JSON.stringify(config, null, 2)}\n`;
}

function renderedWorkflow(environmentName) {
  if (
    typeof environmentName !== "string" ||
    !environmentName.trim() ||
    environmentName.length > 255 ||
    /[\r\n\u0000-\u001f]/.test(environmentName)
  ) {
    throw new Error("--environment must be a single-line value of at most 255 characters.");
  }
  const source = readAsset("project-docs-notion.workflow.yml");
  const token = "__PROJECT_DOCS_GITHUB_ENVIRONMENT__";
  if (source.split(token).length !== 2) {
    throw new Error("Workflow template must contain exactly one environment token.");
  }
  return source.replace(token, JSON.stringify(environmentName.trim()));
}

export function planRepositoryInstallation(rawOptions = {}) {
  const locale = rawOptions.locale ?? "en";
  const localeDefinition = LOCALES[locale];
  if (!localeDefinition) throw new Error(`Unsupported locale "${locale}". Use en or ko.`);

  const target = path.resolve(rawOptions.target ?? process.cwd());
  if (!existsSync(target)) throw new Error(`Target repository does not exist: ${target}`);

  const canonicalBranch = rawOptions.canonicalBranch ?? "main";
  const environment = rawOptions.environment ?? "notion-production";
  const publicationMode = rawOptions.publicationMode ?? "manual";
  const files = [
    {
      destination: "project-docs.config.json",
      content: renderedConfig(localeDefinition.config, canonicalBranch, publicationMode),
    },
    {
      destination: ".github/workflows/project-docs-notion.yml",
      content: renderedWorkflow(environment),
    },
    {
      destination: ".github/project-docs/project-docs.config.schema.json",
      content: readAsset("project-docs.config.schema.json"),
    },
    {
      destination: ".github/project-docs/project-docs-config.mjs",
      content: readFileSync(path.join(SCRIPT_DIRECTORY, "project-docs-config.mjs"), "utf8"),
    },
    {
      destination: ".github/project-docs/validate-project-docs.mjs",
      content: readFileSync(path.join(SCRIPT_DIRECTORY, "validate-project-docs.mjs"), "utf8"),
    },
    {
      destination: ".github/project-docs/sync-project-docs-to-notion.mjs",
      content: readFileSync(path.join(SCRIPT_DIRECTORY, "sync-project-docs-to-notion.mjs"), "utf8"),
    },
    ...localeDefinition.templates.map(([asset, destination]) => ({
      destination,
      content: readAsset(asset),
    })),
  ].map((file) => {
    const absolutePath = path.resolve(target, file.destination);
    const relativePath = path.relative(target, absolutePath);
    if (relativePath.startsWith("..") || path.isAbsolute(relativePath)) {
      throw new Error(`Installation path escapes the target repository: ${file.destination}`);
    }
    const existing = existsSync(absolutePath) ? readFileSync(absolutePath, "utf8") : undefined;
    return {
      ...file,
      absolutePath,
      status:
        existing === undefined
          ? "create"
          : sha256(normalizeLineEndings(existing)) ===
              sha256(normalizeLineEndings(file.content))
            ? "unchanged"
            : "conflict",
    };
  });

  return { canonicalBranch, environment, files, locale, publicationMode, target };
}

export function installRepositoryScaffolding(rawOptions = {}) {
  const plan = planRepositoryInstallation(rawOptions);
  const conflicts = plan.files.filter((file) => file.status === "conflict");
  if (conflicts.length > 0) {
    throw new Error(
      `Installation stopped before writing because existing files differ:\n${conflicts
        .map((file) => `- ${file.destination}`)
        .join("\n")}`,
    );
  }

  if (rawOptions.apply) {
    for (const file of plan.files.filter((candidate) => candidate.status === "create")) {
      mkdirSync(path.dirname(file.absolutePath), { recursive: true });
      writeFileSync(file.absolutePath, file.content, { encoding: "utf8", flag: "wx" });
    }
  }
  return plan;
}

function printPlan(plan, apply) {
  for (const file of plan.files) {
    console.log(`${file.status.padEnd(9)} ${file.destination}`);
  }
  const creates = plan.files.filter((file) => file.status === "create").length;
  const unchanged = plan.files.filter((file) => file.status === "unchanged").length;
  console.log(
    `${apply ? "Installed" : "Dry run"}: ${creates} file(s) to create, ${unchanged} unchanged.`,
  );
  if (!apply && creates > 0) console.log("Run again with --apply after reviewing this plan.");
}

function printHelp() {
  console.log(`Install Notion Project Docs into a repository.

Usage:
  node install-project-docs.mjs [options]

Options:
  --target <path>              Target repository (default: current directory)
  --locale <en|ko>             Template language (default: en)
  --canonical-branch <name>    Publication branch (default: main)
  --publication-mode <mode>    manual or on-main-push (default: manual)
  --environment <name>         Protected GitHub environment (default: notion-production)
  --apply                      Write files; otherwise perform a dry run
  -h, --help                   Show this help`);
}

const isDirectExecution =
  process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1]);

if (isDirectExecution) {
  try {
    const options = parseArguments(process.argv.slice(2));
    if (options.help) printHelp();
    else printPlan(installRepositoryScaffolding(options), options.apply);
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  }
}
