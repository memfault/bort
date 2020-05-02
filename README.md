# Bort: Memfault Bug Report SDK for AOSP

## Overview

The purpose of this SDK is to make it really easy to periodically capture bug
reports on AOSP devices and get them uploaded automatically to Memfault.

At a high level, the process of capturing and uploading involves these
components that work together:

### com.memfault.bort

A privileged system app that is responsible for periodically invoking the
`MemfaultDumpstateRunner`. When it receives a bug report back from the
`MemfaultDumpstateRunner`, the bort app schedules that bug report to be uploaded
when internet is available.

The bort app is built using gradle. Instructions on how to build and include the
APK as a prebuilt artifact are provided in the integration section below.

### /system/bin/MemfaultDumpstateRunner

This native program is exposed as an "init.rc service" (see `memfault_init.rc`
for details). This is so that it can be executed in the required `dumpstate`
security context, but get triggered by the `com.memfault.bort` system app. The
`com.memfault.bort` app runs in the much less capable system app security
context.

The responsibility of this program is to 1) trigger `/system/bin/dumpstate`
(through another init.rc service, `memfault_dumpstatez`) 2) copy the
bugreport.zip file out of the shell file system sandbox and into
`com.memfault.bort`'s file system sandbox 3) broadcast an Intent with the final
path such that `com.memfault.bort` can process it further.

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

> The modifications that are listed in this section can also be found as git
> patch files in the `patches/` folder in this SDK.

- Add this repo to your tree at `packages/apps/bort` (i.e. add it to your repo
  manifest).

- Add this line to your `device.mk` file, to get the components included in your
  build:

```
PRODUCT_PACKAGES += MemfaultDumpstateRunner MemfaultBort
```

- Add this line to your `BoardConfig.mk` file, to get the sepolicy files picked
  up by the build system:

```
BOARD_SEPOLICY_DIRS += packages/apps/bort/sepolicy
```

- Apply `system/sepolicy` patch

A patch is needed to change AOSP's system/sepolicy to allow file creation into a
`system_app_data_file` context from a `dumpstate` context:

```
diff --git a/prebuilts/api/29.0/public/domain.te b/prebuilts/api/29.0/public/domain.te
index 987bb9f2d..dcdaca1e9 100644
--- a/prebuilts/api/29.0/public/domain.te
+++ b/prebuilts/api/29.0/public/domain.te
@@ -1171,6 +1171,7 @@ neverallow {
   -traced_probes # resolve inodes for i/o tracing.
                  # only needs open and read, the rest is neverallow in
                  # traced_probes.te.
+  -dumpstate # Memfault
 } system_app_data_file:dir_file_class_set { create unlink open };
 neverallow {
   isolated_app
diff --git a/public/domain.te b/public/domain.te
index 987bb9f2d..dcdaca1e9 100644
--- a/public/domain.te
+++ b/public/domain.te
@@ -1171,6 +1171,7 @@ neverallow {
   -traced_probes # resolve inodes for i/o tracing.
                  # only needs open and read, the rest is neverallow in
                  # traced_probes.te.
+  -dumpstate # Memfault
 } system_app_data_file:dir_file_class_set { create unlink open };
 neverallow {
   isolated_app
```

- Apply `frameworks/base` patch

This patch is required to allow Memfault to upload bug reports in the
background. Without it, an upload may be terminated when the device transitions
into `IDLE` mode.

```
diff --git a/data/etc/platform.xml b/data/etc/platform.xml
index 233f82640a2..e1c32ab37eb 100644
--- a/data/etc/platform.xml
+++ b/data/etc/platform.xml
@@ -229,6 +229,10 @@
          access while in power save mode, even if they aren't in the foreground. -->
     <allow-in-power-save package="com.android.providers.downloads" />

+    <!-- Memfault requires internet access while in power save mode in order to upload
+        while in the background -->
+    <allow-in-power-save package="com.memfault.bort" />
+
     <!-- These are the standard packages that are white-listed to always have internet
          access while in data mode, even if they aren't in the foreground. -->
     <allow-in-data-usage-save package="com.android.providers.downloads" />
```

### Configure the Bort app

**You must set your project API key in the Bort app**. This is set in
`app/gradle.properties`; the app will not compile without this property.

Additional settings can also be configured in `Settings.kt`, such as the bug
report generation (request) interval, in hours; the minimum log level that will
be logged to LogCat; and the maximum number of times bort will attempt to re-try
uploading a bug report.

### Build the Bort APK

The `MemfaultBort` app is built using gradle. Building the release APK will
automatically invoke a task to copy the resulting APK and place it in the root
directory where it will be picked up by the AOSP build system.

```
cd MemfaultBort && ./gradlew assembleRelease
```
