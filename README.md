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

* Patch the repository with `bort_cli.py` (requires Python 3.2+):

  ```
  packages/apps/bort/bort_cli.py patch-aosp \
    --android-release 10 \
    <AOSP_ROOT>
  ```

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

* Add this line to your `device.mk` file, to get the components included in your
  build:

  ```
  include packages/apps/bort/product.mk
  ```

- Add this line to your `BoardConfig.mk` file, to get the sepolicy files picked
  up by the build system:

  ```
  include packages/apps/bort/BoardConfig.mk
  ```

### Configure the Bort app

**You must set your project API key in the Bort app**. This is set in
`MemfaultBort/bort.properties`; the app will not compile without this property.
We also recommend updating the other properties as necessary, for example,
updating the SDK levels to match the API level of your OS and updating the build
tools version to what is available in your environment.

Additional settings can also be configured in `Settings.kt`, such as the bug
report generation (request) interval, in hours; the minimum log level that will
be logged to LogCat; and the maximum number of times bort will attempt to re-try
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

### Optional: Upload to a custom endpoint

If you wish to upload bug reports to Memfault via your own server, you can
enable this by providing a custom `FileUploader`.

Implement the uploader and set the factory in the application's `onCreate`:

```diff
+import retrofit2.Retrofit
+import vnd.myandroid.bortappid.SampleBugReportUploader


 class Bort : Application(), Configuration.Provider {

     override fun onCreate() {
         super.onCreate()
+
+        ComponentsBuilder().apply {
+            fileUploaderFactory = object : FileUploaderFactory {
+                override fun create(retrofit: Retrofit, projectApiKey: String): FileUploader =
+                    SampleBugReportUploader(
+                        retrofit,
+                        apiKey
+                    )
+            }
+        }.also {
+            updateComponents(it)
+        }
```

a sample uploader is provided in
[`SampleBugReportUploader.kt`](https://github.com/memfault/bort/blob/master/MemfaultBort/app/src/main/java/vnd/myandroid/bortappid/SampleBugReportUploader.kt).

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
