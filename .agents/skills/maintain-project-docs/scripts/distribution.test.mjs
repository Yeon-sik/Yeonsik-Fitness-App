import assert from "node:assert/strict";
import { existsSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";

import {
  installRepositoryScaffolding,
  planRepositoryInstallation,
} from "./install-project-docs.mjs";
import {
  loadProjectDocsConfig,
  validateProjectDocsConfig,
} from "./project-docs-config.mjs";

function temporaryRepository() {
  return mkdtempSync(path.join(tmpdir(), "notion-project-docs-install-"));
}

test("configuration rejects document paths outside the repository", () => {
  assert.throws(
    () =>
      validateProjectDocsConfig({
        version: 1,
        canonicalBranch: "main",
        documents: [
          {
            key: "overview",
            label: "Overview",
            path: "../private.md",
          },
        ],
      }),
    /must stay inside the repository/,
  );
});

test("configuration rejects misspelled fields", () => {
  assert.throws(
    () =>
      validateProjectDocsConfig({
        version: 1,
        canonicalBranch: "main",
        documants: [],
        documents: [{ key: "overview", label: "Overview", path: "docs/Overview.md" }],
      }),
    /unknown root field/,
  );
});

test("configuration defaults to manual publication and accepts on-main-push", () => {
  const base = {
    version: 1,
    canonicalBranch: "main",
    documents: [{ key: "overview", label: "Overview", path: "docs/Overview.md" }],
  };

  assert.equal(validateProjectDocsConfig(base).publicationMode, "manual");
  assert.equal(
    validateProjectDocsConfig({ ...base, publicationMode: "on-main-push" }).publicationMode,
    "on-main-push",
  );
  assert.throws(
    () => validateProjectDocsConfig({ ...base, publicationMode: "always" }),
    /publicationMode/,
  );
});

test("installer performs a non-writing dry run by default", () => {
  const target = temporaryRepository();
  const plan = planRepositoryInstallation({ target });

  assert.ok(plan.files.length >= 8);
  assert.ok(plan.files.every((file) => file.status === "create"));
  assert.equal(existsSync(path.join(target, "project-docs.config.json")), false);
});

test("installer creates a complete idempotent runtime bundle", () => {
  const target = temporaryRepository();
  const first = installRepositoryScaffolding({
    apply: true,
    canonicalBranch: "release/docs",
    environment: "notion-docs-production",
    publicationMode: "on-main-push",
    target,
  });
  const second = installRepositoryScaffolding({
    apply: true,
    canonicalBranch: "release/docs",
    environment: "notion-docs-production",
    publicationMode: "on-main-push",
    target,
  });

  assert.ok(first.files.every((file) => file.status === "create"));
  assert.ok(second.files.every((file) => file.status === "unchanged"));

  const config = loadProjectDocsConfig({ rootDirectory: target });
  assert.equal(config.canonicalBranch, "release/docs");
  assert.equal(config.publicationMode, "on-main-push");
  assert.equal(config.documents.length, 2);

  const workflow = readFileSync(
    path.join(target, ".github", "workflows", "project-docs-notion.yml"),
    "utf8",
  );
  assert.match(workflow, /environment: "notion-docs-production"/);
  assert.doesNotMatch(workflow, /__PROJECT_DOCS_GITHUB_ENVIRONMENT__/);
  assert.match(workflow, /workflow_dispatch:/);
  assert.match(workflow, /inputs\.confirmation == 'PUBLISH'/);
  assert.match(workflow, /needs\.validate\.outputs\.publication_mode == 'on-main-push'/);

  for (const file of first.files) {
    const content = readFileSync(file.absolutePath, "utf8");
    writeFileSync(file.absolutePath, content.replace(/\r?\n/g, "\r\n"), "utf8");
  }
  const windowsCheckout = planRepositoryInstallation({
    canonicalBranch: "release/docs",
    environment: "notion-docs-production",
    publicationMode: "on-main-push",
    target,
  });
  assert.ok(windowsCheckout.files.every((file) => file.status === "unchanged"));
});

test("installer stops before writing when any destination conflicts", () => {
  const target = temporaryRepository();
  writeFileSync(path.join(target, "project-docs.config.json"), "{}\n", "utf8");

  assert.throws(
    () => installRepositoryScaffolding({ apply: true, target }),
    /stopped before writing/,
  );
  assert.equal(
    existsSync(path.join(target, ".github", "workflows", "project-docs-notion.yml")),
    false,
  );
});

test("Korean locale installs matching document paths", () => {
  const target = temporaryRepository();
  installRepositoryScaffolding({ apply: true, locale: "ko", target });
  const config = loadProjectDocsConfig({ rootDirectory: target });

  assert.deepEqual(
    config.documents.map((document) => document.repositoryPath),
    ["docs/Project_Intro.md", "docs/Project_Detail.md"],
  );
  assert.equal(
    existsSync(path.join(target, "docs", "templates", "PROJECT_INTRO_TEMPLATE.md")),
    true,
  );
});
