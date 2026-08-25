# Contributing

This public repository delivers the LCV Calculadora Android application.
Contributions must preserve its explicit privacy and product boundaries.

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

- After the unavoidable one-line empty-repository initialization, every change
  to `main` uses a pull request and GitHub's native merge queue. Squash is the
  only merge method.
- Human-authored pull requests require explicit human admission. Canonical
  same-repository Dependabot pull requests may be admitted by the central
  Dependabot Custom Auto-merge controller only after the exact head satisfies
  the same rules and checks.
- No workflow bypasses rulesets or performs a direct merge.
- Set workflow-level permissions to `{}` or read-only and grant each job only
  the token capabilities it demonstrably needs.
- Pin external GitHub Actions to immutable full commit SHAs and regenerate
  `.github/workflows/actions.lock` after every workflow dependency change.
- Do not commit secrets, tokens, private keys, signing material,
  `local.properties`, service-account files, production payloads, or real
  infrastructure identifiers.

## Validation

Before opening or updating a pull request:

1. validate every edited workflow with `gh actions-lock` and Zizmor;
2. confirm that checks intended for the merge queue also run on `merge_group`;
3. run only gates applicable to the current repository state;
4. record exact evidence in the pull request and linked work item.

Do not create a fake Gradle project or execute Android build gates before a
real application scaffold exists.
