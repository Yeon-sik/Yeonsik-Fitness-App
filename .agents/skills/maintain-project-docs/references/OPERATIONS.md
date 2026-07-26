# Notion publication operations

## Contents

1. Notion connection
2. GitHub controls
3. Secrets
4. First publication
5. Failure and recovery

## Notion connection

Create a Notion integration with read-content and update-content capabilities.
Create one dedicated mirror page per configured document and share every page
with the integration. Do not use pages that contain manual notes, child pages,
or databases.

## GitHub controls

Create the GitHub environment selected during installation and limit deployment
branches to the configured canonical branch.

`publicationMode` controls how a validated canonical revision is published.

- `manual` is the default. Configure a required reviewer. Publication requires
  `workflow_dispatch`, `operation=publish`, the exact `PUBLISH` confirmation,
  environment approval, and the configured canonical branch.
- `on-main-push` is opt-in. Do not configure a required reviewer, because that
  would make every automatic run wait for manual approval. A matching push or
  merge to the configured canonical branch publishes after validation. The
  manual `PUBLISH` path remains available for recovery.

Both modes keep the protected environment for secrets and branch policy. Pull
requests and non-canonical pushes never write to Notion.

## Secrets

Store both values in the protected GitHub environment:

- `NOTION_TOKEN`: the Notion integration token.
- `NOTION_PAGE_IDS_JSON`: a JSON object mapping configured document keys to
  page IDs.

Example:

```json
{"overview":"11111111111111111111111111111111","case-study":"22222222222222222222222222222222"}
```

Never commit either value.

## First publication

1. Review the Notion pages and ensure they contain no manual child content.
2. Merge the complete runtime and documents to the canonical branch.
3. Confirm the validation job passes.
4. For `manual`, dispatch `validate`, then dispatch `publish`, enter `PUBLISH`,
   and approve the environment.
5. For `on-main-push`, confirm that the merge-triggered publish job completes
   without an approval wait.
6. Review the Actions summary, both Notion pages, source revision links, and
   source fingerprints.

## Failure and recovery

- Preflight failure: grant the integration access or correct the page map, then
  rerun. No write should have occurred.
- Partial publication: do not edit the mirror manually. Rerun the same source
  revision; already-current pages are skipped.
- Truncated or unknown Markdown: reduce unsupported page content or choose a
  clean dedicated mirror page.
- Wrong content: fix the canonical Markdown in Git and publish a reviewed
  revision. Do not repair only the Notion mirror.
- Credential exposure: revoke the token, remove the value from Git history if
  committed, create a new token, and update the protected environment secret.
