# Changelog

All material changes to `calculadora-android` are recorded here.

## [Unreleased]

### Changed

- CI keeps the report of the minified-build tests as an artifact of every run
  (CALANDR-28). Gradle does not fail on a skipped test, so the "Build, lint
  and test" check was green whether the live-quote flow of `:teste-release`
  ran or was skipped, and the job log carries no logcat. The
  `teste-release-report` artifact holds the JUnit XML, the HTML report and the
  logcat of each of the three flows, where the quote-source probes are
  logged. It is uploaded with `actions/upload-artifact`, at the pin the
  Scorecard and publication workflows already use, also when the tests fail.
- The scroll loop of `tocarNoFormulario` in `:teste-release` no longer raises
  the "Expression is unused" compiler warning; it scrolls the same way, at
  most ten times.
- The live-quote flow of `:teste-release` failed on weekends with the app
  right (CALANDR-36). It required the Conta Global to show the AwesomeAPI or
  Yahoo label whenever one of them answered HTTP 200, but the app accepts an
  exchange quote only up to 24 hours old (`IDADE_MAXIMA_SPOT`) and falls back
  to the PTAX otherwise. With the market closed, both sources answer with
  Friday's quote. Measured on Sunday 04/10/2026: 41 and 28.5 hours old, and
  the flow failed on `main` too. By the operator's decision, the flow no
  longer judges the exchange quote; the app decides whether to accept it, and
  that rule is covered by the `:core:data` unit tests. A live AwesomeAPI or
  Yahoo label on the Conta Global proves the minified exchange path, and the
  flow passes. A fallback (the last saved value or the PTAX fallback) does not
  prove it, and the flow is reported as skipped. When the Central Bank answers
  the test, the PTAX label stays required, and a Conta Global with no source
  at all fails the flow.
- `:teste-release` waits for an element by scrolling with UI Automator's
  `scrollUntil`. The app sends UI Automator no scroll event, so `scroll`
  reported the end at every step. The old search turned around at every
  reported end and swung half a screen down and up without reaching the
  Conta Global card (measured on 04/10/2026).
- Each `:teste-release` flow opens the app a second time when the first
  launch does not show the form. After `pm clear` returns, the system can
  still kill the app launched right after it: in a CI run on 04/10/2026, the
  app opened 0.8 s after `pm clear` and was killed 0.3 s later, and the flow
  failed with "o aplicativo não abriu". A launch counts only once the
  Calcular button is on screen, not when the package's window appears.

### Fixed

- With the software keyboard open, the whole window was pushed up and the
  app header slid under the status bar, cut and overlapping the clock. It
  happened in 1.0.1 too, measured on its debug build at Pixel 2 width. The
  activity now declares `android:windowSoftInputMode="adjustResize"`, as the
  official edge-to-edge setup asks, so the keyboard reaches the app as an
  inset instead of panning the window. The scrolling container applies
  `consumeWindowInsets` with the `Scaffold` padding and then `imePadding()`,
  so the content stops above the keyboard without padding the navigation bar
  twice (CALANDR-31).
- The "⭐ MELHOR" badge of the winning result card was squeezed by the card
  title. At the default font on a 360 dp wide screen it broke into a column of
  letters ("ME / LH / OR"); with a larger font it was pushed against the
  card's edge with almost no width, cut off, and stretched the header into a
  tall empty block. Both were measured on 1.0.2. The header `Row` measured the
  title first and gave the badge only what was left. The badge is now laid out
  as in the web's `ComparisonCard`: it sits over the card's top-right corner,
  9 dp outside it, as the web's `-top-2.5 -right-2.5` do, since CSS counts
  those 10 px from inside the card's 1 px border. It is drawn outside the
  card surface, so it is not clipped, and the title no longer squeezes it.
  An elevation shadow stands in for the web's `shadow-md`; reproducing that
  shadow exactly is tracked in CALANDR-34, with the badge's other old
  differences (CALANDR-32).
- The "provável" pill of the scenario cards in "cobrado em reais" mode had the
  same measuring order. At 1.3 font scale on Pixel 2 width it broke into one
  letter per line. Its title now takes the remaining width, as the web's
  `ml-auto` does, and the pill keeps its own (CALANDR-32).
- The purchase date button and the Conta Global indicators had the same
  measuring order. With the font at its maximum on a 360 dp wide screen, the
  "Escolher" button broke its word in two ("Escolhe / r"). Off-hours with the
  PTAX fallback, "⚡ Contingência" shows beside "🌙 Plantão" and broke into
  two lines. The button now moves whole to the next line, still on the right,
  when it does not fit beside the date. The indicators flow onto the next line,
  as the web's `inline-block` pills do (CALANDR-32).
- The small pills (the badge, "provável", the indicators and the backtest
  quality pill) use a line height of 1.5 times their font, as the web's
  badge and scenario pill do, instead of inheriting the 24 sp of
  `bodyLarge`. At the default font they are 23 dp tall instead of 32 dp
  (CALANDR-32).
- The "✅" best-option pill under the result cards reused the small 10 sp pill
  and the card title. It now has the web's `text-sm px-4 py-2 border
  border-green-200 bg-green-50 text-green-800`: 14 sp text with the normal
  letter spacing, centered when it wraps, 38 dp tall at the default font. It
  shows the web's short labels ("✅ 🌐 Conta Global", "✅ 💳 Cartão de
  Crédito", "✅ 💰 Saldo Existente"). Tailwind 4 defines those greens in
  OKLCH; the theme carries them converted to sRGB (CALANDR-32).
- Two differences from the web apply only with a large font or a narrow
  screen. Both are declared in the specification by the operator's decisions
  of 03 and 04/10/2026 (CALANDR-32):
  - The title row of the winning card leaves room for the badge when the badge
    would otherwise reach down into the title's first line.
  - In the label/value rows the label keeps its width, and the value wraps
    between words beside it, still on the right; when not even the value's
    longest word fits there, the value moves to the next line. The web shrinks
    both sides. Before, the label squeezed the value until it broke in the
    middle of the number ("R$ 5.896,|40").
- New instrumented tests force the font scale and the screen width with
  `DeviceConfigurationOverride`. Each defect case fails on the 1.0.2 layout; a
  control case, at the default font on a wide screen, passes on both
  (CALANDR-32).
- At the largest font on a 360 dp wide screen, the fixed top bar took about a
  quarter of the screen: the title broke into two lines and the subtitle,
  squeezed beside "Licenças", into five, and the bar kept that space even with
  the results on screen. The bar had not changed since 1.0.0. It is now the
  top of the scrolling content, as the web header is the top of its page: it
  scrolls away with the content and comes back only at the top. It is the
  same Material 3 `TopAppBar`, so the top of the screen looks the same; the
  `Scaffold` no longer pins it. With the keyboard open, when the focused field
  has to move up, the header scrolls with the content, as on the web
  (operator's decision of 04/10/2026). It is never drawn under the status
  bar, which CALANDR-31 fixed, because the content starts below it
  (CALANDR-35).

## [1.0.2] — 03/10/2026

### Added

- Every numeric field formats its input in the Brazilian format as it is
  typed (CALANDR-27, operator's decisions of 02 and 03/10/2026). The
  cash-register entry the web product applies only to its Valor field now
  covers all seven: digits only, entering from the right, with thousands dots
  and the decimal comma added automatically — 2 places for reais and foreign
  currency (`123456` → `1.234,56`), 4 for the VET (`57340` → `5,7340`) and 2
  for the percentages (`350` → `3,50`). Each field is Compose's state-based
  text field: the value is the raw digit string in a `TextFieldState` held by
  the ViewModel, an `InputTransformation` (`DigitosDeCaixa`) keeps digits only
  with the cursor at the end, and an `OutputTransformation`
  (`FormatoDeCaixa`) inserts the separators; editing, selection, clipboard,
  accessibility, undo and saving are the platform's. Empty means the default
  and `0` is an explicit zero. Pasted, dictated, autofilled or accessibility
  text counts only its digits. A key or paste that adds no digit, or that
  would pass the field's limit, is refused whole and keeps the value, the
  selection and the result. "Select all" and a key replace the value; any
  other cursor or partial selection moves to the end. A result is shown only
  while the seven fields hold the numbers it was calculated with, so going
  back to them, even by undo, shows it again; date, mode and currency still
  clear it. An empty parameter field shows the default it stands for even
  while idle — "Padrão: 5,50%", "Padrão: 0,78% (dias úteis, 9h–17h, horário
  de Brasília)" — read from `Parametros` and `ContextoOperacional`, instead of
  "Auto", which suggested a lookup that does not exist. In "cobrado em reais"
  mode the two Conta Global spreads, which the engine ignores there, are
  hidden. Field values saved by 1.0.1 are ignored: the fields use new
  saved-state keys, and the old keys are removed.

### Changed

- Turn on R8 for the release build (CALANDR-26). Play Console flagged 1.0.1
  with "DEX code optimization below our threshold": obfuscation at 1%, no R8
  metadata in the bundle, 25.6 MB of uncompressed DEX. From February 2027
  Play expects at least 25% optimization, obfuscation and shrinking for apps
  above 10 MB of DEX. The release build type now uses the AGP 9.3+
  `optimization { enable = true }` DSL, which shrinks, optimizes and
  obfuscates code and resources with the Android default rules; Hilt, Room,
  Retrofit and OkHttp ship their own, and the app has no reflection of its
  own, so no project rule is added. Measured on the local bundle: the
  `r8.json` Play reads now reports 98.2% obfuscation, 97.2% optimization and
  98.1% shrinking, the uncompressed DEX drops to 2.55 MB, and the mapping
  file travels in the bundle. The minified release APK was driven on an
  emulator through the calculation with live quotes, the sensitivity and
  backtest results, the DCC mode and the licenses screen, with no crash.
  CI now drives the minified build too: the new `:teste-release` module
  (`com.android.test`, self-instrumenting, UI Automator 2.4.0) installs the
  `minificado` build type of `:app` — the release, signed with the debug key —
  and runs the opening, the simulation with live quotes, the DCC mode and the
  licenses screen from outside the app's process, on the same Pixel 2 API 34
  managed device as the other instrumented tests. The app shows the same "no
  quotes" notice for a source that is down and for its own failure, so the
  test probes each quote source itself, at the addresses the app uses: every
  source that answers must appear by name in the result ("PTAX do Banco
  Central" on the card, AwesomeAPI or Yahoo Finance on the global account),
  and with no source answering the test is skipped, not passed. App data is
  cleared before each journey, so a stored quote cannot pass for a live one.
  Controls: a crash on launch fails all three journeys; JSON reading that
  returns nothing fails the simulation.
- `SimulacaoViewModel` no longer defaults its `SavedStateHandle` to the
  no-argument constructor, which AndroidX reserves for tests. Hilt always
  injected the real one in the app; the two tests that relied on the default
  now pass it explicitly, and lint reports no warning on debug or release.
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

### Fixed

- Preserve the CC-BY 2.5 attribution of the four jsr305 3.0.2 classes that
  ship in the app (`javax.annotation.concurrent` `GuardedBy`, `Immutable`,
  `NotThreadSafe` and `ThreadSafe`, © 2005 Brian Goetz), which the published
  POM declares as Apache-2.0 while their source headers grant CC-BY 2.5. The
  credit, license URI and full CC-BY 2.5 text are in `NOTICE`, which the
  licenses screen shows, and `THIRDPARTY.md` explains the per-file license
  (CALANDR-29, #81).

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

First public release: the native port is complete, `:core:calc` +
`:core:data` + `:app`, and the Google Play store listing is filled. Everything
below shipped in this version.

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
