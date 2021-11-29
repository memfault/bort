package com.memfault.bort.dropbox

import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.PackageNameAllowList
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.logcat.NextLogcatCidProvider
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.metrics.HeartbeatReportCollector
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.time.BaseBootRelativeTime
import com.memfault.bort.time.BootRelativeTimeProvider
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueUpload

fun realDropBoxEntryProcessors(
    bootRelativeTimeProvider: BootRelativeTimeProvider,
    tempFileFactory: TemporaryFileFactory,
    enqueueUpload: EnqueueUpload,
    nextLogcatCidProvider: NextLogcatCidProvider,
    packageManagerClient: PackageManagerClient,
    deviceInfoProvider: DeviceInfoProvider,
    builtinMetricsStore: BuiltinMetricsStore,
    handleEventOfInterest: (eventTime: BaseBootRelativeTime) -> Unit,
    tombstoneTokenBucketStore: TokenBucketStore,
    javaExceptionTokenBucketStore: TokenBucketStore,
    anrTokenBucketStore: TokenBucketStore,
    kmsgTokenBucketStore: TokenBucketStore,
    structuredLogTokenBucketStore: TokenBucketStore,
    metricReportTokenBucketStore: TokenBucketStore,
    packageNameAllowList: PackageNameAllowList,
    combinedTimeProvider: CombinedTimeProvider,
    settingsProvider: SettingsProvider,
    heartbeatReportCollector: HeartbeatReportCollector,
    marFileTokenBucketStore: TokenBucketStore,
): Map<String, EntryProcessor> {
    val tombstoneEntryProcessor = UploadingEntryProcessor(
        delegate = TombstoneUploadingEntryProcessorDelegate(
            packageManagerClient = packageManagerClient,
        ),
        tempFileFactory = tempFileFactory,
        enqueueUpload = enqueueUpload,
        nextLogcatCidProvider = nextLogcatCidProvider,
        bootRelativeTimeProvider = bootRelativeTimeProvider,
        deviceInfoProvider = deviceInfoProvider,
        tokenBucketStore = tombstoneTokenBucketStore,
        builtinMetricsStore = builtinMetricsStore,
        handleEventOfInterest = handleEventOfInterest,
        packageNameAllowList = packageNameAllowList,
        combinedTimeProvider = combinedTimeProvider,
    )
    val javaExceptionEntryProcessor = UploadingEntryProcessor(
        delegate = JavaExceptionUploadingEntryProcessorDelegate(),
        tempFileFactory = tempFileFactory,
        enqueueUpload = enqueueUpload,
        nextLogcatCidProvider = nextLogcatCidProvider,
        bootRelativeTimeProvider = bootRelativeTimeProvider,
        deviceInfoProvider = deviceInfoProvider,
        tokenBucketStore = javaExceptionTokenBucketStore,
        builtinMetricsStore = builtinMetricsStore,
        handleEventOfInterest = handleEventOfInterest,
        packageNameAllowList = packageNameAllowList,
        combinedTimeProvider = combinedTimeProvider,
    )
    val anrEntryProcessor = UploadingEntryProcessor(
        delegate = AnrUploadingEntryProcessorDelegate(),
        tempFileFactory = tempFileFactory,
        enqueueUpload = enqueueUpload,
        nextLogcatCidProvider = nextLogcatCidProvider,
        bootRelativeTimeProvider = bootRelativeTimeProvider,
        deviceInfoProvider = deviceInfoProvider,
        tokenBucketStore = anrTokenBucketStore,
        builtinMetricsStore = builtinMetricsStore,
        handleEventOfInterest = handleEventOfInterest,
        packageNameAllowList = packageNameAllowList,
        combinedTimeProvider = combinedTimeProvider,
    )
    val kmsgEntryProcessor = UploadingEntryProcessor(
        delegate = KmsgUploadingEntryProcessorDelegate(),
        tempFileFactory = tempFileFactory,
        enqueueUpload = enqueueUpload,
        nextLogcatCidProvider = nextLogcatCidProvider,
        bootRelativeTimeProvider = bootRelativeTimeProvider,
        deviceInfoProvider = deviceInfoProvider,
        tokenBucketStore = kmsgTokenBucketStore,
        builtinMetricsStore = builtinMetricsStore,
        handleEventOfInterest = handleEventOfInterest,
        packageNameAllowList = packageNameAllowList,
        combinedTimeProvider = combinedTimeProvider,
    )
    val structuredLogProcessor = StructuredLogEntryProcessor(
        temporaryFileFactory = tempFileFactory,
        deviceInfoProvider = deviceInfoProvider,
        tokenBucketStore = structuredLogTokenBucketStore,
        enqueueUpload = enqueueUpload,
        combinedTimeProvider = combinedTimeProvider,
        structuredLogDataSourceEnabledConfig = { settingsProvider.structuredLogSettings.dataSourceEnabled }
    )
    val metricReportProcessor = MetricReportEntryProcessor(
        temporaryFileFactory = tempFileFactory,
        tokenBucketStore = metricReportTokenBucketStore,
        metricReportEnabledConfig = { settingsProvider.structuredLogSettings.metricsReportEnabled },
        heartbeatReportCollector = heartbeatReportCollector,
    )
    val clientServerFileUploadProcessor = ClientServerFileUploadProcessor(
        tempFileFactory = tempFileFactory,
        enqueueUpload = enqueueUpload,
        deviceInfoProvider = deviceInfoProvider,
        combinedTimeProvider = combinedTimeProvider,
        tokenBucketStore = marFileTokenBucketStore,
    )
    return mapOf(
        *tombstoneEntryProcessor.tagPairs(),
        *javaExceptionEntryProcessor.tagPairs(),
        *anrEntryProcessor.tagPairs(),
        *kmsgEntryProcessor.tagPairs(),
        *structuredLogProcessor.tagPairs(),
        *metricReportProcessor.tagPairs(),
        *clientServerFileUploadProcessor.tagPairs(),
    )
}
