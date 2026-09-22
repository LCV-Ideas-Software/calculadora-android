# Security Policy

## Supported state

The current `main` branch and the latest Android release are maintained.
The repository contains the Kotlin/Compose application, its calculation engine,
local Room storage, public exchange-rate clients and the Google Play publication
workflows. There are no accounts, analytics SDKs or application backend.

## Reporting a vulnerability

Prefer GitHub's private vulnerability reporting for this repository. If that
surface is unavailable, report privately to `security@lcv.dev`.

Do not disclose a vulnerability, credential, signing artifact, personal datum,
or unpublished operational detail in a public Issue, Discussion, pull request,
commit, or Pages content.

Include:

- the affected repository and exact revision;
- a concise impact description;
- reproducible steps that do not expose secrets;
- any safe mitigation already tested.

Never send live credentials. Revoke or rotate exposed credentials immediately
through their owning system and report only non-secret metadata.
