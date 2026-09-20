# Third-party inventory

The first table records the build and runtime components of the Android
project, versioned in `gradle/libs.versions.toml`. The second records the
direct automation dependencies; their current immutable pins are the full
commit SHAs in each workflow's `uses:` references, and transitive Action
dependencies remain defined by those pinned upstream actions.

| Component                                     | Version | License                                                                        | Purpose                                                                 |
| --------------------------------------------- | ------- | ------------------------------------------------------------------------------ | ----------------------------------------------------------------------- |
| Android Gradle Plugin (`com.android.application`, `com.android.library`) | 9.4.1 | [Apache-2.0](https://developer.android.com/build/releases/gradle-plugin) | Build the application and library modules |
| Kotlin Gradle Plugin and `kotlin-stdlib`      | 2.4.20  | [Apache-2.0](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt) | Compile the Kotlin modules; the standard library ships in the application |
| `kotlin-test`                                 | 2.4.20  | [Apache-2.0](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt) | Test assertions (test scope only)                                       |
| JUnit Jupiter and JUnit Platform Launcher     | 6.1.3   | [EPL-2.0](https://github.com/junit-team/junit-framework/blob/main/LICENSE.md)  | Run the JVM tests of `:core:calc` and `:core:data` (test scope only)   |
| Kotlin Symbol Processing (`com.google.devtools.ksp`) | 2.3.12 | [Apache-2.0](https://github.com/google/ksp/blob/main/LICENSE) | Run the Room and Hilt annotation processors at build time |
| AndroidX Room (`room-runtime`, `room-compiler`, Gradle plugin) | 2.8.5 | [Apache-2.0](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/LICENSE.txt) | Local cache of quotations and the backtest series (ships in the application) |
| Dagger Hilt (`hilt-android`, `hilt-android-compiler`, Gradle plugin) | 2.60.1 | [Apache-2.0](https://github.com/google/dagger/blob/master/LICENSE.txt) | Dependency injection (ships in the application) |
| OkHttp (`okhttp`)                             | 5.5.0   | [Apache-2.0](https://github.com/square/okhttp/blob/master/LICENSE.txt) | HTTP client for the quotation sources (ships in the application) |
| Retrofit (`retrofit`)                         | 3.0.0   | [Apache-2.0](https://github.com/square/retrofit/blob/trunk/LICENSE.txt) | Declarative readers of the quotation sources (ships in the application) |
| kotlinx.serialization (`kotlinx-serialization-json`) | 1.11.0 | [Apache-2.0](https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt) | Parse the JSON of the quotation sources as text (ships in the application) |
| kotlinx.coroutines (`kotlinx-coroutines-core`) | 1.11.0 | [Apache-2.0](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt) | Suspending repositories and the spot memo lock (ships in the application) |
| OkHttp `mockwebserver3`                       | 5.5.0   | [Apache-2.0](https://github.com/square/okhttp/blob/master/LICENSE.txt) | Fake HTTP server for the reader tests (test scope only) |
| kotlinx.coroutines `kotlinx-coroutines-test`  | 1.11.0  | [Apache-2.0](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt) | Run suspending tests (test scope only) |
| AndroidX Test (`runner` 1.7.0, `ext:junit` 1.3.0, `core` 1.7.0) | see catalog | [Apache-2.0](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/LICENSE.txt) | Instrumented test of the Room DAOs and of the Compose screen on a device (androidTest scope only) |
| Compose Compiler Gradle plugin (`org.jetbrains.kotlin.plugin.compose`) | 2.4.20 | [Apache-2.0](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt) | Compile the `@Composable` functions (its version tracks Kotlin's) |
| Jetpack Compose BOM (`androidx.compose:compose-bom`) | 2026.09.00 | [Apache-2.0](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/LICENSE.txt) | Fix the version of every `androidx.compose.*` artifact below |
| Jetpack Compose UI (`ui`, `ui-graphics`, `ui-tooling-preview`) | via BOM | [Apache-2.0](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/LICENSE.txt) | The interface toolkit (ships in the application) |
| Jetpack Compose Material 3 (`material3`) | via BOM | [Apache-2.0](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/LICENSE.txt) | Native components the port's appearance is built on (ships in the application) |
| AndroidX Activity Compose (`activity-compose`) | 1.13.0 | [Apache-2.0](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/LICENSE.txt) | Host the composition in the activity, edge to edge (ships in the application) |
| AndroidX Lifecycle Compose (`lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`) | 2.11.0 | [Apache-2.0](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/LICENSE.txt) | Bind the `ViewModel` and collect its state with the lifecycle (ships in the application) |
| AndroidX Hilt Navigation Compose (`hilt-navigation-compose`) | 1.4.0 | [Apache-2.0](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/LICENSE.txt) | `hiltViewModel()` — the injected `ViewModel` inside a composable (ships in the application) |
| Jetpack Compose UI Test (`ui-test-junit4`, `ui-test-manifest`) | via BOM | [Apache-2.0](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/LICENSE.txt) | Drive the screen in the instrumented test (androidTest and debug scope only) |
| Espresso (`espresso-core`)                    | 3.7.0   | [Apache-2.0](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/LICENSE.txt) | Idling and input for the Compose test (androidTest scope only) |
| Jetpack Compose UI Tooling (`ui-tooling`) | via BOM | [Apache-2.0](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/LICENSE.txt) | Inspector and previews in the debug build only; absent from release |


| Component                          | Version | Commit SHA                                 | License                                                                                                          | Purpose                                                           |
| ---------------------------------- | ------- | ------------------------------------------ | ---------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| `actions/checkout`                 | v7.0.1  | `3d3c42e5aac5ba805825da76410c181273ba90b1` | [MIT](https://github.com/actions/checkout/blob/3d3c42e5aac5ba805825da76410c181273ba90b1/LICENSE)                 | Read repository content and complete Git history                  |
| `github/codeql-action`             | v4.38.0 | `b96794f015dfd88f77b49b1c93e0fa7110f94c63` | [MIT](https://github.com/github/codeql-action/blob/b96794f015dfd88f77b49b1c93e0fa7110f94c63/LICENSE)             | Upload Scorecard SARIF; CodeQL analysis uses native Default setup |
| `actions/dependency-review-action` | v5.0.0  | `a1d282b36b6f3519aa1f3fc636f609c47dddb294` | [MIT](https://github.com/actions/dependency-review-action/blob/a1d282b36b6f3519aa1f3fc636f609c47dddb294/LICENSE) | Review dependency changes in pull requests                        |
| `zizmorcore/zizmor-action`         | v0.6.4  | `cc914d7f3750a2d13d75c7f184a1060aa0e9d482` | [MIT](https://github.com/zizmorcore/zizmor-action/blob/cc914d7f3750a2d13d75c7f184a1060aa0e9d482/LICENSE)         | Audit GitHub Actions and upload SARIF                             |
| `ossf/scorecard-action`            | v2.4.4  | `2d1146689b8cda280b9bc96326124645441f03bc` | [Apache-2.0](https://github.com/ossf/scorecard-action/blob/2d1146689b8cda280b9bc96326124645441f03bc/LICENSE)     | Assess public supply-chain posture                                |
| `actions/upload-artifact`          | v7.0.1  | `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` | [MIT](https://github.com/actions/upload-artifact/blob/043fb46d1a93c77aae656e7c1c64a875d1fc6a0a/LICENSE)          | Retain the Scorecard SARIF; carry the Play-signed APK to release  |
| `actions/download-artifact`        | v8.0.1  | `3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c` | [MIT](https://github.com/actions/download-artifact/blob/3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c/LICENSE)        | Fetch the Play-signed APK in the release job                      |
| `actions/attest`                   | v4.2.2  | `1e69f48acb82d1966a394da916b4c1698aa569d6` | [MIT](https://github.com/actions/attest/blob/1e69f48acb82d1966a394da916b4c1698aa569d6/LICENSE)                   | Build provenance attestation for the released APK                 |
| `actions/configure-pages`          | v6.0.0  | `45bfe0192ca1faeb007ade9deae92b16b8254a0d` | [MIT](https://github.com/actions/configure-pages/blob/45bfe0192ca1faeb007ade9deae92b16b8254a0d/LICENSE)          | Configure the Pages build                                         |
| `actions/upload-pages-artifact`    | v5.0.0  | `fc324d3547104276b827a68afc52ff2a11cc49c9` | [MIT](https://github.com/actions/upload-pages-artifact/blob/fc324d3547104276b827a68afc52ff2a11cc49c9/LICENSE)    | Upload the sanitized `site/` artifact                             |
| `actions/deploy-pages`             | v5.0.1  | `368f82528645a54fb793d4d04e342629a3f51346` | [MIT](https://github.com/actions/deploy-pages/blob/368f82528645a54fb793d4d04e342629a3f51346/LICENSE)             | Deploy the trusted Pages artifact                                 |
| `linear/linear-release-action`     | v0.18.0 | `d4af10092984f9bc6d5efa075b242bdf01333463` | [MIT](https://github.com/linear/linear-release-action/blob/d4af10092984f9bc6d5efa075b242bdf01333463/LICENSE)     | Create a release in the corresponding Linear pipeline             |

`github/codeql-action` is MIT-licensed. Native CodeQL Default setup manages the
analysis and its CLI bundle; this repository pins the Action only for SARIF
upload. The CodeQL CLI is separately governed by the immutable
[GitHub CodeQL Terms and Conditions](https://github.com/github/codeql-cli-binaries/blob/0d65148c254764ec294892a35e644accd5677ed5/LICENSE.md)
and the Enterprise GitHub Code Security entitlement.

The official Linear Release action selects CLI v0.18.0 explicitly. The local
Dependabot workflow uses the GitHub-hosted runner's official `gh` CLI to enable
GitHub native auto-merge; it does not add a custom application or controller.

## Repository license

The original repository content is licensed under GNU AGPL-3.0-or-later; see
[LICENSE](LICENSE) and [NOTICE](NOTICE). Third-party licenses apply only to
their respective components.
