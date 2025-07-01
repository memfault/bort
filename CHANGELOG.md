# Memfault Android SDK Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project currently does not attempt to adhere to Semantic Versioning, but
breaking changes are avoided unless absolutely necessary.

## [Unreleased]

### :rocket: New Features

- Added 3 more logs-to-metrics parser types.
  - `sum_matching` captures 1 integer, and sums all captured values in the
    heartbeat.
  - `distribution_minmeanmax` captures 1 double, and generates a min/mean/max
    distribution for all captured values in the heartbeat.
  - `string_property` captures 1 string, and reports the latest value for that
    string.
- Added tracking of the metered, temporarily unmetered, and roaming network
  capabilities in HRT.
  - Added a `connectivity.metered.latest` heartbeat metric for tracking metered
    network usage.
- Added new disk wear and disk write Device Vital heartbeat metrics. eMMC/UFS
  lifetime estimation and version stats are collected from the Health HAL
  implementation, falling back to a hardcoded UFS or eMMC filepath if not
  implemented. Bytes written each heartbeat is collected by reading the sectors
  written value from `/proc/diskstats` for matching physical devices in
  `/sys/block` multiplied by the sector size to get a value in bytes.
  - `disk_wear.<source>.lifetime_remaining_pct.latest` captures the lifetime
    remaining estimation for type A flash in increments of 10%. The source can
    be one of: "mmc0", "624000.ufshc", or "HealthHAL".
  - `disk_wear.<source>.lifetime_b_remaining_pct.latest` captures the lifetime
    remaining estimation for type B flash in increments of 10%. The source can
    be one of: "mmc0", "624000.ufshc", or "HealthHAL".
  - `disk_wear.pre_eol.latest` captures the pre-EOL status of consumed reserved
    blocks, as "Normal", "Warning", or "Urgent".
  - `disk_wear.version.latest` captures the version as described from the vendor
    implementation of the Health HAL.
  - `disk_wear.vdc.bytes_written` captures the bytes written for that heartbeat.
- Added per-app CPU usage metrics. The CPU usage percentage for each heartbeat
  is collected by parsing `/proc/<pid>/stat/` for system and user time
  (`stime` + `utime`) divided by the total usage (sum of all fields from
  `/proc/stat`).
  - By default, the SDK will only create heartbeat metrics for a defined set of
    "interesting" processes. Please reach out to customer support to configure
    this set.
  - Otherwise, the top 10 processes exceeding the 50% CPU usage threshold will
    also be recorded. Please reach out to customer support to configure these
    thresholds.
- Added support for Android 15.

### :chart_with_upwards_trend: Improvements

- Added parsers for more HRT batterystats data.
  - `screen_doze` captures whether the display is dozing in a low power state
    ([link](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/view/Display.java;l=491;drc=61197364367c9e404c7da6900658f1b16c42d0da;bpv=1;bpt=1?q=isDozeState)).
  - `flashlight` captures whether the flashlight was turned on.
  - `bluetooth` captures whether Bluetooth was enabled.
  - `usb_data` captures whether a USB connection was established (as reported by
    `android.hardware.usb.action.USB_STATE`).
  - `cellular_high_tx_power` captures whether the modem spent more of its time
    at the highest power level versus any other level.
  - `nr_state` captures the service level of the 5G network that's connected
    ([link](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/telephony/java/android/telephony/NetworkRegistrationInfo.java;l=147)).
- Updated comments on various batterystats fields. More improvements to come in
  clarifying the name and use of batterystats metrics!
- Dumped any existing logs when continuous logging starts, to retain logs
  captured from before a reboot.
- Added control over the maximum age of sampled data, to match the controls over
  unsampled data.
- Added support for parsing the binary log buffers (events, security, stats)
  with continuous logs.
- Modified dumpstate to write bugreports to /data/misc/MemfaultBugReports
  instead of to Bort's app dir. This is necessary for Android 15 support and CTS
  compliance to work around neverallow sepolicy rules. This also allowed
  deleting the custom `bort_app_data_file` app label for the Bort app.

### :construction: Fixes

- Added explicit Android System AID mappings for more consistent system UID
  attribution in tombstone and network stats parsing.
- Added VPN and USB as explicitly defined network types using
  `connectivity.network`.
- Fixed a bug in PhoneState where the unknown `???` state was reported as
  `null`.
- Added exponential backoff for EAGAIN errors in continuous logs. Each write is
  fsync'd immediately after write to reduce the likelihood of data loss.
- Added missing ignore.cil files for SDKs 32, 33, and 34.

### :house: Internal

- Stopped running unit tests on release sources.
- Migrated the SDK to communicate over a named domain (\*.memfault.test) rather
  than localhost for local development.
- Removed unneeded mockk init calls.
- Added some tests for unknown fields in device config.
- Refactored the unsampled holding area logic into a single class.
- Differentiated some continuous logging logs for easier debugging.

## v5.3.0 - February 14, 2025

### :rocket: New Features

- Added new per-component total storage metrics. The total storage used
  (apps+cache+data+external) by an application can be recorded as a
  `storage_<package>_bytes` metric. Please feel free to contact us if you would
  like this enabled for applications in your project.
- Added new mechanism to group operational crashes by package. The existing
  `operational_crashes` core metric can be divided into
  `operational_crashes_<group>` metrics, based off of the package names of the
  crashes. Please feel free to contact us if you would like this enabled for
  your project.
- Added new DropBox count core metrics. The number of DropBox entries processed
  each heartbeat, grouped by Issue type, is now recorded as a
  `drop_box_<type>_count` metric. This makes identifying crashy devices even
  easier.
- Added new `.mean_time_in_state` metric alongside `.total_secs` when the
  `TIME_TOTALS` aggregation is used with `StateTracker`s. The existing
  `.total_secs` metric is truncated between heartbeats, so it is only useful as
  a rough percentage comparison versus other states. The new
  `.mean_time_in_state` metric can be used to track the absolute time spent in a
  state, even across heartbeats, so its value can be used as an absolute number.

### :chart_with_upwards_trend: Improvements

- Returned a `Session` object when `startSession` is called to improve the API
  usability.
- Removed `SYSTEM_BOOT` from the default list of 'other' collected DropBox
  Entries. After some field testing, this DropBox Entry was not consistently
  collected and did not contain useful information.
- Silenced tags in continuous logs that did not match the filter spec.

### :construction: Fixes

- Fixed a logs-to-metrics bug where it could not parse logcat tags with spaces.
- Fixed a harmless SELinux violation where memfault_structured_app could not
  access its own data directory.
- Fixed a bug in bort_cli.py's validation-sdk-integration command where it
  wouldn't check the right location for system_ext sepolicy.
- Fixed a continuous log bug where spaces in the tag would break parsing.

### :house: Internal

- Migrated off junit5/jupiter back to junit4, at least until it has first party
  support from the Android team/
- Added minio to the debug network security config for local development.

## v5.2.0 - November 15, 2024

### :rocket: New Features

- Logs to Metrics: Bort can scan all captured log files (including those not
  uploaded) for patterns, and record metrics based on the results. These can be
  configured in the dashboard. See
  [documentation](https://docs.memfault.com/docs/android/android-logging#converting-logs-into-metrics).
- New Core Metric for CPU usage (`cpu_usage_pct`).
- New Core Metrics for memory usage (`memory_pct`, `memory_pct_max`).
- New Core Metric for storage usage (`storage_used_pct`).
- New Core Metrics for network Usage (`connectivity_*`). These replace the
  previous `network.*` metrics - those will no longer be collected, unless the
  `Network Usage: Collect Legacy Metrics` is enabled in the dashboard.
- New Core Metrics for thermals (`thermal_*`). These replace the previous
  `temp.*` metrics - those will no longer be collected, unless the
  `Collect legacy thermal metrics` is enabled in the dashboard.
- Added a `min_battery_voltage` metric to report the lowest observed voltage.
- New metric to capture the battery charge cycle count
  (`battery.charge_cycle_count.latest`) - this only works on Android 14+. Note
  that the previous metric added in 4.18.0 did not work.
- New metrics (`thermal_status_*`) to capture thermal mitigation status.
- New HRT metric (`device-powered`) capturing device shutdown/startup events.

### :chart_with_upwards_trend: Improvements

- Update the target SDK version to 34 (Android 14).
- Updated to the default hardware version (`ro.product.model`) and software
  version (`ro.build.version.incremental`) to match current defaults for new
  projects.
- Improve battery use attribution (including where usage would have previously
  been assigned to `unknown`).
- Added dashboard controls for `mar` upload job constraints (battery, charging
  state).
- Modified sepolicy to fix some `memfault_structured_app` violations.
- Removed validation of the OTA application ID being configured, if OTA is not
  being used (if `TARGET_USES_MFLT_OTA` is unset).
- `reporting-lib-kotlin` supports asynchronous usage, when constructing a
  `ReportingClient`.

### :construction: Fixes

- Fixed an issue which caused `COUNT` aggregations on non-numeric metric values
  to always return zero. This was introduced in 4.17.0.
- Fixed a bug in the SDK validation tool (`bort_cli.py`) which caused it to fail
  when running on Windows.
- Fixed a bug where the software/hardware version and device serial sysprops
  would not be updated immediately if changed remotely via SDK settings (a
  reboot may have been required for them to take effect).
- `MemfaultStructuredLogdApp` will not crash if sepolicy is incorrectly
  configured.
- Always captured metrics for app versions/sysprops, in an edge-case where the
  software version sysprop changed.

### :house: Internal

- Updated mockk.
- Removed bort internal log-to-disk functionality (this was not used).
- Removed local storage from `DevicePropertiesStore` - forward all internal
  metrics using public APIs.
- Bort no longer writes to the event log.
- Refactored use of WorkManager.
- Updated `ktlint` and reformatted some code.
- Inject coroutine dispatchers in more places.
- Record internal metrics for WorkManager job timing.
- Fixed a flaky unit test (`DevicePropertiesStoreTest`).
- Record zero values for per-app battery usage, for designated apps, instead of
  ignoring them (currently, Bort SDK apps only).

## v5.1.0 - September 13, 2024

### :construction: Fixes

- Fixed Stability Device Vitals: a change to the way metrics are collected in
  Bort 4.17.0 meant that `operational_hours` would erroneously be set to zero,
  resulting in incorrect Stability Vital charts.

### :chart_with_upwards_trend: Improvements

- Added new screen on/off battery drain metrics:
  `battery_screen_on_discharge_duration_ms`, `battery_screen_on_soc_pct_drop`,
  `battery_screen_off_discharge_duration_ms`, `battery_screen_off_soc_pct_drop`.
  These will replace `screen_off_battery_drain_%/hour`/
  `screen_on_battery_drain_%/hour` in the future, once they are supported in the
  Memfault dashboard (to more accurately track battery drain across the fleet).

## v5.0.0 - September 12, 2024

### :boom: Breaking Changes

- Bort has been moved from the `system` partition to the `system-ext` partition.
  This enables the use of Generic System Images.
- The AOSP patches to `product/mainline.mk`/`mainline_system.mk` (Android 11)
  and `product/generic_system.mk` (Android 12+) have been removed. These were
  required to whitelist Bort in the `system` partition. These were the only
  required AOSP patches if using Android 9+, meaning that all remaining AOSP
  patches are now optional, to enhance Bort functionality (except if using
  Android 8).
- To support this change, we have forked the Bort SDK because of
  backwards-incompatible `bp` file changes required to support `system-ext`. We
  will continue to support Android 8-10 on the `8-10` branch, and the `master`
  branch will support android versions 11+. No other work is required other than
  making sure to check out the correct branch - the AOSP build will fail if
  using the wrong branch.
- You will need to remove the aforementioned patch, if already applied and
  committed to your AOSP repo.

### :rocket: New Features

- Added the ability for Bort to upload any DropBoxManager tags, without the need
  to add specific processing for each in the SDK. This is configurable from the
  dashboard - by default we will start collecting `SYSTEM_AUDIT`, `SYSTEM_BOOT`,
  `SYSTEM_FSCK`, `SYSTEM_RECOVERY_LOG`, `SYSTEM_RESTART` entries, which can be
  viewed on the Device Timeline.

### :construction: Fixes

Bug fixes in the Bort SDK are now broken out into their own section in this
changelog.

- Fixed a bug where Bort would drop reboot events after a factory reset, if
  `RUNTIME_ENABLE_REQUIRED=true` and Bort was not enabled at the time of boot.
  Bort will now upload the latest reboot when it is enabled, if not already
  processed.

### :chart_with_upwards_trend: Improvements

- Store the software version alongside a metric report, so that metrics will not
  be associated with the wrong software version in the case of an OTA update.
- Also, collect metrics immediately after boot, if an OTA update was just
  installed - reducing the potential for metrics to be assigned to the wrong
  software version.

### :house: Internal

- Refactored HRT file generation.
- Switched to using the Room gradle plugin.

## v4.19.0 - August 13, 2024

### :rocket: New Features

- Added support for automatically collecting the phone's IMEI number as a
  metric: `phone.imei`.
- Updates the Stable Hours algorithm. By default, only ANRs, non-WTF app
  crashes, kernel panics, and tombstones count as "crashes" for the purposes of
  the algorithm. It is now also possible to exclude these crashes from the
  algorithm if desired (please reach out to customer support for more
  information).

### :chart_with_upwards_trend: Improvements

- Fixed a bug in the battery per-hour charge and discharge calculations where
  the wrong denominator was being used.
- Fixed a bug in the SDK where heartbeats after reboots could fail to process.
- Fixed a bug in the SDK where batterystats collection could fail
  inconsistently.
- Added the device serial and override to the diagnostics content provider.
- Disables metrics handling in more places if Bort is also disabled.

### :house: Internal

- Added more internal metrics to detect how often the Bort app restarts.
- Cleaned up spurious Bort logs.
- Fixed bug when tracking Bort's own battery usage.

## v4.18.0 - July 1, 2024

### :rocket: New Features

- Battery device vitals are automatically added to
  [Sessions](https://mflt.io/android-sessions), to enable seeing how the Session
  affected battery life: `battery_discharge_duration_ms`,
  `battery_soc_pct_drop`.
- Added battery charge cycle count metric: `battery.charge_cycle_count` - this
  tracks the total number of charge cycles the battery has been through (if the
  device supports this and writes it to
  `/sys/class/power_supply/battery/cycle_count`).
- Added `startSession`/`finishSession` native APIs for Sessions
- Reboots are now always uploaded, regardless of fleet sampling resolution
  (previously only uploaded when Debugging resolution is enabled).

### :chart_with_upwards_trend: Improvements

- Removed legacy settings endpoint support (this had not been used for ~2
  years - replaced by `device-config`).
- Added rate-limiter for Sessions.
- Catch a `SocketTimeoutException` during settings update, so that it doesn't
  fail the job.
- Changed the jitter for settings update, so that it evenly distributes requests
  throughout the fleet. Also removed any jitter from the very first settings
  update on a fresh device.
- Updated gradle to version 8.8 (fixed a build failure we saw internally).

### :house: Internal

- Changed network security config in the debug variant of the Bort app, for our
  internal CI configuration.
- Added internal metrics to track the size of Bort's databases.

## v4.17.0 - June 24, 2024

### :rocket: New Features

- Support for the new [Session](https://mflt.io/android-sessions) metrics. This
  enables Memfault to be used for more product-specific use cases by associating
  metrics to product functionality.
  - The latest reporting-lib on each platform have been updated to start, end,
    and record metrics to sessions (reporting-lib 1.5 has been published to
    Maven with Sessions support).
  - Every session will automatically include relevant `sync_successful`,
    `sync_failure`, `memfault_sync_successful`, `memfault_sync_failure`,
    `connectivity_expected_time_ms` and `connectivity_connected_time_ms` Device
    Vitals metrics. `operational_crashes` will also be automatically recorded,
    even if the value is 0.
- Support for new Daily Heartbeats has been added. This enables support for
  daily metric aggregations to supplement or replace Hourly Heartbeats for lower
  upload frequency use cases. Please contact us for more information.
  - Daily Heartbeats will contain every metric that Hourly Heartbeats do,
    besides batterystats at the moment.

### :chart_with_upwards_trend: Improvements

- The `structuredlogd` metrics database has been replaced by a Room database
  inside the Bort app. This move allows us to more quickly iterate on
  improvements to how metrics are persisted. The `structuredlogd` service now
  forwards all calls to the Bort app for backwards compatibility.
- Bort Lite's serial can be overridden using by sending an
  `INTENT_ACTION_OVERRIDE_SERIAL` intent with an `INTENT_EXTRA_SERIAL` string
  extra.
- DropBox is now queried when Bort starts in Dev Mode.
- A bug was fixed where Device information would be incorrectly cached until
  Bort restarted.

## v4.16.0 - May 28, 2024

### :rocket: New Features

- Added a `latestInReport` option to the `event` API in `Reporting`. This will
  create a heartbeat metric with the latest value.
- New Bort diagnostics - to improve diagnosing any SDK integration issues:
  - The SDK validation tool is enhanced to check Bort's runtime state (whether
    Bort is enabled, job execution state, recent failures, etc), and report this
    in the validation tool output. This will also now report several key
    compile-time configuration parameters.
  - Bort will upload more types of error to the
    [Integration Hub Processing Log](https://mflt.io/processing-log)
    (`batterystats` parsing errors, file cleanup errors, and job failures).
- Added a new [SDK Setting](https://mflt.io/sdk-settings) ("Allow OTA downloads
  based on the Network Type") to allow configuring OTA downloads to never happen
  on a roaming cellular network. This is unset by default, and will override the
  existing "Allow OTA downloads on Metered Networks" setting if configured.

### :chart_with_upwards_trend: Improvements

- Removed usages of `launch` when calling coroutines - avoiding any potential
  dangling work after a job/receiver has completed.

### :house: Internal

- Added support for V1 metrics to the Bort custom metrics database (this is not
  used yet, outside of Bort Lite).

## v4.15.3 - May 20, 2024

### :chart_with_upwards_trend: Improvements

- Fixes a bug where a single possibly corrupt MAR file would prevent the upload
  of all remaining MAR files.
- Adds type information to each MAR filename.
- Updates Gradle, WorkManager, Anvil, Kotlin Serialization, and various AndroidX
  libraries. Re-organizes the internal Jupiter test setup.
- Fixes a bug where Batterystats would fail to parse Doubles.
- Fixes a bug where the Count aggregation for Events would incorrectly report as
  a String instead of a Number.

## v4.15.2 - April 19, 2024

### :chart_with_upwards_trend: Improvements

- Improve rounding numbers when parsing battery stats.

## v4.15.1 - April 16, 2024

### :chart_with_upwards_trend: Improvements

- Fix an exception when parsing batterystats files with lines that have trailing
  commas.

## v4.15.0 - April 12, 2024

### :rocket: New Features

- Bort rate-limiting events are uploaded, to be displayed in the Processing Log.
- Added a `bort-cli` command (`generate-keystore`) to generate Bort keystores.
  [See documentation](https://mflt.io/android-keystore).

### :chart_with_upwards_trend: Improvements

- Updated `reporting-lib-java` to match latest kotlin `reporting-lib` APIs
  (added `sync`, `successOrFailure`).
- Catch `SecurityException` gathering network stats when permission is not
  granted, so that metric collection task does not fail.
- Added more logging around custom OTA logic.
- Update Bort Lite's metrics implementation to match the full SDK in reporting
  boolean vales as `0`/`1`.
- Added more Bort permission checks to the SDK validation tool.
- Removed SELinux violations from Stability Device Vital calculation.
- Bort will now always report an `all` network usage metric, even if there was
  no usage during the heartbeat.
- Added Bluetooth as a possible network usage metric type.

### :house: Internal

- Capture Bort battery usage as internal heartbeat metrics.
- Fixed some typos in code.
- Refactored how Bort registers scoped services.
- Bort can capture per-app heartbeat metrics for storage/network/battery usage.
  This is not enabled yet, pending backend changes.

## v4.14.0 - March 1, 2024

### :rocket: New Features

- Added a windows batch script to install Bort Lite.

### :chart_with_upwards_trend: Improvements

- Fixed an issue where Android 8 build could fail because of missing
  `seapp_contexts`.
- Updated Bort logs when not enabled to be clearer.
- Remove manual SharedPreferences construction.
- Don't add core battery metrics when there is no discharge (including on
  devices with no battery).
- Enable Dev Mode automatically when installing Bort Lite (and collect metrics
  immediately).

### :house: Internal

- Updated to Gradle 8.3, and Kotlin 1.9.21.

## v4.13.0 - February 7, 2024

### :boom: Breaking Changes

- This release contains more features inside the Bort app which rely on the
  permission whitelist changes made in 4.10.0. This release itself isn't
  breaking, but the Bort app v4.13.0 will not work with a base Bort SDK prior to
  4.10.0 - this will only be an issue if updating the Bort apk separately from
  the rest of the Bort SDK.
- Removed the default value of the `PROJECT_KEY_SYSPROP` property in
  `bort.properties`. Setting the project key via broadcast would fail, if this
  is configured (it would reset whenever Bort restarts). **Be sure that this
  property is not configured if you intend to set the project key via
  broadcast**. See
  [documentation](https://mflt.io/android-setting-project-key-at-runtime).

### :rocket: New Features

- Android 14 is supported.
- Bort Lite is supported (several changes below are in support of this). This is
  a cut-down version of the Bort SDK for previewing Memfault on any Android
  device with `adb` access. This will be released separately, as a pre-built
  binary.
- `batterystats` and `PackageManager` access is moved from `UsageReporter` to
  the `Bort` app. Periodic log collection is also moved to the Bort app on
  Android versions <= 12, and `sysprop` collection is done inside Bort if
  `MemfaultDumpster` is not available. This enables the forthcoming Bort Lite
  feature. The `READ_LOGS` and `INTERACT_ACROSS_USERS` permissions are removed
  from `UsageReporter` (they were added to Bort in 4.10.0).
- Connectivity metrics can now be collected by the `Bort` app, if
  `UsageReporter` is not installed. This is only used for Bort Lite (otherwise,
  `UsageReporter` is still used because it is persistent).

### :chart_with_upwards_trend: Improvements

- Disabled setting the project key via broadcast if `PROJECT_KEY_SYSPROP` is
  configured. See
  [documentation](https://mflt.io/android-setting-project-key-at-runtime).
- Use `successOrFailure` to generate the
  `sync_memfault_successful`/`sync_memfault_failure` Core Metrics. This improves
  their display on the device timeline.
- Fixed the naming of the battery life Core Metrics
  (`operational_hours`/`operational_crashfree_hours`) - removing the `.sum`
  suffix.
- Fixed an issue where if Dev Mode is toggled (enabled+disabled) on the device,
  then `mar` files will fail to upload until the device is rebooted.
- Migrated all Package Manager queries to use the java `PackageManager` API
  instead of `dumpsys pm` - this is much faster, and removed the possibility for
  `dumpsys` lock contention. Also added a caching layer to avoid doing a full
  query each time this data is needed.

### :house: Internal

- Adds Custom Metrics database in the Bort app. This is only used for the
  unreleased Bort Lite feature (i.e. not for the standard Bort SDK) - this will
  be migrated in a future release, to remove the `MemfaultStructuredLogD`
  database.
- The `reporting-lib` Custom Metrics library will attempt to add metrics to the
  Bort Custom Metrics database using a `ContentProvider` if
  `MemfaultStructuredLogD` is not installed. This is a no-op unless using Bort
  Lite.
- Adds a fallback device identifier, for the case where Bort doesn't have
  permission to get the real device identifier (only for use in Bort Lite).
- Fixed camelCase package naming.
- Cleaned up duplicate network interceptor code in the `debug` variant of the
  app.
- Removed all uses of `runBlocking` in unit tests.
- Removed unnecessary `IndividualTaskWorkerFactory` interface.
- Added an internal `bort_lite` metric to flag when Bort Lite is being used vs
  the full Bort SDK.
- Improved dagger usage in `SystemEventReceiver` to inject more classes vs
  creating them locally.

## v4.12.0 - December 28, 2023

### :rocket: New Features

- Added a OTA validation to the bort_cli.py
- Added a new disk wear metric.

### :chart_with_upwards_trend: Improvements

- Improved logging of network requests.
- Improved the way the Bort BoardConfig is loaded to allow it to be added
  multiple times. This was added to support devices who include a system and
  vendor boardconfig.
- Improved the sepolicy check in the bort_cli.py
- Moved the custom metrics database into bort. This is not yet used but will
  allow for future improvements.

## v4.11.0 - December 5, 2023

### :rocket: New Features

- Added `connectivity_connected_time_ms` and `connectivity_expected_time_ms`
  connectivity metrics.
  [See documentation](https://mflt.io/connectivity-metrics).
- Added support for `Reporting.report().successOrFailure()` and
  `Reporting.report().sync()` - see
  [documentation](https://mflt.io/android-custom-metrics-types). Update to the
  latest version of `reporting-lib` (kotlin only) to use these.
- Project keys can now be set using a sysprop, in addition to an Intent. See
  [documentation](https://mflt.io/android-setting-project-key-at-runtime) for
  more details.

### :chart_with_upwards_trend: Improvements

- Fixed parsing of application ID in tombstones, where a named process is in
  use.
- Fixed an issue with Continuous Logging, where the filter-spec was not
  correctly applied.

### :house: Internal

- Added internal metrics for Bort network usage.

## v4.10.0 - November 15, 2023

### :boom: Breaking Changes

- Previous AOSP system images with Bort are not backwards compatible with newer
  versions of the Bort apk, due to new permissions required by Bort that are
  only granted by a new AOSP build. These permissions were previously granted to
  UsageReporter. This means that newer versions of the Bort apk cannot run on
  older system images - they must be released together.

### :rocket: New Features

- Added network stats metrics. This tracks the total and per app usages of the
  Wi-Fi, Ethernet, and cellular interfaces. Please see
  [documentation](https://docs.memfault.com/docs/android/android-builtin-metrics#network-usage-metrics)
  for more information.
- Added new Crash-Free hours metric. Please see
  [documentation](https://docs.memfault.com/docs/best-practices/fleet-reliability-metrics-crash-free-hours/#enable-crash-free-hours-on-your-devices)
  for more information.
- Added new battery charge rate first 80% metric. This measures the rate at
  which the battery is charging before Android begins trickle charging the
  battery at a slower rate. This allows for a more accurate understanding of the
  health of the battery.
- Added new Battery SOC percentage drop metric which will be used to estimate
  expected battery runtime of the device.
- Added a new Battery State of Health (SoH) metric. This metric measures the
  current full charge capacity of the battery against the original full charge
  capacity and reports the value as a percentage of health remaining.

### :chart_with_upwards_trend: Improvements

- Fixed a typo in TemperatureMetricCollector. Thank you
  [satur9nine](https://github.com/satur9nine) for bringing this to our attention
  and fixing. [PR #4](https://github.com/memfault/bort/pull/4)
- Improved detection of battery state (charging or discharging) when gathering
  battery related metrics.
- Improved the reliability of OTA downloads by refactoring to more modern
  coroutine best practices.
- Improved Custom Metrics API for native code (C/C++).
  - Missing `Event` type added.
  - Missing metadata added.
  - Removed finishReport method and report name parameters as they were meant
    for internal use only.
  - Support Soong (Blueprint files) build system.
  - Removed deprecated Custom Event API.
  - Fixed native Reporting API timestamp generation.
- Fixed .gitignore to remove build files that were incorrectly being included.
- Updated AGP, Kotlin, and 3rd party libraries to newer versions.
- Refactor the Kotlin Reporting Library to use the Java lib internally to keep a
  single source of truth for implementation.
- Moved DropBoxManager entry processing from UsageReporter to Bort. This stops
  waking Bort for every new entry, if Bort already isn't running, and
  significantly simplifies DropBoxManager entry processing.

### :house: Internal

- Improved Kotlin formatting by updating ktlint.
- Removed unused ingress URL.
- Added exemplary Java app compiled using Android Make to demonstrate usage of
  the Java based Reporting lib.

## v4.9.0 - September 5, 2023

### :rocket: New Features

- Introduced a new pure java based reporting library. This is easier to use in
  apps and services built by Android.mk and Soong build systems.

### :chart_with_upwards_trend: Improvements

- Minimize disk writes by StructuredLogd.
  - Property metrics are now kept in memory instead of being sent to
    StructuredLogd
  - Don't re-write metric metadata if it is already correct.

## v4.8.1 - July 26, 2023

### :chart_with_upwards_trend: Improvements

- OTA app will now try to stay running (using a JobScheduler job, and optionally
  a foreground service) while downloading A/B updates. Set
  `useForegroundServiceForAbDownloads = true` in `AutoInstallRules.kt` to use a
  foreground service.
- OTA app will now recover if killed while `UpdateEngine` is downloading an A/B
  update (using a periodic job which runs every 15 minutes).
- Made libmflt-structuredlog a Soong module.

## v4.8.0 - July 17, 2023

### :rocket: New Features

- Refactored the OTA app to support more granular control over the download and
  install constraints. Download and installation can now be configured
  separately from each other. Default network, storage, and battery constraints
  are now available alongside any custom logic. See
  [AutoInstallRules.kt](https://mflt.io/android-ota-customization) for more
  details. The [Memfault CLI](https://mflt.io/memfault-cli) 0.18.0+ supports
  attaching extra information to OTA payloads using the `--extra-metadata`
  option, which can be used for further customization of the download and
  install constraints.
- Added support for collecting new batterystats summary and per-app usage
  metrics. We will roll out this feature over the coming weeks - please contact
  us for more information.
- Added support for collecting SELinux violations without using Android
  bugreports by parsing logcat directly - please contact us for more
  information.

### :chart_with_upwards_trend: Improvements

- Improved file cleanup deletion logic so that one single large file over the
  size limit doesn't force other smaller files to be deleted.
- Fixed an issue where UsageReporter couldn't forward data to Bort on Android
  13+.
- Fixed an ANR in UsageReporter while collecting temperature metrics.
- Fixed continuous logging SELinux permissions on Android 12.1+.
- Fixed continuous logging alarm on Android 11+.
- Fixed a bug where a state update could be dropped in the OTA app.
- Fixed several Stream leaks in Bort.
- Fixed an issue where HRT files were not being uploaded because the Custom
  Metrics service configuration was stale.

### :house: Internal

- Updated to AGP 8.0.1, Kotlin 1.8.21, Kotlin Coroutines 1.7.0, JVM 17.
- Moved package namespaces to the gradle file from the manifest.
- Turned on Kotlin allWarningsAsErrors.
- Turned on Kotlin
  [explicitApi](https://kotlinlang.org/docs/whatsnew14.html#explicit-api-mode-for-library-authors)
  mode.
- Enabled more verbose unit test logging output.
- Added Dagger and Hilt to the OTA app.
- Enabled
  [strict mode](https://developer.android.com/reference/android/os/StrictMode)
  for all debug apps.

## v4.7.0 - May 9, 2023

### :chart_with_upwards_trend: Improvements

- Disable the UsageReporter -Receiver classes when Bort is disabled.
- Add missing deprecation annotations to CustomEvent API.

### :house: Internal

- Report a Bort installation id to track new app install vs existing app where
  data was cleared.

## v4.6.0 - April 26th, 2023

### :rocket: New Features

- Removed (previously deprecated) individual file upload support:
  [MAR](https://mflt.io/android-mar) is the only supported upload mechanism.

### :chart_with_upwards_trend: Improvements

- Bort will resend Config State when not synced with backend.
- Fixed an issue where if SDK settings change, they are not applied immediately,
  and fleet sampling configuration may not be updated.
- If the project key was changed at runtime, enable resetting it:
  [See Documentation](https://mflt.io/android-setting-project-key-at-runtime).
- Fixed a FileNotFoundException in Bort processing files to upload.
- Fixed a crash during file cleanup, caused by modified date changing during the
  cleanup process.
- Limit DropBoxManager queries to a maximum file age of 1 hour after a fresh
  install of Bort.
- When using Client-Server mode, limit the age and retry count of files waiting
  to be transferred to the Server device in UsageReporter.
- Fixed a UsageReporter crash when a message is received during service
  shutdown.

### :house: Internal

- Added an internal metric for the Bort package name.
- Track any task worker failures.

## v4.5.1 - April 5th, 2023

### :chart_with_upwards_trend: Improvements

- Fixed an issue where files could be leaked in UsageReporter, when using
  Client-Server mode.
- Added remotely-configurable storage limits for UsageReporter/Bort cache
  folders.
- Added more granular storage limits for fleet sampling devices with disabled
  aspects.

### :house: Internal

- Added internal metrics for storage usage/file cleanup.

## v4.5.0 - March 27th, 2023

### :rocket: New Features

- Added metric for [Fleet Sampling](https://mflt.io/android-fleet-sampling)
  resolutions.
- Bypass all rate-limits in Dev Mode.
  [See Documentation](https://mflt.io/android-dev-mode)
- [Batterystats](https://mflt.io/android-batterystats-metrics) via HRT
  - Bort writes batterystats history directly to the HRT file produced by the
    metrics service.
  - Bort calculates batterystats aggregate metrics to replicate existing backend
    processing.
- Allow the project key to be changed at runtime via intent broadcast.
  [See Documentation](https://mflt.io/android-setting-project-key-at-runtime).

### :chart_with_upwards_trend: Improvements

- Removed cuttlefish related AOSP patches as they were for internal use only.
- Improved MAR file bundling.
- Fixed a socket error when running in Client/Server mode.
- Fixed a bug that caused MemfaultDumpster to crash.
- Fixed crash when channel closes in UsageReporter.
- Updated Android Build Tools and Libraries.
- Updated AGP to 7.4.1
- Limit max storage age of MAR files to 7 days (down from 30).
- StructuredLogD now forces timestamp linearity for time calculations.
- Fix SELinux violation caused by dumpstate calling `dump()` on the
  memfault_structured service.

### :house: Internal

- Fixed incorrect HRT typing on internal metrics.
- Added metric for tracking deleted MAR files.

## v4.4.0 - January 11th, 2023

### :rocket: New Features

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

### :chart_with_upwards_trend: Improvements

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

### :house: Internal

- Migrates Bort internal metrics to use High-Res Telemetry.

## v4.3.2 - November 22nd, 2022

### :chart_with_upwards_trend: Improvements

- Fixed an issue where bugreports may fail to be processed by Bort if the Bort
  OTA app is installed on the device.

## v4.3.1 - November 7th, 2022

### :chart_with_upwards_trend: Improvements

- Fixed an issue which could cause batterystats collection to fail after some
  time-change events.
- Fixed an issue in WTF on-device rate-limiting.

## v4.3.0 - October 18th, 2022

### :rocket: New Features

- Continuous Logging Mode is in preview. When enabled, logs will be continuously
  collected by the `MemfaultDumpster` service. This is designed to avoid missing
  logs because of buffer expiry.
  [See documentation](https://mflt.io/android-logging). For devices running
  Android 8/9/10 a new AOSP patch is required for Continuous mode to work.
- On-device scrubbing of tombstone files.
  [See documentation](https://mflt.io/android-data-scrubbing).

### :chart_with_upwards_trend: Improvements

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

### :house: Internal

- Improved how settings are overridden during CI tests.

## v4.2.0 - September 14th, 2022

### :rocket: New Features

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

### :chart_with_upwards_trend: Improvements

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

### :house: Internal

- Added internal metrics around `mar` storage/usage.
- Changed the default API endpoint from `api.memfault.com` to
  `device.memfault.com` (to match remote settings configuration).
- Added the device software version to the `device-config` server call.
- Bort can optionally fetch device configuration from new `/device-config`
  endpoint.

## v4.1.0 - April 18, 2022

### :chart_with_upwards_trend: Improvements

- Enhanced the
  [SDK validation tool](https://docs.memfault.com/docs/android/android-getting-started-guide#validating-the-sdk-integration)
  to check that the `MemfaultStructuredLogD` (Bort's metric collection service)
  is correctly configured.
- In a client/server configuration, Bort SDK settings are now forwarded from the
  server to the client.
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

### :chart_with_upwards_trend: Improvements

- Note: The Bort gradle build now requires JDK 11 (this is a requirement for the
  latest version of the Android Gradle Plugin).
- Updated several dependencies, including Gradle 3.7.2 and Kotlin 1.6.10.
- Updated patch descriptions.
- Fixed an issue where custom metrics may not be enabled on some devices.

## v4.0.0-RC1 - December 15, 2021

This preview release contains several features which do not yet have full
documentation.

### :rocket: New Features

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

### :chart_with_upwards_trend: Improvements

- Bort will now only run as the primary user, on devices with multiple users.
- Bort is updated to use the latest version of several tools & libraries
  (Gradle, Kotlin, Android Gradle Plugin, etc) and a `compileSdkVersion` of 31.
- Fixed an SELinux violation when Bort accesses DropBoxManager entries on a
  recent targetSdkVersion.
- Fixed a race condition which caused a crash when Bort was started by a
  ContentProvider query.
- Fixed an edge-case in DropBoxManager processing, which could cause an entry to
  be missed.

### :boom: Breaking Changes

- `BUILD_ID_CODE` in `bort.properties` is replaced by `BUILD_ID_CODE_OVERRIDE`.
  This is now an override, which is disabled by default - and should only be
  uncommented if required (SDK updates will always increment the build ID
  internally, so this will not normally be needed).
- The `custom-event-lib` library has been removed. The `CustomEvent` API is now
  located in the `reporting-lib` library, which is also now published as an
  artifact to Maven Central as `com.memfault.bort:reporting-lib:1.0`.

### :house: Internal

- Bort now uses Maven Central instead of JCenter for dependencies.
- Added support for the `mar` (Memfault Archive) file format - this enables
  bundling data into a batched file upload, instead of uploading a separate file
  for every piece of data. This is not yet enabled by default.
- Bort is migrated to use the Dagger dependency injection framework.

## v3.7.3 - October 26, 2021

### :chart_with_upwards_trend: Improvements

- Fix a potential crash after requesting a bugreport via Intent.
- Sepolicy changes to allow Bort+OTA to run on the latest build of Android 11,
  and with a target SDK version of 30.
- Enable requesting an OTA update check via Intent
  (`com.memfault.intent.action.OTA_CHECK_FOR_UPDATES`).

### :house: Internal

- Added ability for bort to log to disk, to aid debugging. This is disabled by
  default.

## v3.7.2 - October 18, 2021

### :chart_with_upwards_trend: Improvements

- Fix a crash when using A/B OTA updates.
- Disable debug variant.
- Add system properties for Bort SDK/patch versions.
- Improved internal observability.
- Improve rate-limiting behavior after a reboot.

## v3.7.1 - September 30, 2021

### :chart_with_upwards_trend: Improvements

- Fix a rate-limiting issue which sometimes caused regular data collection to be
  missed.
- Added internal logging for the upstream Bort version code.

## v3.7.0 - September 15, 2021

### :rocket: New Features

- OTA Update Client: Bort now includes a full OTA Update client.
  [See documentation](https://mflt.io/android-ota-update-client) for more
  information.

### :chart_with_upwards_trend: Improvements

- Enable Bort to collect internal logs when custom log scrubbing rules are in
  place.
- Fix an issue during SDK integration, where Bort sepolicy files can be included
  multiple times, resulting in build errors.
- Added the ability to collect kernel oops' in Caliper.
- Fix an issue causing a CTS/GMS test failure.

## v3.6.2 - August 17, 2021

### :chart_with_upwards_trend: Improvements

- Fix "Bort enabled" log (previously included incorrect "enabled" value).
- Improve Bort internal logging and metrics.
- Make periodic bugreport rate-limiting remotely controlled. This fixes an issue
  where some bugreport collection was being rate-limited.

## v3.6.1 - July 21, 2021

### :chart_with_upwards_trend: Improvements

- In 3.6.0, the OTA app had to be configured with an application ID and
  certificate (even though not being included in the build). This is fixed - OTA
  app configuration is no longer required when not being used.

## v3.6.0 - July 9, 2021

### :boom: Breaking Changes

- Structured Logging (introduced in 3.5.0) has been renamed to Custom Events.
  Any code using the `StructuredLog` API from the `structured-log-lib` library
  (now removed) must be changed to call the equivalent methods in the
  `CustomEvent` API from the `custom-event-lib` library.

### :chart_with_upwards_trend: Improvements

- Fixed Custom Events (formerly: Structured Logs) on devices where
  `RUNTIME_ENABLE_REQUIRED=false`. These devices would fail to upload events.
- Fixed SDK validation script (`bort_cli.py --validate-sdk-integration`) when
  running on Windows (this previously reported incorrect failures).
- Added new metrics and Custom Events reporting the behavior and impact of the
  Bort SDK, to enable Memfault to track down any issues that may arise with
  Bort.
- The SDK patch script now works correctly when cuttlefish emulator directories
  are missing.

### :house: Internal

- OTA update client: a new OTA update client is included in this release. This
  is not yet ready for use (and is not yet documented), and is not included when
  building. The new OTA update client will be available in a future Bort
  release.

## v3.5.0 - June 2, 2021

### :rocket: New Features

- Structured logging: this new API enables logging structured events, which
  appear directly on the device timeline
  [See documentation](https://docs.memfault.com/docs/android/structured-logs)
  for more information.
- Android 11 support for the Bort SDK.

### :chart_with_upwards_trend: Improvements

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

### :chart_with_upwards_trend: Improvements

- Fix a rare issue in the on-device rate limiting system.
- Make HTTP request timeout a configurable parameter.
- Add randomized delay to uploads to distribute fleet-wide requests over time.

### :house: Internal

- Structured logging: this is an experimental new feature still in development.

## v3.4.2 - March 17, 2021

### :chart_with_upwards_trend: Improvements

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

### :chart_with_upwards_trend: Improvements

- Added a `dontaudit` sepolicy line to avoid failing a CTS test. See
  `memfault_dumpster.te` for more information.

## v3.4.0 - March 8, 2021

### :rocket: New Features

- On-device data scrubbing: Caliper traces pertinent to allowed application IDs
  are uploaded and others are filtered out, on-device. Similarly,
  Caliper-captured log lines are scrubbed before they are uploaded, based on
  allowed application IDs and configured text scrubbing rules. The data
  scrubbing rules can be configured through the Memfault Settings UI. Note that
  bug reports are still scrubbed server-side. See
  [data scrubbing documentation](https://mflt.io/android-data-scrubbing) for
  more information.

### :chart_with_upwards_trend: Improvements

- Hard-coded, 5-second command (i.e. logcat) timeouts have been removed and
  replaced with remotely configurable timeouts.
- DropBoxEntry tags can be excluded entirely from uploading. The list of tags to
  exclude can be configured in the Memfault Settings / Data Sources UI.
- Fixed/avoided a problem relevant to Android 8.x, where Bort would occasionally
  crash due to bugs in the AOSP platform code.

## v3.3.1 - February 19, 2021

### :chart_with_upwards_trend: Improvements

- Improved the gradle build scripting to prevent accidentally replacing the
  `MemfaultPackages/settings/settings.json` with different settings. In case the
  local file contains settings that differ from what has been configured in the
  Memfault web service, the build is aborted and a
  [RFC 6902 JSON diff](https://tools.ietf.org/html/rfc6902) is printed to aid
  the user, as well as suggestions on how to fix the issue.

## v3.3.0 - February 17, 2021

### :rocket: New Features

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

### :chart_with_upwards_trend: Improvements

- File uploads are now gzip-compressed prior to uploading to save bandwidth.
- A built-in metric `MemfaultSdkMetric_bort_version_code` has been added that
  contains Bort's version code.

### :house: Internal

- Fixed a bug that caused overly aggressive rate limiting under certain
  conditions.
- All periodic data collection tasks are now also rate limited, to avoid
  uploading more data than desirable in case those tasks are triggered by
  repeatedly enabling/disabling of the SDK.

## v3.2.0 - February 4, 2021

### :chart_with_upwards_trend: Improvements

- New built-in metrics have been added that count the number of traces by tag.
  The naming format is `drop_box_trace_%s_count` where `%s` is filled with the
  tag, i.e. `system_tombstone`.
- A new built-in metric called `drop_box_traces_drop_count` has been added that
  counts the number of dropped traces due to rate-limiting.

### :house: Internal

- Rate limiting: Bort will now also rate limit reboot event uploads.
- A bug has been fixed that caused project keys for newly created projects to
  fail the gradle build.

## v3.1.0 - January 29, 2021

### :chart_with_upwards_trend: Improvements

- A new option is available to label a bug report that is manually triggered via
  the intent-based API (the action
  `com.memfault.intent.action.REQUEST_BUG_REPORT`). When the optional ID is
  provided, the `dumpstate.memfault.requestid` system property is set to the
  value provided. Additionally, a BroadcastReceiver can be specified to which
  the status of the bug report request will be reported by Bort. See
  <https://mflt.io/android-bort-sdk> for details.
  > NOTE: in order to use this feature, the AOSP system image has to be updated
  > (merely updating MemfaultBort.apk is not sufficient, because this feature
  > also involved a change in the MemfaultDumpstateRunner system component).
- Added a build-time check that runs as part of the AOSP build, to ensure the
  MemfaultBort.x509.pem signing certificate matches the signature of
  MemfaultBort.apk.
- Improved `bort_cli.py`'s SDK validation by also printing more details about
  the reason of a failed validation.

### :house: Internal

- Rate limiting: Bort will now rate limit uploads to the Memfault web service,
  to avoid faulty devices from uploading too much data.
- Bug fix: avoid uploading empty trace files.

## v3.0.1 - January 4, 2021

### :house: Internal

- Fixed a bug that caused `BUG_REPORT_MINIMAL_MODE=true` to only enable minimal
  mode bug reports to get captured after rebooting but not after enabling the
  SDK at run-time.
- Remove a spammy log message.

## v3.0.0 - December 17, 2020

### :chart_with_upwards_trend: Improvements

- Have you ever wondered how the metrics in your device timelines are trending
  across your whole fleet? With the 3.0.0 version of Bort, now you can view
  visualizations of these metrics aggregated across all your devices! See
  <https://mflt.io/fleet-wide-metrics> for details.
- The aforementioned "experimental" data source is now ready for prime time! It
  can be used to collect traces and metrics, with additional data sources coming
  in future SDK releases. See <https://mflt.io/memfault-caliper> for details.
- Reboot events were added in an earlier version of the SDK but didn't make it
  into the changelog. Let's call that a 3.0 feature too! See
  <https://mflt.io/android-reboot-reasons> to learn more.

## v2.9.1 - December 3, 2020

### :chart_with_upwards_trend: Improvements

- The `bort_src_gen.py` tool used to require Python 3.6 to run, but it is now
  compatible down to Python 3.4.

## v2.9.0 - November 26, 2020

### :house: Internal

- Experimental DropBoxManager API based data source:
  - Added uploading of DropBox-sourced ANRs, Java exceptions, WTFs, last kmsg
    logs and Tombstones with "native backtrace" style dumps.
  - Improved DropBox file upload, reusing existing upload worker task that is
    also used to upload bug reports.

## v2.8.0 - November 13, 2020

### :house: Internal

- Experimental DropBoxManager API based data source:
  - Refactored parts of the infrastructure in UsageReporter and Bort apps to
    collect DropBox entries, logcat logs, etc.
  - Added infra to collect installed package metadata.
  - Added a preliminary uploader for DropBox-sourced tombstones.
  - Fixed a bug where the DropBox entry collection process would miss entries
    after a backwards RTC time change.

## v2.7.1 - November 5, 2020

### :chart_with_upwards_trend: Improvements

- The `bort_cli.py` tool now only writes to a validation log file when running
  the validation command.

### :house: Internal

- Add a backtrace when sending an error response in IPC between the
  UsageReporter and Bort apps.

## v2.7.0 - October 28, 2020

### :chart_with_upwards_trend: Improvements

- Any custom configuration of Hardware and Software Versions as well as Device
  Serial sources is now automatically retrieved by the SDK from the Memfault
  backend during the build process by a gradle task. This means it is no longer
  necessary to manually configure the following properties:
  - `ANDROID_HARDWARE_VERSION_KEY`
  - `ANDROID_DEVICE_SERIAL_KEY`
  - `ANDROID_BUILD_VERSION_SOURCE`
  - `ANDROID_BUILD_VERSION_KEY`

## v2.6.0 - October 27, 2020

### :chart_with_upwards_trend: Improvements

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

### :house: Internal

- `ktlint` was applied to the codebase, resulting in reformatting of many files.
- Logcat log fetching has been added to the experimental
  `DATA_SOURCE_DROP_BOX_ENABLED` feature

## v2.5.0 - October 20, 2020

### :chart_with_upwards_trend: Improvements

- Remove reliance on `BOARD_PLAT_PRIVATE_SEPOLICY_DIR` for Android 8.1 or older
  platforms. This makefile variable is supposed to be set to only a single
  directory on these older platforms and was causing issues when the variable
  was already used for other purposes than Bort. As a work-around,
  `BOARD_PLAT_PRIVATE_SEPOLICY_DIR` is now used only on Android 9 and newer. For
  older Android versions, Bort's private sepolicy changes are patched directly
  into `system/sepolicy`. See `BoardConfig.mk` and
  `patches/android-8/system/sepolicy/git.diff`.

## v2.4.1 - October 9, 2020

### :chart_with_upwards_trend: Improvements

- Additional HTTP logging

## v2.4.0 - September 29, 2020

### :chart_with_upwards_trend: Improvements

- Before this release, the Bort application ID and feature name had to be
  patched in various files, using the `bort_cli.py` tool. With this release,
  `BORT_APPLICATION_ID` and `BORT_FEATURE_NAME` properties have been added to
  the `bort.properties` file. Files that had to be patched previously, are now
  generated at build-time, using the `BORT_APPLICATION_ID` and
  `BORT_FEATURE_NAME` from the `bort.properties` file.
- The `bort_cli.py` command `patch-bort` has been changed to only patch the
  `bort.properties`' `BORT_APPLICATION_ID` and `BORT_FEATURE_NAME` properties if
  they have not already been set.

### :house: Internal

- The target SDK is now a configurable SDK property (via `bort.properties`)
- The default target SDK has been lowered 26 from 29 for more consistent
  behaviour across the supported platforms.
- The min SDK version has been lowered to 26 to support Android 8.

### :boom: Breaking Changes

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

### :boom: Breaking Changes

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

### :house: Internal

- Log events when the SDK is enabled and disabled.

### :chart_with_upwards_trend: Improvements

- There is a new `bort.property` called `BORT_CONTROL_PERMISSION`, used to
  specify which permission should be used to control the SDK. By default, this
  is property is set to `com.memfault.bort.permission.CONTROL`.
- Improved `bort_cli.py`'s `validate-sdk-integration` command to also check the
  ownership and sepolicy context of key SDK files.

## v2.2.4 - July 14, 2020

### :rocket: New Features

- Adds `enable-bort` and `request-bug-report` commands to `bort_cli.py`.

### :chart_with_upwards_trend: Improvements

- Adds fixes for the `validate-sdk-integration` command in `bort_cli.py` when
  being run on Windows.

## v2.2.3 - July 8, 2020

### :rocket: New Features

- Validate your SDK integration with the `validate-sdk-integration` command in
  `bort_cli.py`.

### :chart_with_upwards_trend: Improvements

- The SDK will now work if
  [UserManager.DISALLOW_DEBUGGING_FEATURES](https://developer.android.com/reference/android/os/UserManager#DISALLOW_DEBUGGING_FEATURES)
  is enabled.

## v2.2.2 - June 29, 2020

### :chart_with_upwards_trend: Improvements

- Easily specify V1 and/or V2 APK signing configurations for the MemfaultBort
  app using `bort.properties`.

### :house: Internal

- The `bort_cli.py` script now requires Python 3.6+

## v2.2.1 - June 24, 2020

### :boom: Breaking Changes

- The `versionCode` and `versionName` are now set by default by the SDK. If you
  need to override them or increase the `versionCode` for an OTA update, see
  `bort.properties`.

### :chart_with_upwards_trend: Improvements

- Fixed `bort_cli.py` to patch the custom application ID into the permissions
  XML. This fixes an issue where the MemfaultBort application was not being
  granted the expected permissions.

### :house: Internal

- More debug info in requests to track and debug different SDK behaviour.
- Use the `EventLog` API to log SDK events.
- Update `MemfaultBort` dependencies.

## v2.2.0 - June 15, 2020

### :rocket: New Features

- This release adds an intent-based API to enable or disable the SDK at runtime.
- Additionally, there is new requirement that the SDK be explicitly enabled
  using this API at runtime. This can be disabled via a gradle property,
- There is now also an intent-based API to trigger a one-off bug report.

## v2.1.0 - June 2, 2020

### :rocket: New Features

- Adds the ability to upload bug reports to a user-specified endpoint

### :chart_with_upwards_trend: Improvements

- The `storeFile` property in a `keystore.properties` file is now expected to be
  relative to the `keystore.properties` file itself, no longer relative to
  `bort/MemfaultBort/app`.
- `bort_cli.py` tool improvements:
  - The tool no longer exits with a non-zero code if all patches have already
    been applied.

## v2.0.1 - May 18, 2020

### :chart_with_upwards_trend: Improvements

- `bort_cli.py` tool improvements:
  - For the `patch-aosp` command, the `--bort-app-id` option has been removed
    because it is no longer needed.
  - Check whether patch is applied, before attempting to apply patch.
  - Fix log message to correctly reflect whether patches failed to apply.

## v2.0.0 - May 14, 2020

### :rocket: New Features

- Added the ability to update the Bort app independent of the OS by changing it
  from a system app to a privileged app.
- Added a configuration option to disable the Bort app from creating and
  uploading reports in builds where PII may be present (`user` builds by
  default).

### :chart_with_upwards_trend: Improvements

- Simplified SDK/OS integration by including `BoardConfig.mk` and `product.mk`.
- Simplified SDK setup with improvements to the python setup tool.
- New SE policy preserves system `neverallow` rules.

### :house: Internal

- Adds a new system app `MemfaultUsageReporter`. This provides an intent-based
  API for starting the `MemfaultDumpstateRunner`. While the API is available to
  all applications with the (privileged) `DUMP` permission, the output of the
  API will always be only delivered directly to the Bort app.
- The bort application no longer appears in the launcher by default. To include
  it, add an intent filter with the launcher category to an activity in the
  `AndroidManifest`.

```xml
<intent-filter>
  <action android:name="android.intent.action.MAIN" />
  <category android:name="android.intent.category.LAUNCHER" />
</intent-filter>
```

### :boom: Breaking Changes

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
