# Agents for the Bort SDK (`sdk/bort`)

Root instructions in [../../AGENTS.md](../../AGENTS.md) still apply (conda env,
`inv` usage, lint tasks). This file only covers what is specific to Bort.

## What this is

Bort is the Memfault **Android SDK**: a set of AOSP system apps plus native
daemons that get built into a customer's platform image. Two important
consequences:

- **This directory is published to customers.** It is mirrored to the public
  `memfault/bort` repo (and `memfault/bort-lite`). Anything internal-only lives
  under `_internal/`, which is stripped from public releases. Do not put
  internal notes, keys, or Memfault-only tooling anywhere else.
- **Customer-visible changes need a `CHANGELOG.md` entry**, in the Keep a
  Changelog format already used there, under the unreleased/current version
  heading and the right `### :rocket: New Features` / `### :construction: Fixes`
  section.

Read [`_internal/DEVELOPMENT.md`](_internal/DEVELOPMENT.md) before doing build
or emulator work. It is the authoritative workflow doc and covers the AOSP dev
container, gradle property setup (`_internal/ci-gradle.properties`), Bort Lite,
OTA, and publishing `reporting-lib`.

## Layout

| Path                       | What                                                                      |
| -------------------------- | ------------------------------------------------------------------------- |
| `MemfaultPackages/`        | The gradle build. All Kotlin/Java apps and libs live here.                |
| `MemfaultPackages/bort/`   | The main Bort app (collection, upload, settings, metrics).                |
| `bort-shared/`             | Code shared between Bort, UsageReporter and OTA.                          |
| `reporter/`                | UsageReporter, the privileged helper app.                                 |
| `bort-ota/`, `-ota-lib/`   | The OTA app. Only configured when OTA is enabled (see `settings.gradle`). |
| `reporting-libs/`          | Public Custom Metrics API, published to Maven Central.                    |
| `MemfaultDumpster/`        | C++ daemon (dumpstate/logcat/property access).                            |
| `MemfaultStructuredLogd/`  | C++ structured logging daemon, with its own SQLite storage.               |
| `MemfaultDumpstateRunner/` | C++ helper that runs `dumpstate` with the right permissions.              |
| `sepolicy/`                | SELinux policy, per Android version (`private-28` … ).                    |
| `patches/`                 | AOSP patches per target release (`android-7` … `android-17`).             |
| `bort_cli.py`              | Customer-facing CLI (patching AOSP, validating an integration).           |
| `_internal/`               | Memfault-only: dev docs, keystores, CI gradle props, branch overlays.     |

Python here (`bort_cli.py`, `MemfaultPackages/bort_src_gen.py`) is deliberately
conservative and standalone. `bort_src_gen.py` must stay Python 3.4 compatible
(type annotations in comments), since it runs inside customer AOSP builds.
`bort_cli.py` is linted by this directory's own `ruff.toml`.

## Commands

Run these from anywhere in the monorepo:

| Action                | Command                                                |
| --------------------- | ------------------------------------------------------ |
| Unit tests            | `inv aosp.bort.test`                                   |
| Tests + lint + ktlint | `inv aosp.bort.check`                                  |
| Format Kotlin         | `inv aosp.bort.ktlint-format`                          |
| Typo check            | `inv lint.bort-typos` (`lint.bort-typos-fix` to apply) |
| Build APKs            | `inv aosp.bort.build-apk`                              |
| Clean                 | `inv aosp.bort.clean`                                  |

`--offline` is supported on the test/check/format tasks and is much faster once
dependencies are cached.

To run a single test class, use gradle directly from `MemfaultPackages/`:

```shell
./gradlew :bort:testReleaseUnitTest --tests '*DeviceInfoTest*'
```

Note that a bare `./gradlew` needs `MEMFAULT_PROJECT_API_KEY` and
`BORT_KEYSTORE_PROPERTIES_PATH` in `~/.gradle/gradle.properties` or gradle sync
fails. See `_internal/DEVELOPMENT.md`.

E2E tests are **not** in this directory. They live in
`py-packages/mflt-bort-e2e` and run against an emulator (`inv bort-e2e.test`, or
`BORT_LITE=1 inv bort-e2e.test` for Bort Lite).

## Code conventions

Kotlin, formatted by ktlint with `ktlint_code_style = android_studio`, 4-space
indent, 120 column limit. The full config is `MemfaultPackages/.editorconfig`,
so let `ktlintFormat` decide rather than hand-formatting.

- **DI is Dagger/Hilt with Anvil contributions.** New collectors, uploaders and
  providers are bound via `@ContributesBinding` / `@ContributesMultibinding`
  rather than added to a module by hand. See `BortAnvil.kt` and
  `BortAppModule.kt`.
- **Tests** use JUnit 4, [assertk](https://github.com/willowtreeapps/assertk)
  (`assertThat(...).isEqualTo(...)`), mockk, and Robolectric where an Android
  runtime is needed. Prefer the existing hand-written `Fake*` classes in
  `bort/src/test/.../Fake*.kt` over mocking where one already exists.
- **Coroutines and Flow** throughout, with WorkManager for scheduled work.
  Injected dispatchers, not `Dispatchers.IO` at call sites.
- Source sets are split `main` / `debug` / `release`, and some modules have
  `testFixtures`. Debug-only behavior belongs in `src/debug`.

## SDK settings

Settings are server-pushed and read through `SettingsProvider`. Adding one
touches several places:

- The Kotlin accessor, in `bort/src/main/java/com/memfault/bort/settings/`
  (`Settings.kt`, `DynamicSettingsProvider.kt`) or `bort-shared/.../settings/`
  if UsageReporter or OTA also needs it.
- `FetchedSettings.kt` in `bort-shared`, for deserialization of the server
  payload.
- `MemfaultPackages/settings/settings.json`, the bundled defaults used before
  the first fetch.
- The backend side of the setting, which lives outside this directory in the
  main web app.

## Android version compatibility

Bort supports Android 7 through 17 from one source tree, in two ways:

- **Patches**: per-release AOSP patches in `patches/android-N/`, applied by
  `bort_cli.py`.
- **Branch overlays**: files that cannot be shared between SDK versions live in
  `_internal/branches/<branch>/<same relative path>` with one copy per branch,
  not in the main tree. Overlays are applied at build time and released to the
  matching public branch. See `_internal/branches/README.md`.

If you change something version-sensitive, check whether a branch overlay copy
of the file exists and needs the same change.
