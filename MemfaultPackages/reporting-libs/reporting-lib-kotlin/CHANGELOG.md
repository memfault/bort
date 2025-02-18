# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
currently does not attempt to adhere to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

### Changed

### Deprecated

### Removed

### Fixed

## [1.6.0] - 2024-11-20

### Added

- Created new `ReportingClient` class to support passing a custom `ExecutorService` for the
  ContentProvider IPC or Binder IPC to run on, or passing an `Application` `Context`, which allows
  for bypassing the ContentProvider static context in `MetricsInitProvider`. If you use the
  `ReportingClient` with an `Application` `Context`, you can use `tools:node="remove"` to remove the
  `MetricsInitProvider` from your `AndroidManifest.xml`, which will disable its initialization.

```xml
  <provider
      android:name="com.memfault.bort.reporting.MetricsInitProvider"
      android:authorities="${applicationId}.com.memfault.metrics.init"
       />
```

### Changed

- The `Reporting` object now runs on `newSingleThreadExecutor` by default. Many methods now return a
  `CompletableFuture<T>` instead of `void` or `boolean`, which breaks binary compatibility.

## [1.5] - 2024-06-24

### Added

- Support recording Session metrics. Sessions enables support for aggregations across variable
  durations, by associating metrics to an activity, rather than the hourly Heartbeat. Sessions
  require Android SDK >= 4.17.0.
