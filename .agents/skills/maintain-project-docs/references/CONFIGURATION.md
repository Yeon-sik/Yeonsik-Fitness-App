# Repository configuration

## Contents

1. Installation
2. Configuration file
3. Installed runtime
4. Upgrade policy

## Installation

Run the installer from the skill directory. It is non-writing by default:

```text
node scripts/install-project-docs.mjs --target <repository>
```

Supported options:

- `--locale en|ko`: choose the template and default document names.
- `--canonical-branch <branch>`: restrict publication to this branch.
- `--publication-mode manual|on-main-push`: choose manual approval or
  canonical-branch automatic publication. The default is `manual`.
- `--environment <name>`: set the protected GitHub environment used by the
  publication job.
- `--apply`: create files after a conflict-free dry run.

The installer never overwrites a differing file. A conflict stops the whole
preflight before any new file is written.

## Configuration file

`project-docs.config.json` is the target repository's canonical document map:

```json
{
  "$schema": "./.github/project-docs/project-docs.config.schema.json",
  "version": 1,
  "canonicalBranch": "main",
  "publicationMode": "manual",
  "documents": [
    {
      "key": "overview",
      "label": "Project Overview",
      "path": "docs/Project_Overview.md",
      "requiredSections": ["Executive summary", "Verification"]
    }
  ]
}
```

Rules:

- Keep `publicationMode` as `manual` for reviewed releases, or explicitly set
  `on-main-push` when every matching merge to the canonical branch should
  update Notion.
- Keep document keys unique, stable, lower-case, and hyphen-delimited.
- Keep document paths relative to the repository and Git-tracked.
- Use `requiredSections` for language- or organization-specific structure.
- Map each key to one dedicated Notion page in `NOTION_PAGE_IDS_JSON`.
- Do not store page IDs or tokens in the configuration file.

## Installed runtime

The installer creates:

```text
project-docs.config.json
.github/
  project-docs/
    project-docs.config.schema.json
    project-docs-config.mjs
    sync-project-docs-to-notion.mjs
    validate-project-docs.mjs
  workflows/
    project-docs-notion.yml
docs/
  templates/
    ...
```

The target repository owns this pinned runtime. GitHub Actions never depends on
an agent's local plugin installation.

## Upgrade policy

Run a new plugin version's installer without `--apply`. Differing installed
runtime files appear as conflicts by design. Review the upstream diff, update
the target repository in one change, run validation, and preserve local
configuration deliberately. Do not use a force-overwrite upgrade.
