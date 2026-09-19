# Changelog

All material changes to `calculadora-android` are recorded here.

## [Unreleased]

### Added

- Add the data layer as the second Kotlin unit of the port: the `:core:data`
  module, an Android library that feeds the engine with everything it does not
  compute itself. The four quotation sources of the web product — BCB Olinda
  PTAX, the BCB closing CSV, the AwesomeAPI spot and Yahoo Finance as its
  contingency — sit behind thin Retrofit/OkHttp readers with a 4-second call
  timeout, no API key, and an honest `User-Agent`
  (`calculadora-android/<versionName> (Android; +https://calculadora.lcv.dev)`,
  operator decision of 19/09/2026); the JSON of each source is read as text and
  turned into `BigDecimal` without passing through floating point. PTAX is
  looked up for the purchase day and up to six days back, cached per (currency,
  day) in Room; the closing CSV is now a real same-day contingency when Olinda
  is unavailable (in the web product it was a dead path for the supported
  currencies). The spot is memoised in memory for 60 seconds and, once
  calibrated by the engine, persisted as the device's last known spot — the web
  product's `LATEST_SPOT`. The backtest series lives in Room with the web
  product's semantics: seven-day window capped at 200 observations, last 20
  exposed, 30-day pruning; `BigDecimal` and dates are stored as exact text,
  never `REAL`. `Simulador` orchestrates context, quotations, engine and
  persistence and is the single entry point the interface will call. Two
  deliberate departures from the web product, both recorded in the issue: the
  CSV contingency above, and an observation is recorded only when a real spot
  (from a source or the last saved one) was compared against the PTAX — on the
  PTAX contingency the "error" is zero by construction and would only flatter
  the MAPE. Tests on the JVM without an emulator: the readers against the
  real payloads recorded on 19/09/2026 through the official OkHttp
  `MockWebServer`, and the repositories and `Simulador` against in-memory
  implementations of the Room DAO interfaces; the DAO SQL itself is covered by
  an instrumented test run on the local emulator before each pull request,
  since CI has none. Room, Hilt, KSP, OkHttp, Retrofit, kotlinx-serialization
  and kotlinx-coroutines enter the version catalog and the third-party
  inventory.
- Add the calculation engine as the first Kotlin of the port: the `:core:calc`
  module, pure Kotlin with no Android dependency, holding every business rule
  the web product keeps on the server and in the client — card versus global
  account cost, the three scenarios of the charged-in-reais mode with the
  reverse diagnosis, sensitivity bands, operational context in Brasília time,
  backtest error, MAPE and classification, the Central Bank closing CSV,
  localized number parsing, formatting, currencies and the best-option choice.
  Arithmetic is `BigDecimal` with an explicit scale and rounding mode at every
  step (operator decision). Movable holidays — Carnival, Good Friday, Corpus
  Christi — are derived from Easter by the Meeus/Jones/Butcher computus instead
  of a per-year table, so the spread selection stays right in any year without
  a release. Seventy-one JVM tests with hand-checked values run under the
  existing `./gradlew test` of the CI workflow; peer review (cross-review
  session 96dd5a4b) caught a double rounding in the charged-in-reais
  surcharges, fixed with two regressions before this landed. The Kotlin Gradle Plugin, JUnit
  and the Android Gradle Plugin versions now live in the official Gradle
  version catalog `gradle/libs.versions.toml`, and the Dependabot `ignore` for
  the Kotlin Gradle Plugin is removed, as that file itself required once the
  plugin became a direct dependency.
- Record a GitHub Release on every publication to the Google Play `production`
  track (operator decision of 18/09/2026, PANDROI-40). The Release carries the
  universal APK that Google Play generated and signed with the app signing key
  — the same binary the store distributes, so it installs alongside and updates
  a Play installation, which an APK signed here with the upload key never
  could — plus `SHA256SUMS` and a build provenance attestation from the
  official `actions/attest`. The tag is `vXX.XX.XX` derived from `versionName`;
  a repeated tag fails on purpose. Internal, alpha and beta publications stay in
  Google Play alone. The Release is created as a draft, assets attached, then
  published, the order immutable releases require.
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

- Raise the minimum Android version to 14 (`minSdk` 34) and compile against
  Android 17 (`compileSdk` 37.2). The first is the operator's decision of
  19/09/2026 (*"Estamos no Android 17. Versão mínima 14."*), which also makes
  the engine's `java.time` native on every supported device, with no core
  library desugaring; the second is required by OkHttp 5.5, whose Android
  artifact refuses to compile against anything older than API 37; `targetSdk`
  follows it, so the application declares itself built for the version it is
  compiled against (lint `OldTargetApi`).
- Correct `docs/especificacao-v1.md` after a fresh review of the day's work. The
  scope inventory had counted only server-side logic and missed client-side
  logic that is in scope — sharing, formatting and supported currencies, form
  validation, the backtest read model and the licences screen — so the total
  moves from about 820 to about 1,300 lines. The AwesomeAPI rationale was
  invented: the documentation says keyless requests are served from cache, not
  that limits are per IP; the web product uses no key and the application
  inherits the same tier. Yahoo Finance had never been probed; it answers 200 to
  an honest User-Agent and **429 to none**, which is now recorded. The claim
  that `security.js` mattered for input sanitisation was false — it holds only
  server concerns. And movable holidays in the web product exist in a table
  for 2026 alone; by operator decision the application computes them from the
  Easter date (Meeus/Jones/Butcher) for any year, instead of carrying the table.
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
