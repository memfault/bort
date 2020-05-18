# Memfault Bort Changelog

## v2.0.1

#### :chart_with_upwards_trend: Improvements

- `bort_cli.py` tool improvements:
  - For the `patch-aosp` command, the `--bort-app-id` option has been removed
    because it is no longer needed.
  - Check whether patch is applied, before attempting to apply patch.
  - Fix log message to correctly reflect whether patches failed to apply.

## v2.0.0

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

## v1.1.0

- Added git patch files under `patches/`, including a python script
  `apply-patches.py` to automate applying the patches.
- Added support for Android 9 (alongside Android 10).

## v1.0.0

- Initial release.
