package com.memfault.bort.metrics

import android.content.SharedPreferences
import com.memfault.bort.boot.LinuxBootId
import com.memfault.bort.shared.SerializedCachedPreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

interface DiskActivityStorage {
    var state: DiskActivity
}

@ContributesBinding(SingletonComponent::class, boundType = DiskActivityStorage::class)
class RealDiskActivityStorage @Inject constructor(
    sharedPreferences: SharedPreferences,
    readBootId: LinuxBootId,
) : DiskActivityStorage, SerializedCachedPreferenceKeyProvider<DiskActivity>(
    sharedPreferences,
    DiskActivity.EMPTY.copy(bootId = readBootId()),
    DiskActivity.serializer(),
    "DISK_ACTIVITY",
)
