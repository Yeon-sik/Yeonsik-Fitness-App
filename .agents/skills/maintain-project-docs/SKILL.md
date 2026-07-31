---
name: maintain-project-docs
description: Create, audit, or update evidence-grounded repository documentation and maintain a reviewed Git Markdown to Notion mirror. Use when Codex must prepare project overviews or technical case studies, refresh portfolio documentation from code and test evidence, validate publishable Markdown, install repository-local documentation automation, or diagnose a configured Notion publication workflow.
---

# Maintain Project Docs

Keep Git-tracked Markdown as the canonical source. Treat Notion as a generated
mirror and never as a second editable source.

## Document a repository

1. Establish the repository, revision, target documents, audience, and requested
   scope.
2. Read repository instructions and acceptance criteria.
3. Inspect tracked source, tests, configuration, migrations, deployment files,
   existing documentation, and relevant runtime evidence.
4. Read [references/EVIDENCE_MODEL.md](references/EVIDENCE_MODEL.md). Classify
   every material claim before writing.
5. Preserve useful existing structure. Update only claims affected by current
   evidence.
6. Use the matching asset as a starting point only when the repository lacks a
   stronger template:
   - English overview:
     [assets/PROJECT_OVERVIEW_TEMPLATE.md](assets/PROJECT_OVERVIEW_TEMPLATE.md)
   - English case study:
     [assets/PROJECT_CASE_STUDY_TEMPLATE.md](assets/PROJECT_CASE_STUDY_TEMPLATE.md)
   - Korean overview:
     [assets/PROJECT_OVERVIEW_TEMPLATE.ko.md](assets/PROJECT_OVERVIEW_TEMPLATE.ko.md)
   - Korean case study:
     [assets/PROJECT_CASE_STUDY_TEMPLATE.ko.md](assets/PROJECT_CASE_STUDY_TEMPLATE.ko.md)
7. Run the repository's relevant checks sequentially. Record the command,
   revision, environment, result, and remaining uncertainty.
8. Review the final diff for private data, invented metrics, stale counts,
   unsupported Markdown, broken links, and unrelated scope.

## Validate configured documents

When `project-docs.config.json` exists, run:

```text
node .github/project-docs/validate-project-docs.mjs --config project-docs.config.json --require-tracked
node .github/project-docs/sync-project-docs-to-notion.mjs --config project-docs.config.json
```

The second command is a local render-only dry run. Do not add `--apply`
interactively.

## Install repository automation

Read [references/CONFIGURATION.md](references/CONFIGURATION.md) before installing
or changing the runtime bundle.

1. Run the installer without `--apply` and review every planned path.
2. Resolve conflicts manually; never overwrite an existing workflow, document,
   template, or runtime file.
3. Run the installer with `--apply` only after the plan is accepted.
4. Generate the configured documents from repository evidence.
5. Commit the configuration, workflow, runtime scripts, templates, and
   documents together.
6. Configure Notion and GitHub only after reading
   [references/OPERATIONS.md](references/OPERATIONS.md).

## Maintain the Notion mirror

- Validate pull requests and pushes without secrets.
- Permit writes only from the configured canonical branch.
- Keep `publicationMode` at the safe `manual` default unless the repository
  owner explicitly chooses `on-main-push`.
- In `manual` mode, require `workflow_dispatch`, the exact `PUBLISH`
  confirmation, and protected-environment approval.
- In `on-main-push` mode, publish automatically only after a matching change is
  pushed or merged to the canonical branch. Keep the GitHub environment,
  secrets, and branch restriction, but do not configure a required reviewer
  that would turn the automatic job back into a manual approval.
- Validate all secrets, document paths, page IDs, and read access before the
  first write.
- Preflight every page. Abort before writing if any page is missing,
  inaccessible, truncated, or contains unknown blocks.
- Replace only dedicated mirror pages.
- Skip a page only when its complete Markdown already matches the rendered
  source.
- Keep manual notes, child pages, and databases outside mirror pages.
- Report partial failure visibly. Rerun the same revision to converge.

## Preserve evidence integrity

- Separate repository evidence, runtime evidence, user-confirmed evidence,
  inference, and plans.
- Use one explicit source boundary: a commit, release, or labeled dirty tree.
- Never present file existence or a local build as production evidence.
- Record failed and skipped checks.
- Never invent adoption, revenue, accuracy, performance, or impact figures.
- Exclude credentials, private source data, internal URLs, local absolute
  paths, and personal identifiers from publishable documents.
