package com.memfault.bort.boot

import android.content.ContentResolver
import android.provider.Settings
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

class BootCountTracker
@Inject constructor(
    private val lastTrackedBootCountProvider: LastTrackedBootCountProvider,
    private val rebootEventUploader: RebootEventUploader,
    private val settingsProvider: SettingsProvider,
    private val androidBootCount: AndroidBootCount,
) {
    suspend fun trackIfNeeded() {
        if (!settingsProvider.rebootEventsSettings.dataSourceEnabled) {
            return
        }

        val bootCount = androidBootCount()
        if (bootCount <= lastTrackedBootCountProvider.bootCount) {
            Logger.v("Boot $bootCount already tracked")
            return
        }

        rebootEventUploader.handleUntrackedBootCount(bootCount)

        lastTrackedBootCountProvider.bootCount = bootCount
        Logger.v("Tracked boot $bootCount")
    }
}

fun interface AndroidBootCount : () -> Int

@ContributesBinding(SingletonComponent::class)
class RealAndroidBootCount @Inject constructor(
    private val contentResolver: ContentResolver,
) : AndroidBootCount {
    override fun invoke(): Int {
        return Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT)
    }
}
