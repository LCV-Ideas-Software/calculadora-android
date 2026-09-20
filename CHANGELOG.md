# Changelog

All material changes to `calculadora-android` are recorded here.

## [Unreleased]

### Added

- Add the interface and close the port: the `:app` module, Jetpack Compose with
  Material 3 over the `Simulador` of `:core:data`. Appearance is part of the
  port, not a reinterpretation (operator, 20/09/2026: *"se é um porte similar e
  nativo, inclusive a aparência é similar"*), so every label, hint, emoji,
  placeholder and compliance paragraph was measured word for word in
  `calculadora-app/src/components` and `functions/api/compra-reais.mjs` rather
  than rewritten, and the colours, the brand mark and the launcher icon are the
  LCV's, converted from the brand SVGs into vectors and an adaptive icon with
  its monochrome layer. The model for what a port owes each side is Proton's,
  which the operator named: native idiom per platform over shared logic, with a
  semantic design system — here `Tema`, whose roles (`textoNorm`, `textoFraco`,
  `fundoNorm`, `separador`, `foco`) are provided through a
  `CompositionLocalProvider` and mapped onto the Material 3 scheme, so a screen
  never names a raw brand colour. What changes is only what the platform forces:
  a top bar and full-width content instead of the browser's centred panel, the
  native date dialog instead of `<input type="date">`, the Android share sheet
  instead of a copy button. Two departures are declared rather than hidden — no
  animated particle backdrop, because a permanent background animation costs
  battery without carrying information, and no dark palette, because the brand
  has not defined dark tones and inventing them would be creating an identity,
  not porting one. The licences screen reads `LICENSE`, `NOTICE` and
  `THIRDPARTY.md` from assets that a Gradle task copies from the repository root
  at build time through the AGP generated-sources API, so no second, divergent
  copy of those files can exist. Those files are written for an 80-column
  editor, and drawn verbatim on a phone every hard-wrapped line wraps again into
  an unreadable zigzag, with `#`, backticks and pipe tables showing as literal
  characters; the screen therefore reflows them — paragraphs are rejoined and
  set justified with a first-line indent and automatic hyphenation, headings are
  styled, and each row of the third-party tables becomes a block with the
  component in bold over its labelled fields, which is how a five-column table
  reads on a phone. The words are the file's; only where the lines break is the
  device's. The scroll container also gained an indicator: Compose draws none,
  and in the versions this project pins (Foundation 1.12.1, Material 3 1.4.0)
  the official API is half there — `ScrollIndicatorState` carries the offset and
  the content and viewport sizes, but nothing consumes it — so a thin mark is
  drawn on the right margin from the official `ScrollState` and removed when the
  platform ships the other half. Twelve JVM tests drive the `ViewModel` against a
  real `Simulador` over in-memory sources, and three Compose tests run the screen
  itself on a device — CI has no emulator, so they run on the local one before
  each pull request, as the `:core:data` DAO test already does. Those three
  forced Espresso to be declared directly: Compose `ui-test` still drags in
  espresso-core 3.5.0, which reflects on an `InputManager.getInstance()` that no
  longer exists on API 37, so every Compose test died in `Espresso.onIdle` before
  reaching an assertion; 3.7.0, declared in the catalog, is the Gradle mechanism
  for raising a transitive and puts the dependency under Dependabot. Compose, the
  Compose compiler plugin, Material 3, activity-compose, lifecycle-compose,
  hilt-navigation-compose and Espresso enter the version catalog and the
  third-party inventory.
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
  currencies). The spot is memoised in memory for 60 seconds (a backward
  wall-clock adjustment counts as expiry, never as freshness) and, once
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

- Correct section 11 of `docs/especificacao-v1.md`, which still listed as
  missing two pieces that have existed since 17/09/2026. The section stated
  there was no continuous integration workflow compiling, analysing and testing
  — `.github/workflows/ci.yml` has gated every pull request since CALANDR-10 —
  and that the `README.md` still claimed no Gradle project existed, which the
  same change corrected. A governing document cannot carry a statement its own
  repository disproves: a reader of the specification today would conclude this
  repository has no CI. The four items are not deleted, because they are the
  record of what the specification created; each now carries its state, with the
  date and the evidence. The two that remain — CodeQL not covering Kotlin, and
  the inert Code Quality placeholder that exists only until it does — are one
  piece of work in that order, carried by issue #42, which merging `:app`
  unblocked.
- Name the application by its public name wherever a person reads it. The
  launcher label and the first line of `NOTICE` — which the licences screen
  shows — said `Calculadora` and `calculadora-android`; the product is
  **Calculadora Financeira**, and `calculadora-android` is the repository's
  internal name (operator, 20/09/2026). The `User-Agent` keeps
  `calculadora-android/<versionName>`: it identifies the client to the quotation
  sources, it is not a name shown to anyone, and it is the operator's decision of
  19/09/2026.
- Record in `NOTICE` that the AGPL licenses the code and not the marks. The
  brand vectors and the launcher icon arrive with `:app`, and the licence that
  lets anyone redistribute a modified version grants no right to keep the LCV
  name or logo on it; whoever redistributes one replaces those files with their
  own identity. Naming this now avoids a redistributor inheriting a trademark
  claim from a licence that never covered it.
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
