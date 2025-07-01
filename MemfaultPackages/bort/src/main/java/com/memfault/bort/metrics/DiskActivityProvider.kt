package com.memfault.bort.metrics

import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface DiskActivityProvider {
    fun getDiskActivity(): DiskActivity
}

@Singleton
@ContributesBinding(SingletonComponent::class, boundType = DiskActivityProvider::class)
class RealDiskActivityProvider @Inject constructor(
    private val diskActivityParser: DiskActivityParser,
) : DiskActivityProvider {
    override fun getDiskActivity(): DiskActivity =
        diskActivityParser.parse(File(DISK_STATS_FILE))

    companion object {
        const val DISK_STATS_FILE = "/proc/diskstats"
    }
}
