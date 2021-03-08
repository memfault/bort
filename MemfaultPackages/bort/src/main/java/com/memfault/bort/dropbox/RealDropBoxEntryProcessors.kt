package com.memfault.bort.dropbox

import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.PackageNameAllowList
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.logcat.NextLogcatCidProvider
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.time.BaseBootRelativeTime
import com.memfault.bort.time.BootRelativeTimeProvider
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueFileUpload

fun realDropBoxEntryProcessors(
    bootRelativeTimeProvider: BootRelativeTimeProvider,
    tempFileFactory: TemporaryFileFactory,
    enqueueFileUpload: EnqueueFileUpload,
    nextLogcatCidProvider: NextLogcatCidProvider,
    packageManagerClient: PackageManagerClient,
    deviceInfoProvider: DeviceInfoProvider,
    builtinMetricsStore: BuiltinMetricsStore,
    handleEventOfInterest: (eventTime: BaseBootRelativeTime) -> Unit,
    tombstoneTokenBucketStore: TokenBucketStore,
    javaExceptionTokenBucketStore: TokenBucketStore,
    anrTokenBucketStore: TokenBucketStore,
    kmsgTokenBucketStore: TokenBucketStore,
    packageNameAllowList: PackageNameAllowList,
): Map<String, EntryProcessor> {
    val tombstoneEntryProcessor = UploadingEntryProcessor(
        delegate = TombstoneUploadingEntryProcessorDelegate(
            packageManagerClient = packageManagerClient,
        ),
        tempFileFactory = tempFileFactory,
        enqueueFileUpload = enqueueFileUpload,
        nextLogcatCidProvider = nextLogcatCidProvider,
        bootRelativeTimeProvider = bootRelativeTimeProvider,
        deviceInfoProvider = deviceInfoProvider,
        tokenBucketStore = tombstoneTokenBucketStore,
        builtinMetricsStore = builtinMetricsStore,
        handleEventOfInterest = handleEventOfInterest,
        packageNameAllowList = packageNameAllowList,
    )
    val javaExceptionEntryProcessor = UploadingEntryProcessor(
        delegate = JavaExceptionUploadingEntryProcessorDelegate(),
        tempFileFactory = tempFileFactory,
        enqueueFileUpload = enqueueFileUpload,
        nextLogcatCidProvider = nextLogcatCidProvider,
        bootRelativeTimeProvider = bootRelativeTimeProvider,
        deviceInfoProvider = deviceInfoProvider,
        tokenBucketStore = javaExceptionTokenBucketStore,
        builtinMetricsStore = builtinMetricsStore,
        handleEventOfInterest = handleEventOfInterest,
        packageNameAllowList = packageNameAllowList,
    )
    val anrEntryProcessor = UploadingEntryProcessor(
        delegate = AnrUploadingEntryProcessorDelegate(),
        tempFileFactory = tempFileFactory,
        enqueueFileUpload = enqueueFileUpload,
        nextLogcatCidProvider = nextLogcatCidProvider,
        bootRelativeTimeProvider = bootRelativeTimeProvider,
        deviceInfoProvider = deviceInfoProvider,
        tokenBucketStore = anrTokenBucketStore,
        builtinMetricsStore = builtinMetricsStore,
        handleEventOfInterest = handleEventOfInterest,
        packageNameAllowList = packageNameAllowList,
    )
    val kmsgEntryProcessor = UploadingEntryProcessor(
        delegate = KmsgUploadingEntryProcessorDelegate(),
        tempFileFactory = tempFileFactory,
        enqueueFileUpload = enqueueFileUpload,
        nextLogcatCidProvider = nextLogcatCidProvider,
        bootRelativeTimeProvider = bootRelativeTimeProvider,
        deviceInfoProvider = deviceInfoProvider,
        tokenBucketStore = kmsgTokenBucketStore,
        builtinMetricsStore = builtinMetricsStore,
        handleEventOfInterest = handleEventOfInterest,
        packageNameAllowList = packageNameAllowList,
    )
    return mapOf(
        *tombstoneEntryProcessor.tagPairs(),
        *javaExceptionEntryProcessor.tagPairs(),
        *anrEntryProcessor.tagPairs(),
        *kmsgEntryProcessor.tagPairs(),
    )
}
