package com.memfault.bort.dropbox

import android.content.SharedPreferences
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.PREFERENCE_TOKEN_BUCKET_ANRS
import com.memfault.bort.PREFERENCE_TOKEN_BUCKET_JAVA_EXCEPTIONS
import com.memfault.bort.PREFERENCE_TOKEN_BUCKET_KMSG
import com.memfault.bort.PREFERENCE_TOKEN_BUCKET_TOMBSTONES
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.time.BootRelativeTimeProvider
import com.memfault.bort.tokenbucket.RealTokenBucketFactory
import com.memfault.bort.tokenbucket.RealTokenBucketStorage
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueFileUpload
import kotlin.time.minutes

fun realDropBoxEntryProcessors(
    bootRelativeTimeProvider: BootRelativeTimeProvider,
    tempFileFactory: TemporaryFileFactory,
    enqueueFileUpload: EnqueueFileUpload,
    packageManagerClient: PackageManagerClient,
    deviceInfoProvider: DeviceInfoProvider,
    sharedPreferences: SharedPreferences,
): Map<String, EntryProcessor> {
    val tombstoneEntryProcessor = TombstoneEntryProcessor(
        tempFileFactory = tempFileFactory,
        enqueueFileUpload = enqueueFileUpload,
        bootRelativeTimeProvider = bootRelativeTimeProvider,
        deviceInfoProvider = deviceInfoProvider,
        packageManagerClient = packageManagerClient,
        tokenBucketStore = TokenBucketStore(
            storage = RealTokenBucketStorage(
                sharedPreferences,
                PREFERENCE_TOKEN_BUCKET_TOMBSTONES
            ),
            maxBuckets = 1,
            tokenBucketFactory = RealTokenBucketFactory(
                defaultCapacity = 10,
                defaultPeriod = 15.minutes,
            ),
        ),
    )
    val javaExceptionEntryProcessor = JavaExceptionEntryProcessor(
        tempFileFactory = tempFileFactory,
        enqueueFileUpload = enqueueFileUpload,
        bootRelativeTimeProvider = bootRelativeTimeProvider,
        deviceInfoProvider = deviceInfoProvider,
        tokenBucketStore = TokenBucketStore(
            storage = RealTokenBucketStorage(
                sharedPreferences,
                PREFERENCE_TOKEN_BUCKET_JAVA_EXCEPTIONS
            ),
            // Note: the backtrace signature is used as key, so one bucket per issue basically.
            maxBuckets = 100,
            tokenBucketFactory = RealTokenBucketFactory(
                defaultCapacity = 4,
                defaultPeriod = 15.minutes,
            ),
        ),
    )
    val anrEntryProcessor = AnrEntryProcessor(
        tempFileFactory = tempFileFactory,
        enqueueFileUpload = enqueueFileUpload,
        bootRelativeTimeProvider = bootRelativeTimeProvider,
        deviceInfoProvider = deviceInfoProvider,
        tokenBucketStore = TokenBucketStore(
            storage = RealTokenBucketStorage(
                sharedPreferences,
                PREFERENCE_TOKEN_BUCKET_ANRS
            ),
            maxBuckets = 1,
            tokenBucketFactory = RealTokenBucketFactory(
                defaultCapacity = 10,
                defaultPeriod = 15.minutes,
            ),
        ),
    )
    val kmsgEntryProcessor = KmsgEntryProcessor(
        tempFileFactory = tempFileFactory,
        enqueueFileUpload = enqueueFileUpload,
        bootRelativeTimeProvider = bootRelativeTimeProvider,
        deviceInfoProvider = deviceInfoProvider,
        tokenBucketStore = TokenBucketStore(
            storage = RealTokenBucketStorage(
                sharedPreferences,
                PREFERENCE_TOKEN_BUCKET_KMSG
            ),
            maxBuckets = 1,
            tokenBucketFactory = RealTokenBucketFactory(
                defaultCapacity = 10,
                defaultPeriod = 15.minutes,
            ),
        ),
    )
    return mapOf(
        *tombstoneEntryProcessor.tagPairs(),
        *javaExceptionEntryProcessor.tagPairs(),
        *anrEntryProcessor.tagPairs(),
        *kmsgEntryProcessor.tagPairs(),
    )
}
