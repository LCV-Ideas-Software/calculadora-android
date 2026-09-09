# Contributing

This public repository delivers the LCV Ideas & Software Calculadora Android
application. Contributions must preserve its explicit privacy and product
boundaries.

## Tracking boundary

- Link a GitHub Issue to Linear only when both resources are explicit,
  unequivocal counterparts.
- Do not copy sensitive Linear content, private Project drafts, credentials,
  account data, personal data, or unpublished operational details into public
  Issues, Discussions, commits, pull requests, Pages, or source files.
- Never create speculative Issues to satisfy a reconciliation count.
- The application must remain free of AI functionality, tracking SDKs,
  fingerprinting, and the web product's telemetry or IP logging.

## Change control

- Every change to `main` uses a pull request and the effective native GitHub
  rules. Squash is the only merge method; there is no merge queue.
- Human-authored pull requests follow the operator's admission process.
  The repository-local Dependabot workflow enables GitHub native auto-merge
  for canonical same-repository Dependabot pull requests, bound to the exact
  head and subject to all required checks and rules. It grants no bypass.
- Each repository operates independently. Do not introduce central lifecycle
  controllers, custom governance scripts or obsolete Actions lock mechanisms.
- Set workflow-level permissions to `{}` or read-only and grant each job only
  the token capabilities it demonstrably needs.
- Pin external GitHub Actions to immutable full commit SHAs directly in each
  workflow.
- Do not commit secrets, tokens, private keys, signing material,
  `local.properties`, service-account credentials, production payloads or
  private planning material. Non-secret configuration identifiers required
  by official integrations may be versioned; do not confuse them with secrets.
- Follow [INBOUND.md](INBOUND.md) before submitting copyrightable material.

## Validation

Before opening or updating a pull request:

1. validate every edited workflow with Zizmor;
2. confirm that required checks cover pull requests, including retargeting and
   ready-for-review events;
3. run only gates applicable to the current repository state, preserving
   `Build Pages artifact`, `Dependency Review` and `Run zizmor`;
4. record exact evidence in the pull request and linked work item.

Do not create a fake Gradle project or execute Android build gates before a
real application scaffold exists.
