# Bort: Memfault Bug Report SDK for AOSP

## Overview

The purpose of this SDK is to make it really easy to periodically capture bug
reports on AOSP devices and get them uploaded automatically to Memfault.

At a high level, the process of capturing and uploading involves these
components that work together:

### The Bort app

A privileged app that is responsible for periodically invoking bug reports. When
it receives a bug report back from the `MemfaultDumpstateRunner`, the bort app
schedules that bug report to be uploaded when internet is available.

The application ID `vnd.myandroid.bortappid` should be replaced to something
vendor specific (e.g. `com.yourcompany.bort`), using the `bort_cli.py` tool. See
_Integration Steps_ below.

The Bort app is built using gradle. Instructions on how to build and include the
APK as a prebuilt artifact are provided in the integration section below.

### The UsageReporter app (com.memfault.usagereporter)

A system app that is responsible for invoking the `MemfaultDumpstateRunner`,
whenever it receives a request to do so from the Bort app.

The application ID `com.memfault.usagereporter` should be kept as-is.

This app is also built using gradle. Instructions on how to build and include
the APK as a prebuilt artifact are provided in the integration section below.

### /system/bin/MemfaultDumpstateRunner

This native program is exposed as an "init.rc service" (see `memfault_init.rc`
for details). This is so that it can be executed in the required `dumpstate`
security context, but get triggered by the Bort app, via the UsageReporter
system app. The Bort app runs in the much less capable security context compared
to `MemfaultDumpstateRunner`.

1. trigger `/system/bin/dumpstate` (through another init.rc service,
   `memfault_dumpstatez`),
2. copy the bugreport.zip file out of the shell file system sandbox and into
   Bort's file system sandbox,
3. broadcast an Intent with the final path such that Bort can process it
   further.

To permit `MemfaultDumpstateRunner` to do all the things it needs to do, it is
labelled with the existing/builtin `dumpstate` sepolicy label and is broadened a
little further as well (see `memfault_dumpstate_runner.te`). It's possible to
make a tail-made policy that is narrower in scope but this requires more changes
to the builtin AOSP system/sepolicy, so we choose to piggy-back on the existing
`dumpstate` type instead. This is also the approach that the AOSP Car product
(Android Auto) takes. See `sepolicy/file_contexts` for reference.

### /system/bin/dumpstate

This is the AOSP-built-in bugreport capturing program; it requires no
modification.

Note that it is not triggered through the builtin `dumpstatez` init.rc service,
but through the slightly specialized `memfault_dumpstatez`. See
`memfault_init.rc` for details on the differences.

## Integration steps

- Add this repo to your tree at `packages/apps/bort` (i.e. add it to your repo
  manifest).

- Decide on an vendor-specific application ID for the Bort application so that
  you can upload it to the Google Play Store if necessary.

- Apply [AOSP patches](https://github.com/memfault/bort/tree/master/patches)
  using the `bort_cli.py` tool (requires Python 3.6+).

```
packages/apps/bort/bort_cli.py patch-aosp \
  --android-release 10 \
  <AOSP_ROOT>
```

- Patch the default application ID with your own:

```
packages/apps/bort/bort_cli.py patch-bort \
  --bort-app-id <YOUR_BORT_APPLICATION_ID> \
  <AOSP_ROOT>/packages/apps/bort/MemfaultBort
```

```
packages/apps/bort/bort_cli.py patch-dumpstate-runner \
  --bort-app-id <YOUR_BORT_APPLICATION_ID> \
  <AOSP_ROOT>/packages/apps/bort/MemfaultDumpstateRunner
```

- Add this line to your `device.mk` file, to get the components included in your
  build:

```
include packages/apps/bort/product.mk
```

- Add this line to your `BoardConfig.mk` file, to get the sepolicy files picked
  up by the build system:

  ```
  include packages/apps/bort/BoardConfig.mk
  ```

  Verify whether `BOARD_SEPOLICY_DIRS` and `BOARD_PLAT_PRIVATE_SEPOLICY_DIR`
  makefile variables in your `BoardConfig.mk` use `+=` and not `:=`, or, ensure
  that the SDK's `BoardConfig.mk` is included at the very end, after your own
  assignments to the aforementioned variables.

### When are bug reports collected?

The bort app periodically generates bug reports by scheduling a periodic task to
trigger the `MemfaultDumpstateRunner`.

The bort app registers a handler (broadcast receiver) for the system boot event
as well as when the app itself is updated or installed. When either of these
happens, the app will register the periodic task if one is not registered.

Configuration of the task period as well as the initial delay for when the first
bug report is generated (e.g. if you wish to wait until more data is available)
is described below.

Bug reports can also be generated via an intent as described below.

### Configure the Bort app

**You must set your project API key in the Bort app**. This is set in
`MemfaultBort/bort.properties`; the app will not compile without this property.
We also recommend updating the other properties as necessary, for example,
updating the SDK levels to match the API level of your OS and updating the build
tools version to what is available in your environment.

Additional settings can also be configured this file, such as the bug report
generation (request) interval, in hours; the minimum log level that will be
logged to LogCat; and the maximum number of times bort will attempt to re-try
uploading a bug report.

You must provide the Android SDK location for gradle. This can either be set
with the `ANDROID_HOME` env var, or by opening the project with Android Studio
(e.g. opening the root `build.gradle`) which will auto-generate a
`local.properties` file with the `sdk.dir` property.

### Create a keystore for the Bort app

**The bort app requires a Java keystore file so that the app can be signed**.

Instructions on how to create a keystore in Android Studio can be found
[here](https://developer.android.com/studio/publish/app-signing#generate-key).
If you plan to update the app via the Play Store, you may wish to follow the
additional instructions on that page.

> _Important Notes_
>
> - The key that is used to sign the Bort app must NOT be the platform signing
>   key, otherwise updates to the Bort app may be rejected by the Play Store.
> - The key must ONLY be used to sign the Bort app and no other apps. Special
>   permissions are assigned to Bort based on the signing certificate.

Once you have a keystore, set up a `keystore.properties` file and provide the
path to it via the `bort.properties` file:

```
BORT_KEYSTORE_PROPERTIES_PATH=keystore.properties
```

The `keystore.properties` file must contain these properties:

```
keyAlias=myKey # e.g. key0
keyPassword=mySecretKeyPassword
storeFile=myKeystore.jks
storePassword=mySecretStorePassword
```

### Enabling the SDK at Runtime

By default, the SDK assumes it is running on a device that may contain
Personally Identifiable Information (PII). As such, it will not run until it is
explicitly told to do so (e.g. when user consent has been obtained). Once
enabled, that value will be persisted in preferences. If the bort app's data is
cleared, this value must be set again.

To enable the SDK, send an intent to the `ControlReceiver`. Note that the sender
_must_ hold the permission specified in the `BORT_CONTROL_PERMISSION` property
in `bort.properties`. The default is `com.memfault.bort.permission.CONTROL`,
which may only be may only be granted to applications signed with the same
signing key as `MemfaultBort` (`signature`) or applications that are installed
as privileged apps on the system image (`privileged`). See
[Android protectionLevel](https://developer.android.com/reference/android/R.attr#protectionLevel)
for further documentation.

```kotlin
Intent("com.memfault.intent.action.BORT_ENABLE").apply {
    component = ComponentName(
        APPLICATION_ID_BORT, // Whatever you have chosen for the application ID
        "com.memfault.bort.receivers.ControlReceiver"
    )
    putExtra("com.memfault.intent.extra.BORT_ENABLED", true)
}.also {
    context.sendBroadcast(it)
}
```

To disable the SDK, for example if the user later revokes consent, simply send
the same intent with the opposite boolean extra

```kotlin
Intent("com.memfault.intent.action.BORT_ENABLE").apply {
    component = ComponentName(
        APPLICATION_ID_BORT,
        "com.memfault.bort.receivers.ControlReceiver"
    )
    putExtra("com.memfault.intent.extra.BORT_ENABLED", false) // <-- Now disabled
}.also {
    context.sendBroadcast(it)
}
```

If the SDK is running on a device that does not require user consent, this
requirement can be disabled by changing a property in `bort.properties`:

```
RUNTIME_ENABLE_REQUIRED=false
```

If you wish to enable the SDK on a development device over ADB, `bort_cli.py`
provides a convenience command:

```bash
./bort_cli.py enable-bort --bort-app-id your.app.id
```

### Optional: Upload to a custom endpoint

If you wish to upload bug reports to Memfault via your own server, you can
enable this by providing a custom `FileUploader`.

Implement the uploader and set the factory in the application's `onCreate`:

```diff
+import retrofit2.Retrofit
+import vnd.myandroid.bortappid.SampleBugReportUploader


 open class Bort : Application(), Configuration.Provider {
@@ -38,7 +40,15 @@ open class Bort : Application(), Configuration.Provider {
         }
     }

-    open fun initComponents(): AppComponents.Builder = AppComponents.Builder(this)
+    open fun initComponents(): AppComponents.Builder = AppComponents.Builder(this).apply {
+        fileUploaderFactory = object : FileUploaderFactory {
+            override fun create(retrofit: Retrofit, projectApiKey: String): FileUploader =
+                SampleBugReportUploader(
+                    retrofit = retrofit,
+                    apiKey = projectApiKey
+                )
+        }
+    }

     companion object {
         private var appComponentsBuilder: AppComponents.Builder? = null
```

a sample uploader is provided in
[`SampleBugReportUploader.kt`](https://github.com/memfault/bort/blob/master/MemfaultBort/app/src/main/java/vnd/myandroid/bortappid/SampleBugReportUploader.kt).

### Optional: Triggering a one-off bug report

In addition to generating bug reports at regular intervals, you may also wish to
capture a bug report if a significant event occurs. Doing this will not affect
the scheduled bug reports.

Note that if the dumpstate runner is busy capturing a bug report already, the
in-flight bug report will continue and the interrupting request will be ignored.

Triggering a bug report requires that the sender hold the permission specified
in the `BORT_CONTROL_PERMISSION` property in `bort.properties`. The default is
the `com.memfault.bort.permission.CONTROL`.

```kotlin
Intent("com.memfault.intent.action.REQUEST_BUG_REPORT").apply {
    component = ComponentName(
        APPLICATION_ID_BORT,
        "com.memfault.bort.receivers.ControlReceiver"
    )
}.also {
    context.sendBroadcast(it)
}
```

If you wish to generate a bug report using the SDK on a development device over
ADB, `bort_cli.py` provides a convenience command:

```bash
./bort_cli.py request-bug-report --bort-app-id your.app.id
```

### Build the Bort APK

The `MemfaultBort` app is built using gradle. Building the release APK will
automatically invoke a task to copy the resulting APK and place it in the root
directory where it will be picked up by the AOSP build system.

```
cd MemfaultBort && ./gradlew assembleRelease # Or gradlew assembleRelease on Windows
```

This will create a signed `MemfaultBort.apk` and `MemfaultBort.x509.pem` file.
The `pem` file is a public certificate used by the system to when enforcing the
SE policy.

### Configure the UsageReporter app

We recommend updating the SDK levels in
`MemfaultUsageReporter/gradle.properties` to match the API level of your OS and
to match the build tools version that is available.

You must provide the Android SDK location for gradle. This can either be set
with the `ANDROID_HOME` env var, or by opening the project with Android Studio
(e.g. opening the root `build.gradle`) which will auto-generate a
`local.properties` file with the `sdk.dir` property.

### Build the UsageReporter APK

The `MemfaultUsageReporter` app is built using gradle. Building the release APK
will automatically invoke a task to copy the resulting APK and place it in the
root directory where it will be picked up by the AOSP build system.

```
cd MemfaultUsageReporter && ./gradlew assembleRelease # Or gradlew assembleRelease on Windows
```

## Validating the SDK Integration

The `bort_cli.py` tool can be used to check for issues with the SDK
installation. To use it, install a build containing the Bort SDK on a device
that you wish to validate. Connect that device via ADB (verify via
`adb devices`) and run the script:

```bash
./bort_cli.py validate-sdk-integration --bort-app-id your.app.id
```

If you have multiple devices connected, use the `--device` flag to specify the
target device. For more information on the different options, run the command
with the `-h` flag:

```bash
./bort_cli.py validate-sdk-integration -h
```
