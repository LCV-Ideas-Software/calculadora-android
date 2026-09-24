# Changelog

All material changes to `calculadora-android` are recorded here.

## [Unreleased]

### Changed

- Record that CodeQL now analyzes the Kotlin code (CALANDR-14, step 1). On
  24/09/2026 the Default setup added `java-kotlin`, built with autobuild, on
  CodeQL 2.27.1, the first version that supports Kotlin 2.4.20; the first
  analysis on `main` (`95d8506`) finished green with no alerts. The `README.md`
  and section 11 of the specification still said `java-kotlin` stayed out of
  CodeQL, and now say it is in; section 11 moves item 2 to the resolved list.
  They also correct why `quality/code-quality-probe.js` stays, and so does the
  placeholder's own comment. It was never there for CodeQL but for Code
  Quality, whose rule-based analysis does not cover Kotlin and whose `none`
  build mode cannot extract it. The placeholder remains, by the operator's
  decision of 24/09/2026, until Code Quality covers Kotlin; removing it is
  still CALANDR-14's step 2.

## [1.0.1] — 21/09/2026

### Fixed

- Cancel superseded calculations and reject stale responses after any input edit;
  restore primitive form inputs through SavedStateHandle after process recreation.
- Bound decimal input, validate optional fields and percentage ranges, and reject
  future purchase dates before calculation. Reject nonpositive or wrong-date CSV rates.
- Cache only closing PTAX; expire spot quotes after 24 hours using source timestamps.
  Compare matching quote dates and keep at most one sample per currency/day. Explicit
  Room 1→2 migration discards only untrustworthy derived caches and old observations.
- Correct BRL spread labels, expose quote dates/sources, label both sensitivity panels,
  associate editable fields with accessible labels, and improve text contrast.
- Bound HTTP bodies and query durations; exclude derived local data from backup.
- Repair APK download output; verify actual APK package/version/signing certificate;
  bind release tags to the checked-out commit and require Play publication lifecycle
  PUBLISHED before recording a GitHub Release. Preserve ongoing review by default.
- Add regression, migration and Android 14 CI coverage plus debug/release lint/build.

Details: [CALANDR-24](https://linear.app/lcv-ideas-software/issue/CALANDR-24),
[correction contract](docs/correcoes-v1.0.1.md). This entry describes the version;
Google review/publication remains observable through its API, not inferred from this file.

### Maintenance included since 1.0.0

### Changed

- Complete the third-party inventory for the Actions this repository's workflows
  actually use. `actions/setup-java`, `gradle/actions` and
  `google-github-actions/auth` were pinned in `ci.yml` and `publish-play.yml`
  and missing from the table — an inventory that omits what a workflow runs is
  not an inventory. `gradle/actions` is recorded as its own `LICENSE` states:
  primarily MIT, with a vendored component, `gradle-actions-caching`, that is
  proprietary under a separate licence detailed in that repository's
  `DISTRIBUTION.md` and `NOTICE`. That is why GitHub classifies it as
  `NOASSERTION`, and flattening it to MIT would misstate what ships. The gap was
  found while porting this pipeline to astrologo-android and maestro-android, and
  is fixed here for the same reason it was fixed there.

### Added

- Record a GitHub Release for a version already live on Google Play, without
  rebuilding or re-uploading anything: the `Record a Play release on GitHub`
  workflow, taking a `versionCode` and fetching the universal APK the Play
  signed. It exists because the first publication of an app cannot be automated
  end to end — a never-published app only accepts a draft release on the public
  track, and a person finishes it in the Console. By then `publish-play.yml` has
  already exited, and re-dispatching it does not help: it would rebuild and
  re-upload the same `versionCode`, which the Play refuses. Measured on
  20/09/2026: the Play makes the universal APK available as soon as it processes
  the bundle, before any rollout, so the artefact is never what delays the
  Release — the policy of only recording what the store distributes is. The
  workflow refuses to run when the checked-out `versionCode` differs from the one
  asked for, because recording a release under another version's name would be
  worse than not recording it.

- Let the publishing workflow send a release as a draft, which is the only thing
  Google Play accepts from an app that has never been published. The 1.0.0
  publication to `production` was refused with *"Only releases with status draft
  may be created on draft app"* — visible only because the previous change stopped
  discarding the API's answer. The restriction is specific: the internal
  publication of 17/09/2026 used `completed` on this same app and succeeded, so
  it is the public track that a draft app guards. The official Play Console help
  describes the path — *"When you're ready to publish a draft app, you'll need to
  roll out a release. At the end of the release process, clicking Release will
  also publish your app"* — so the API uploads the bundle and creates the draft,
  and a person finishes the first publication in the Console. A `release_status`
  input carries `completed` or `draft`, and a draft deliberately does **not**
  record a GitHub Release: nobody receives that binary until someone presses the
  button, and a Release would claim the store distributes it.

### Fixed

- Let Google Play explain itself when it refuses a publication. The 1.0.0
  release to `production` failed twice, and the entire error in the log was
  `curl: (22) The requested URL returned error: 400` — the API answers 400 with a
  body that names the reason, and the workflow threw that body away.
  `--fail-with-body` writes it to standard output, and none of the four calls let
  that output reach the log: two redirect to `/dev/null`, one feeds `jq`, and one
  lands in a variable that `set -e` aborts before printing. Turning on debug
  logging does not help, which was tried: it echoes the script, not the
  execution, and the body stays discarded. Every call now goes through a `play`
  helper that keeps body and status, returns the body on success and, on failure,
  prints which call failed and what Google Play answered. The calls carry labels
  — opening the edit, uploading the bundle, updating the track, committing the
  edit, downloading the universal APK — so the log names the exact step. The
  diagnostic goes to stderr deliberately: two calls are written with
  `>/dev/null`, which would swallow a message printed on standard output beside
  the success body. This does not fix the cause of the 400; it makes Google Play
  state it.

## [1.0.0] — 20/09/2026

First public release: the native port is complete, `:core:calc` + `:core:data`
+ `:app`, and the Google Play store listing is filled. Everything below shipped
in this version.

### Added

- Send the release notes to Google Play from the repository instead of typing
  them into the Console. The publishing workflow updated the track with only
  `versionCodes` and `status`, so a publication reached the store with no
  "what's new" at all. The notes now live in `play/release-notes/pt-BR.txt` and
  travel in the same track update, as `releases[].releaseNotes[]` of
  `LocalizedText {language, text}` with a BCP-47 language tag — the shape the
  Android Publisher API v3 documents for `edits.tracks`. The body is built with
  `jq` rather than shell interpolation, because the text is Portuguese prose
  with accents, quotation marks and line breaks. A step before the build refuses
  a missing, empty or over-long file: the Play Console documents the ceiling as
  500 Unicode characters per language, and discovering it through a rejected
  edit would waste a full build. `name` is deliberately not sent, so the API
  derives the release name from `versionName` and the version lives in one place.

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
  piece of work in that order, carried by issue #42, and the section now says
  what actually blocks them: not the absence of Kotlin, which `:core:calc`
  settled on 18/09/2026, but CodeQL itself, which does not support Kotlin 2.4.20
  — the reason the operator removed `java-kotlin` from the Default setup that
  same day. Reassessment is the issue's, dated 25/09/2026. The `README.md` said
  the same thing in two places, calling the language change "the next step, now
  that there is Kotlin to compile", which reads as available; both passages now
  name what actually holds it.
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
