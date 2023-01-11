# Memfault Bort Changelog

## v4.4.0 - January 11th, 2022

#### :rocket: New Features

- High-Res Telemetry. Custom Metrics are now shown on timeline in high
  resolution, in addition to aggregated values.
  [See documentation](https://docs.memfault.com/docs/android/android-custom-metrics).
  - Adds new `event` metric type. This will replace Custom Events.
  - Makes aggregations optional for various metric types.
  - Changes signature of `aggregations` parameter from `vararg` to `<List>`.
- New built-in connectivity metrics: `connectivity.type`, `airplane_mode`,
  `connectivity.validated`, `connectivity.captive_portal`.
  [See documentation](https://mflt.io/android-builtin-metrics).
- Support Fleet Sampling when using
  [client/server mode](https://docs.memfault.com/docs/android/android-multi-device-support).

#### :chart_with_upwards_trend: Improvements

- Fixed the `bort_cli.py` python version check (was using a bad string-basd
  check).
- Continuous Logging mode now works on Android 13.
- Filter `DropBoxManager` intents in UsageReporter - don't wake Bort for unused
  tags.
- Fix Custom Metric `MIN`/`MAX` aggregations. These were using a lexicographical
  sort, which could result in incorrect results.
- Bypass WTF rate-limiting when Dev Mode is enabled.
- Flag log uploads containing a kernel oops.
- Enable `-fstack-protector-all` for MemfaultDumpster to enable easier debugging
  of crashes.
- Fixed an issue in Continuous Logging which could cause a parsing failure in
  frontend (spelling of "beginning").
- Add collection mode metadata to log uploads.
- Adjust `mar` file upload jitter to match batching period.

#### :house: Internal

- Migrates Bort internal metrics to use High-Res Telemetry.

## v4.3.2 - November 22nd, 2022

#### :chart_with_upwards_trend: Improvements

- Fixed an issue where bugreports may fail to be processed by Bort if the Bort
  OTA app is installed on the device.

## v4.3.1 - November 7th, 2022

#### :chart_with_upwards_trend: Improvements

- Fixed an issue which could cause batterystats collection to fail after some
  time-change events.
- Fixed an issue in WTF on-device rate-limiting.

## v4.3.0 - October 18th, 2022

#### :rocket: New Features

- Continuous Logging Mode is in preview. When enabled, logs will be continuously
  collected by the `MemfaultDumpster` service. This is designed to avoid missing
  logs because of buffer expiry.
  [See documentation](https://mflt.io/android-logging). For devices running
  Android 8/9/10 a new AOSP patch is required for Continuous mode to work.
- On-device scrubbing of tombstone files.
  [See documentation](https://mflt.io/android-data-scrubbing).

#### :chart_with_upwards_trend: Improvements

- Built-in metrics for Storage have been changed. Bort does not collect
  `storage.primary.` metrics any more - instead collecting `storage.data.`
  metrics - [see documentation](https://mflt.io/android-builtin-metrics).
- All Bort sepolicy files now have leading/trailing newlines, to avoid bad
  merges during build.
- Added logging for client/server disconnection events.
- WTFs are rate-limited separately from Java exceptions.
- New runtime integration validation check.
- Fixed a race condition in the OTA app, which could cause a crash when opening
  the UI.

#### :house: Internal

- Improved how settings are overridden during CI tests.

## v4.2.0 - September 14th, 2022

#### :rocket: New Features

- Fleet Sampling, including new Memfault Log Collection.
- Support for Android 12L.
- Support for Android 13.
- Adds a Development Mode to Bort, to make the integration and testing
  experience easier. [See documentation](https://mflt.io/android-dev-mode).
- C/C++ API for reporting custom metrics (not yet documented).
- OTA app can now be configured to auto-install updates (with no user
  intervention) by setting the `OTA_AUTO_INSTALL` property to `true` in
  `bort.properties`. In the future, it will be possible to override this in the
  dashboard. Also adds a `custom_canAutoInstallOtaUpdateNow` callback (in
  `autoInstallRules.kt`) to customize this logic.
  [See documentation](https://mflt.io/android-ota-background).
- Custom log scrubbing rules can be defined in `CustomLogScrubber.kt`.
  [See documentation](https://mflt.io/android-custom-scrubbing).

#### :chart_with_upwards_trend: Improvements

- OTA will now automatically reboot after applying an A/B update.
- Adds configurable limits to `mar` file size. Multiple bundled `mar` files may
  now be created if over the limit.
- Adds configurable limits for total `mar` file storage (by size/age).
- Fixed an issue where devices running a Qualcomm QSSI Android 12 build may fail
  to collect custom metrics.
- The OTA app can now fall back to using default settings if it cannot fetch
  updated settings from the Bort app.
- Force Bort's OTA settings ContentProvider to be queryable, to work around an
  issue on some devices where the OTA app could not request settings from the
  Bort app.
- Updated several libraries/tools used by Bort, including Gradle, AGP, Dagger,
  etc.
- Improved Bort build speed, and removed many spammy java/kotlin warnings during
  compilation.
- Fixed an issue where the `mar` bundling task may not get scheduled (causing no
  files to get uploaded until the next device reboot) after changing a specific
  combination of SDK settings.
- Made zip compression level configurable.
- Adds an OTA update check task to the Bort CLI:
  `bort_cli.py ota-check --bort-ota-app-id your.bort.ota.app.id`
- Fixed an issue where metrics service could not create files on some devices
  due to encryption state.
- Added a compile-time check to ensure that Bort SELinux policy has been
  correctly applied.
- The OTA app will now continue to check for updates while waiting to
  download/install.

#### :house: Internal

- Added internal metrics around `mar` storage/usage.
- Changed the default API endpoint from `api.memfault.com` to
  `device.memfault.com` (to match remote settings configuration).
- Added the device software version to the `device-config` server call.
- Bort can optionally fetch device configuration from new `/device-config`
  endpoint.

## v4.1.0 - April 18, 2022

#### :chart_with_upwards_trend: Improvements

- Enhanced the
  [SDK validation tool](https://docs.memfault.com/docs/android/android-getting-started-guide#validating-the-sdk-integration)
  to check that the `MemfaultStructuredLogD` (Bort's metric collection service)
  is correctly configured.
- In a client/server configuration, Bort SDK settings are now forwarded from the
  server ot the client.
- Added a maximum file storage limit on the client, in client/server mode.
- Updated several tool versions (Gradle, AGP, Dagger, etc).
- Fixed a potential crash in the metric reporting service, when using State
  Tracker metrics.
- Compiler and sepolicy fixes for Qualcomm/QSSI Android 12 release.
- Fixes an issue in `MemfaultStructuredLogD` which could cause an error during
  startup or shutdown when storage is not available.

## v4.0.0 - February 09, 2021

4.0.0 includes all changes listed for 4.0.0-RC1 below.

See documentation for new
[Built-In Metrics](https://docs.memfault.com/docs/android/android-builtin-metrics)
and
[Custom Metrics APIs](https://docs.memfault.com/docs/android/android-custom-metrics).

#### :chart_with_upwards_trend: Improvements

- Note: The Bort gradle build now requires JDK 11 (this is a requirement for the
  latest version of the Android Gradle Plugin).
- Updated several dependencies, including Gradle 3.7.2 and Kotlin 1.6.10.
- Updated patch descriptions.
- Fixed an issue where custom metrics may not be enabled on some devices.

## v4.0.0-RC1 - December 15, 2021

This preview release contains several features which do not yet have full
documentation.

#### :rocket: New Features

- Custom Metrics SDK. See the `Reporting` APIs in `reporting-lib`, which is also
  published as an artifact to Maven Central as
  `com.memfault.bort:reporting-lib:1.0`.
- Android 12 support.
- Bort supports running in client/server mode, on configurations containing
  multiple connected devices.
- Bort records storage usage metrics - these are captured automatically.
- Bort records CPU/skin temperature metrics - these are captured automatically.
- Bort can capture selected installed app versions as Device Attributes. This is
  configurable in the dashboard, on the Data Sources tab.
- Bort can capture selected system properties as Device Attributes. This is
  configurable in the dashboard, on the Data Sources tab.

#### :chart_with_upwards_trend: Improvements

- Bort will now only run as the primary user, on devices with multiple users.
- Bort is updated to use the latest version of several tools & libraries
  (Gradle, Kotlin, Android Gradle Plugin, etc) and a `compileSdkVersion` of 31.
- Fixed an SELinux violation when Bort accesses DropBoxManager entries on a
  recent targetSdkVersion.
- Fixed a race condition which caused a crash when Bort was started by a
  ContentProvider query.
- Fixed an edge-case in DropBoxManager processing, which could cause an entry to
  be missed.

#### :boom: Breaking Changes

- `BUILD_ID_CODE` in `bort.properties` is replaced by `BUILD_ID_CODE_OVERRIDE`.
  This is now an override, which is disabled by default - and should only be
  uncommented if required (SDK updates will always increment the build ID
  internally, so this will not normally be needed).
- The `custom-event-lib` library has been removed. The `CustomEvent` API is now
  located in the `reporting-lib` library, which is also now published as an
  artifact to Maven Central as `com.memfault.bort:reporting-lib:1.0`.

#### :house: Internal

- Bort now uses Maven Central instead of JCenter for dependencies.
- Added support for the `mar` (Memfault Archive) file format - this enables
  bundling data into a batched file upload, instead of uploading a separate file
  for every piece of data. This is not yet enabled by default.
- Bort is migrated to use the Dagger dependency injection framework.

## v3.7.3 - October 26, 2021

#### :chart_with_upwards_trend: Improvements

- Fix a potential crash after requesting a bugreport via Intent.
- Sepolicy changes to allow Bort+OTA to run on the latest build of Android 11,
  and with a target SDK version of 30.
- Enable requesting an OTA update check via Intent
  (`com.memfault.intent.action.OTA_CHECK_FOR_UPDATES`).

#### :house: Internal

- Added ability for bort to log to disk, to aid debugging. This is disabled by
  default.

## v3.7.2 - October 18, 2021

#### :chart_with_upwards_trend: Improvements

- Fix a crash when using A/B OTA updates.
- Disable debug variant.
- Add system properties for Bort SDK/patch versions.
- Improved internal observability.
- Improve rate-limiting behavior after a reboot.

## v3.7.1 - September 30, 2021

#### :chart_with_upwards_trend: Improvements

- Fix a rate-limiting issue which sometimes caused regular data collection to be
  missed.
- Added internal logging for the upstream Bort version code.

## v3.7.0 - September 15, 2021

#### :rocket: New Features

- OTA Update Client: Bort now includes a full OTA Update client.
  [See documentation](https://mflt.io/android-ota-update-client) for more
  information.

#### :chart_with_upwards_trend: Improvements

- Enable Bort to collect internal logs when custom log scrubbing rules are in
  place.
- Fix an issue during SDK integration, where Bort sepolicy files can be included
  multiple times, resulting in build errors.
- Added the ability to collect kernel oops' in Caliper.
- Fix an issue causing a CTS/GMS test failure.

## v3.6.2 - August 17, 2021

#### :chart_with_upwards_trend: Improvements

- Fix "Bort enabled" log (previously included incorrect "enabled" value).
- Improve Bort internal logging and metrics.
- Make periodic bugreport rate-limiting remotely controlled. This fixes an issue
  where some bugreport collection was being rate-limited.

## v3.6.1 - July 21, 2021

#### :chart_with_upwards_trend: Improvements

- In 3.6.0, the OTA app had ot be configured with an application ID and
  certificate (even though not being included in the build). This is fixed - OTA
  app configuration is no longer required when not being used.

## v3.6.0 - July 9, 2021

#### :boom: Breaking Changes

- Structured Logging (introduced in 3.5.0) has been renamed to Custom Events.
  Any code using the `StructuredLog` API from the `structured-log-lib` library
  (now removed) must be changed to call the equivalent methods in the
  `CustomEvent` API from the `custom-event-lib` library.

#### :chart_with_upwards_trend: Improvements

- Fixed Custom Events (formerly: Structured Logs) on devices where
  `RUNTIME_ENABLE_REQUIRED=false`. These devices would fail to upload events.
- Fixed SDK validation script (`bort_cli.py --validate-sdk-integration`) when
  running on Windows (this previously reported incorrect failures).
- Added new metrics and Custom Events reporting the behavior and impact of the
  Bort SDK, to enable Memfault to track down any issues that may arise with
  Bort.
- The SDK patch script now works correctly when cuttlefish emulator directories
  are missing.

#### :house: Internal

- OTA update client: a new OTA update client is included in this release. This
  is not yet ready for use (and is not yet documented), and is not included when
  building. The new OTA update client will be available in a future Bort
  release.

## v3.5.0 - June 2, 2021

#### :rocket: New Features

- Structured logging: this new API enables logging structured events, which
  appear directly on the device timeline
  [See documentation](https://docs.memfault.com/docs/android/structured-logs)
  for more information.
- Android 11 support for the Bort SDK.

#### :chart_with_upwards_trend: Improvements

- Fixed a problem where BatteryStat-based system metrics stop being collected if
  the battery was fully charged and still plugged. To resolve this, patches for
  `frameworks/base` have been added to the SDK. The command
  `bort_cli.py patch-aosp --android-release $RELEASE` will need to be run again
  to apply this new patch.
- The bort apk is now backwards-compatible with older SDK versions (from 2.2.4
  onwards). This means that the bort app can safely be updated (via Play Store
  or an MDM tool) without a full system image update. Not all new bort apk
  features will be available, depending on the bort SDK version installed in the
  system image.
- Added the ability to configure storage limits (disk space or age) for
  bugreports stored on the device.
- Populate the "captured timestamp" for DropBox-sourced items.

## v3.4.3 - April 27, 2021

#### :chart_with_upwards_trend: Improvements

- Fix a rare issue in the on-device rate limiting system.
- Make HTTP request timeout a configurable parameter.
- Add randomized delay to uploads to distribute fleet-wide requests over time.

#### :house: Internal

- Structured logging: this is an experimental new feature still in development.

## v3.4.2 - March 17, 2021

#### :chart_with_upwards_trend: Improvements

- Capture log files, even when empty, to make it clear the SDK is working as
  expected.
- Fixed 2 bugs in the Dymamic SDK Settings code that 1) caused changes in the
  polling interval to not get applied until Bort got restarted or
  disabled/re-enabled 2) caused most periodic collection tasks to restart, upon
  any change in Dynamic SDK Settings, even if the change was unrelated to the
  periodic collection tasks.
- Fixed a bug in the logcat capturing code that caused noisy, but harmless logs
  of a (handled) exception.
- Fixed an issue in the logcat capturing code that caused the start time for the
  next capture to be not as accurate as it should be, which could lead to
  missing log lines between captures.
- Fixed a bug that could sometimes cause Bort to crash under rare circumstances,
  when reconnecting to MemfaultUsageReporter.

## v3.4.1 - March 11, 2021

#### :chart_with_upwards_trend: Improvements

- Added a `dontaudit` sepolicy line to avoid failing a CTS test. See
  `memfault_dumpster.te` for more information.

## v3.4.0 - March 8, 2021

#### :rocket: New Features

- On-device data scrubbing: Caliper traces pertinent to allowed application IDs
  are uploaded and others are filtered out, on-device. Similarly,
  Caliper-captured log lines are scrubbed before they are uploaded, based on
  allowed application IDs and configured text scrubbing rules. The data
  scrubbing rules can be configured through the Memfault Settings UI. Note that
  bug reports are still scrubbed server-side. See
  [data scrubbing documentation](https://mflt.io/android-data-scrubbing) for
  more information.

#### :chart_with_upwards_trend: Improvements

- Hard-coded, 5-second command (i.e. logcat) timeouts have been removed and
  replaced with remotely configurable timeouts.
- DropBoxEntry tags can be excluded entirely from uploading. The list of tags to
  exclude can be configured in the Memfault Settings / Data Sources UI.
- Fixed/avoided a problem relevant to Android 8.x, where Bort would
  occassionally crash due to bugs in the AOSP platform code.

## v3.3.1 - February 19, 2021

#### :chart_with_upwards_trend: Improvements

- Improved the gradle build scripting to prevent accidentally replacing the
  `MemfaultPackages/settings/settings.json` with different settings. In case the
  local file contains settings that differ from what has been configured in the
  Memfault web service, the build is aborted and a
  [RFC 6902 JSON diff](https://tools.ietf.org/html/rfc6902) is printed to aid
  the user, as well as suggestions on how to fix the issue.

## v3.3.0 - February 17, 2021

#### :rocket: New Features

- Dynamic SDK Settings: at run-time, Bort will now periodically fetch the SDK
  settings from the Memfault web service, making it possible to change the
  settings remotely, after shipping. Many settings that were previously
  specified as build-time constants in `bort.properties` must now be configured
  in the Memfault web app via your project's Settings page. When building
  MemfaultBort.apk, the gradle build script will download the settings and place
  it in a file called `MemfaultPackages/settings/settings.json`. This file is
  used as initial SDK settings and also as fall-back, in case the settings from
  the web service cannot be used.
- A new "Caliper" data source has been added to collect logcat logs when issues
  occur. By default, logs are collected in hunks of 15 minutes and are uploaded
  only if issues occurred around that span of time. In the Memfault UI, the logs
  can be found through the timeline of a device, or under the "Logs" tab of an
  Issue detail page.

#### :chart_with_upwards_trend: Improvements

- File uploads are now gzip-compressed prior to uploading to save bandwidth.
- A built-in metric `MemfaultSdkMetric_bort_version_code` has been added that
  contains Bort's version code.

#### :house: Internal

- Fixed a bug that caused overly aggressive rate limiting under certain
  conditions.
- All periodic data collection tasks are now also rate limited, to avoid
  uploading more data than desirable in case those tasks are triggered by
  repeatedly enabling/disabling of the SDK.

## v3.2.0 - February 4, 2021

#### :chart_with_upwards_trend: Improvements

- New built-in metrics have been added that count the number of traces by tag.
  The naming format is `drop_box_trace_%s_count` where `%s` is filled with the
  tag, i.e. `system_tombstone`.
- A new built-in metric called `drop_box_traces_drop_count` has been added that
  counts the number of dropped traces due to rate-limiting.

#### :house: Internal

- Rate limiting: Bort will now also rate limit reboot event uploads.
- A bug has been fixed that caused project keys for newly created projects to
  fail the gradle build.

## v3.1.0 - January 29, 2021

#### :chart_with_upwards_trend: Improvements

- A new option is available to label a bug report that is manually triggered via
  the intent-based API (the action
  `com.memfault.intent.action.REQUEST_BUG_REPORT`). When the optional ID is
  provided, the `dumpstate.memfault.requestid` system property is set to the
  value provided. Additionally, a BroadcastReceiver can be specified to which
  the status of the bug report request will be reported by Bort. See
  https://mflt.io/android-bort-sdk for details.
  > NOTE: in order to use this feature, the AOSP system image has to be updated
  > (merely updating MemfaultBort.apk is not sufficient, because this feature
  > also involved a change in the MemfaultDumpstateRunner system component).
- Added a build-time check that runs as part of the AOSP build, to ensure the
  MemfaultBort.x509.pem signing certificate matches the signature of
  MemfaultBort.apk.
- Improved `bort_cli.py`'s SDK validation by also printing more details about
  the reason of a failed validation.

#### :house: Internal

- Rate limiting: Bort will now rate limit uploads to the Memfault web service,
  to avoid faulty devices from uploading too much data.
- Bug fix: avoid uploading empty trace files.

## v3.0.1 - January 4, 2021

#### :house: Internal

- Fixed a bug that caused `BUG_REPORT_MINIMAL_MODE=true` to only enable minimal
  mode bug reports to get captured after rebooting but not after enabling the
  SDK at run-time.
- Remove a spammy log message.

## v3.0.0 - December 17, 2020

#### :chart_with_upwards_trend: Improvements

- Have you ever wondered how the metrics in your device timelines are trending
  across your whole fleet? With the 3.0.0 version of Bort, now you can view
  visualizations of these metrics aggregated across all your devices! See
  https://mflt.io/fleet-wide-metrics for details.
- The aforementioned "experimental" data source is now ready for prime time! It
  can be used to collect traces and metrics, with additional data sources coming
  in future SDK releases. See https://mflt.io/memfault-caliper for details.
- Reboot events were added in an earlier version of the SDK but didn't make it
  into the changelog. Let's call that a 3.0 feature too! See
  https://mflt.io/android-reboot-reasons to learn more.

## v2.9.1 - December 3, 2020

#### :chart_with_upwards_trend: Improvements

- The `bort_src_gen.py` tool used to require Python 3.6 to run, but it is now
  compatible down to Python 3.4.

## v2.9.0 - November 26, 2020

#### :house: Internal

- Experimental DropBoxManager API based data source:
  - Added uploading of DropBox-sourced ANRs, Java exceptions, WTFs, last kmsg
    logs and Tombstones with "native backtrace" style dumps.
  - Improved DropBox file upload, reusing existing upload worker task that is
    also used to upload bug reports.

## v2.8.0 - November 13, 2020

#### :house: Internal

- Experimental DropBoxManager API based data source:
  - Refactored parts of the infrastructure in UsageReporter and Bort apps to
    collect DropBox entries, logcat logs, etc.
  - Added infra to collect installed package metadata.
  - Added a preliminary uploader for DropBox-sourced tombstones.
  - Fixed a bug where the DropBox entry collection process would miss entries
    after a backwards RTC time change.

## v2.7.1 - November 5, 2020

#### :chart_with_upwards_trend: Improvements

- The `bort_cli.py` tool now only writes to a validation log file when running
  the validation command.

#### :house: Internal

- Add a backtrace when sending an error response in IPC between the
  UsageReporter and Bort apps.

## v2.7.0 - October 28, 2020

#### :chart_with_upwards_trend: Improvements

- Any custom configuration of Hardware and Software Versions as well as Device
  Serial sources is now automatically retrieved by the SDK from the Memfault
  backend during the build process by a gradle task. This means it is no longer
  necessary to manually configure the following properties:
  - `ANDROID_HARDWARE_VERSION_KEY`
  - `ANDROID_DEVICE_SERIAL_KEY`
  - `ANDROID_BUILD_VERSION_SOURCE`
  - `ANDROID_BUILD_VERSION_KEY`

## v2.6.0 - October 27, 2020

#### :chart_with_upwards_trend: Improvements

- Added the option to capture "minimal" bug reports. These collect the data
  required for Memfault diagnostics while being roughly 5x smaller and requiring
  10x less load on the system. If system load or bandwidth are concerns for your
  deployment, we recommend using minimal mode. See `BUG_REPORT_MINIMAL_MODE` in
  `bort.properties`.
- Added options to enable/disable the data sources that Bort uses to collect
  traces. By default, only bug reports are used
  (`DATA_SOURCE_BUG_REPORTS_ENABLED=true`). We are working on adding a new data
  source that uses Android's DropBoxManager API. This is still experimental and
  is therefore disabled (`DATA_SOURCE_DROP_BOX_ENABLED=false`).
- Memfault now supports custom configuration of the Hardware and Software
  Versions as well as Serial Number sources by specifying which system
  properties to read. New SDK properties must be configured to enable this. See
  the following properties in `bort.properties`:
  - `ANDROID_HARDWARE_VERSION_KEY`
  - `ANDROID_DEVICE_SERIAL_KEY`
  - `ANDROID_BUILD_VERSION_SOURCE`
  - `ANDROID_BUILD_VERSION_KEY`
- The `bort_cli.py` validation command now uses rotating log files and performs
  additional checks to ensure the bort SDK integrated into the system image is
  consistent with the APKs installed.

#### :house: Internal

- `ktlint` was applied to the codebase, resulting in reformatting of many files.
- Logcat log fetching has been added to the experimental
  `DATA_SOURCE_DROP_BOX_ENABLED` feature

## v2.5.0 - October 20, 2020

#### :chart_with_upwards_trend: Improvements

- Remove reliance on `BOARD_PLAT_PRIVATE_SEPOLICY_DIR` for Android 8.1 or older
  platforms. This makefile variable is supposed to be set to only a single
  directory on these older platforms and was causing issues when the variable
  was already used for other purposes than Bort. As a work-around,
  `BOARD_PLAT_PRIVATE_SEPOLICY_DIR` is now used only on Android 9 and newer. For
  older Android versions, Bort's private sepolicy changes are patched directly
  into `system/sepolicy`. See `BoardConfig.mk` and
  `patches/android-8/system/sepolicy/git.diff`.

## v2.4.1 - October 9, 2020

#### :chart_with_upwards_trend: Improvements

- Additional HTTP logging

## v2.4.0 - September 29, 2020

#### :chart_with_upwards_trend: Improvements

- Before this release, the Bort application ID and feature name had to be
  patched in various files, using the `bort_cli.py` tool. With this release,
  `BORT_APPLICATION_ID` and `BORT_FEATURE_NAME` properties have been added to
  the `bort.properties` file. Files that had to be patched previously, are now
  generated at build-time, using the `BORT_APPLICATION_ID` and
  `BORT_FEATURE_NAME` from the `bort.properties` file.
- The `bort_cli.py` command `patch-bort` has been changed to only patch the
  `bort.properties`' `BORT_APPLICATION_ID` and `BORT_FEATURE_NAME` properties if
  they have not already been set.

#### :house: Internal

- The target SDK is now a configurable SDK property (via `bort.properties`)
- The default target SDK has been lowered 26 from 29 for more consistent
  behaviour across the supported platforms.
- The min SDK version has been lowered to 26 to support Android 8.

#### :boom: Breaking Changes

- The folder structure of the SDK has been changed: the `MemfaultBort` and
  `MemfaultUsageReporter` folders have been merged into a single
  `MemfaultPackages` folder. The two Android Studio projects have been merged
  into a single project with both `MemfaultBort.apk` and
  `MemfaultUsageReporter.apk` targets.
- To conform to Android conventions, the SDK is now expected to be installed at
  `<AOSP_ROOT>/vendor/memfault/bort` instead of
  `<AOSP_ROOT>/packages/apps/bort`.
- The `bort_cli.py` command `patch-dumpstate-runner` has been removed. The
  command is no longer needed. Instead of patching the .cpp source code, a
  header file is now generated based on the `MemfaultPackags/bort.properties`
  file.

## v2.3.0 - August 20, 2020

#### :boom: Breaking Changes

- Controlling the Bort SDK, either enabling it or requesting a bug report, now
  requires the `com.memfault.bort.permission.CONTROL` permission. This
  permission may not be used by regular apps. This permission may only be
  granted to applications signed with the same signing key as `MemfaultBort`
  (`signature`) or applications that are installed as privileged apps on the
  system image (`privileged`). See
  [Android protectionLevel](https://developer.android.com/reference/android/R.attr#protectionLevel)
  for further documentation.
- Broadcasts to control the SDK (enabling/disabling, requesting a bug report)
  have been consolidated into a single, new receiver
  (`com.memfault.bort.receivers.ControlReceiver`).

#### :house: Internal

- Log events when the SDK is enabled and disabled.

#### :chart_with_upwards_trend: Improvements

- There is a new `bort.property` called `BORT_CONTROL_PERMISSION`, used to
  specify which permission should be used to control the SDK. By default, this
  is property is set to `com.memfault.bort.permission.CONTROL`.
- Improved `bort_cli.py`'s `validate-sdk-integration` command to also check the
  ownership and sepolicy context of key SDK files.

## v2.2.4 - July 14, 2020

#### :rocket: New Features

- Adds `enable-bort` and `request-bug-report` commands to `bort_cli.py`.

#### :chart_with_upwards_trend: Improvements

- Adds fixes for the `validate-sdk-integration` command in `bort_cli.py` when
  being run on Windows.

## v2.2.3 - July 8, 2020

#### :rocket: New Features

- Validate your SDK integration with the `validate-sdk-integration` command in
  `bort_cli.py`.

#### :chart_with_upwards_trend: Improvements

- The SDK will now work if
  [UserManager.DISALLOW_DEBUGGING_FEATURES](https://developer.android.com/reference/android/os/UserManager#DISALLOW_DEBUGGING_FEATURES)
  is enabled.

## v2.2.2 - June 29, 2020

#### :chart_with_upwards_trend: Improvements

- Easily specify V1 and/or V2 APK signing configurations for the MemfaultBort
  app using `bort.properties`.

#### :house: Internal

- The `bort_cli.py` script now requires Python 3.6+

## v2.2.1 - June 24, 2020

#### :boom: Breaking Changes

- The `versionCode` and `versionName` are now set by default by the SDK. If you
  need to override them or increase the `versionCode` for an OTA update, see
  `bort.properties`.

#### :chart_with_upwards_trend: Improvements

- Fixed `bort_cli.py` to patch the custom application ID into the permissions
  XML. This fixes an issue where the MemfaultBort application was not being
  granted the expected permissions.

#### :house: Internal

- More debug info in requests to track and debug different SDK behaviour.
- Use the `EventLog` API to log SDK events.
- Update `MemfaultBort` dependencies.

## v2.2.0 - June 15, 2020

#### :rocket: New Features

- This release adds an intent-based API to enable or disable the SDK at runtime.
- Additionally, there is new requirement that the SDK be explicitly enabled
  using this API at runtime. This can be disabled via a gradle property,
- There is now also an intent-based API to trigger a one-off bug report.

## v2.1.0 - June 2, 2020

#### :rocket: New Features

- Adds the ability to upload bug reports to a user-specified endpoint

#### :chart_with_upwards_trend: Improvements

- The `storeFile` property in a `keystore.properties` file is now expected to be
  relative to the `keystore.properties` file itself, no longer relative to
  `bort/MemfaultBort/app`.
- `bort_cli.py` tool improvements:
  - The tool no longer exits with a non-zero code if all patches have already
    been applied.

## v2.0.1 - May 18, 2020

#### :chart_with_upwards_trend: Improvements

- `bort_cli.py` tool improvements:
  - For the `patch-aosp` command, the `--bort-app-id` option has been removed
    because it is no longer needed.
  - Check whether patch is applied, before attempting to apply patch.
  - Fix log message to correctly reflect whether patches failed to apply.

## v2.0.0 - May 14, 2020

#### :rocket: New Features

- Added the ability to update the Bort app independent of the OS by changing it
  from a system app to a privileged app.
- Added a configuration option to disable the Bort app from creating and
  uploading reports in builds where PII may be present (`user` builds by
  default).

#### :chart_with_upwards_trend: Improvements

- Simplified SDK/OS integration by including `BoardConfig.mk` and `product.mk`.
- Simplified SDK setup with improvements to the python setup tool.
- New SE policy preserves system `neverallow` rules.

#### :house: Internal

- Adds a new system app `MemfaultUsageReporter`. This provides an intent-based
  API for starting the `MemfaultDumpstateRunner`. While the API is available to
  all applications with the (privileged) `DUMP` permission, the output of the
  API will always be only delivered directly to the Bort app.
- The bort application no longer appears in the launcher by default. To include
  it, add an intent filter with the launcher category to an activity in the
  `AndroidManifest`.

```
<intent-filter>
  <action android:name="android.intent.action.MAIN" />
  <category android:name="android.intent.category.LAUNCHER" />
</intent-filter>
```

#### :boom: Breaking Changes

- This SDK update has several breaking changes.
- You must remove the previous required modifications to:
  - `frameworks/base` and replace them with a separately included
    `com.memfault.bort.xml` permissions file.
  - `neverallow` rules in `system/sepolicy` and add a new file type
    `bortbort_app_data_file` to avoid the need to change AOSP's builtin rules.
- Add `BoardConfig.mk` and `product.mk` to simplify the integration of the SDK
  to only two `include ...` lines.
- `patches/apply-patches.py` is now `bort_cli.py` with subcommands.
- The bort app now requires you to provide an application ID as well as a
  keystore. See the README for details.
- Configuration for the bort app has moved from
  `MemfaultBort/app/gradle.properties` to `MemfaultBort/bort.properties`; you
  will need to set your Project Key in this new location.

## v1.1.0 - May 2, 2020

- Added git patch files under `patches/`, including a python script
  `apply-patches.py` to automate applying the patches.
- Added support for Android 9 (alongside Android 10).

## v1.0.0 - April 29, 2020

- Initial release.
