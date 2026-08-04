package com.memfault.bort.metrics

import android.os.Build
import android.os.Build.VERSION_CODES.O
import android.os.Process
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface UidIoStatsProvider {
    fun getUidIoStats(): UidIoStats
}

@Singleton
@ContributesBinding(SingletonComponent::class, boundType = UidIoStatsProvider::class)
class RealUidIoStatsProvider @Inject constructor(
    private val parser: UidIoStatsParser,
) : UidIoStatsProvider {
    override fun getUidIoStats(): UidIoStats {
        // /proc/uid_io/stats requires Android 8 (API 26) or newer
        if (Build.VERSION.SDK_INT < O) return UidIoStats.EMPTY
        return parser.parse(Process.myUid(), File(UID_IO_STATS_FILE))
    }

    companion object {
        const val UID_IO_STATS_FILE = "/proc/uid_io/stats"
    }
}
