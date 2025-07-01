package com.memfault.bort.metrics

import android.os.Environment
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

interface DiskSpaceProvider {
    fun getFreeBytes(): Long
    fun getTotalBytes(): Long
}

@Singleton
@ContributesBinding(SingletonComponent::class, boundType = DiskSpaceProvider::class)
class RealDiskSpaceProvider @Inject constructor() : DiskSpaceProvider {
    override fun getFreeBytes(): Long = Environment.getDataDirectory().freeSpace

    override fun getTotalBytes(): Long = Environment.getDataDirectory().totalSpace
}
