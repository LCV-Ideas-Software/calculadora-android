# Changelog

All material changes to `calculadora-android` are recorded here.

## [Unreleased]

### Added

- Record the v1 specification in `docs/especificacao-v1.md`: scope, architecture
  and the operator's structural decisions for the native port. The application
  reimplements the web product's logic in new Kotlin rather than wrapping it,
  carries every feature except artificial intelligence and e-mail, fetches
  quotations directly from the sources, and computes with `BigDecimal` instead
  of floating point. Each decision records the measurement behind it, and the
  accepted risks are named so they surface as deliberate choices in any later
  review.

- Add the `CI` workflow so every pull request and every push to `main` is
  compiled, analysed and tested: Gradle wrapper validation, `:app:assembleDebug`,
  `:app:lintDebug` and unit tests, on the same JDK 17 the publishing workflow
  uses. Until now no workflow ran `build`, `lint` or `test` on a pull request —
  wrapper validation and the build existed only inside `publish-play.yml`, which
  runs on `workflow_dispatch`. This settles the debt the 17/09/2026 scaffold left
  behind, and it has to precede the first Kotlin, because that first Kotlin is
  the calculation engine.

### Changed

- Correct the `README.md`, which still claimed no Gradle project or Android code
  existed and that the Gradle ecosystem would only be declared once a real
  project existed — both untrue since 17/09/2026. It now describes the scaffold
  that is actually there, records the debt that scaffold left rather than erasing
  it, and states that the inert Code Quality probe is waiting on Kotlin, not on
  an Android project.

- Update the official CodeQL Action to v4.38.0 and Zizmor Action to v0.6.4,
  retaining full commit pins and aligning the current third-party inventory.

- Align repository-local governance with the native fleet baseline, without
  adding an Android scaffold, production dependencies or a fabricated build.
- Preserve native CodeQL Default setup and Code Quality; retire the disabled
  advanced CodeQL workflow and obsolete merge-queue consumers.
- Schedule Actions-only Dependabot updates every day, including weekends,
  at 05:00 in fixed UTC-03:00, with a minor/patch group,
  separate majors, automatic rebasing and the existing selective cooldown.
- Group security updates separately from version updates.
- Enable exact-head GitHub native Dependabot auto-merge, subject to required
  checks and rules; remove documentation of the retired central controller.
- Cover retargeted and ready pull requests in Pages, Dependency Review and
  Zizmor, and keep Scorecard artifacts and SARIF repository-local.
- Align the official Linear CLI with action v0.18.0 while preserving the
  push-to-main continuous commit-history pipeline and its pending-run queue.
- Add repository-local inbound rights documentation without changing the
  existing AGPL license, copyright notice or product privacy decisions.

### Added

- Established the public repository baseline without inventing an Android or
  Gradle project.
- Added organization-standard CodeQL, Code Quality, Dependency Review, Zizmor,
  OpenSSF Scorecard, Pages, Dependabot and Linear Release automation.
- Added a deliberately inert JavaScript probe for GitHub Code Quality until
  real application source exists.
- Added independent contribution, conduct, security, licensing, notice,
  third-party, ownership and sponsorship records.

### Security

- Pinned every external GitHub Action to an immutable full commit SHA.

### Fixed

- Removed the obsolete Actions dependency lock and its workflow onboarding
  markers to restore workflow startup after Dependabot updates. Direct SHA
  pins, workflow behavior and repository security settings are unchanged.
