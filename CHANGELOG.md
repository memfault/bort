# Memfault Bort Changelog

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
  will need to set your API key in this new location.

## v1.1.0 - May 2, 2020

- Added git patch files under `patches/`, including a python script
  `apply-patches.py` to automate applying the patches.
- Added support for Android 9 (alongside Android 10).

## v1.0.0 - April 29, 2020

- Initial release.
