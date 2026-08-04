package com.memfault.bort.metrics

import android.content.SharedPreferences
import com.memfault.bort.boot.LinuxBootId
import com.memfault.bort.shared.SerializedCachedPreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

interface UidIoStatsStorage {
    var state: UidIoStats
}

@Singleton
@ContributesBinding(SingletonComponent::class, boundType = UidIoStatsStorage::class)
class RealUidIoStatsStorage @Inject constructor(
    sharedPreferences: SharedPreferences,
    readBootId: LinuxBootId,
) : UidIoStatsStorage, SerializedCachedPreferenceKeyProvider<UidIoStats>(
    sharedPreferences,
    UidIoStats(bootId = readBootId(), writtenBytes = 0),
    UidIoStats.serializer(),
    "UID_IO_STATS",
)
