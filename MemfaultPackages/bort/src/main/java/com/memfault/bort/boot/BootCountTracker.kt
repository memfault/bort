package com.memfault.bort.boot

import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.Logger
import javax.inject.Inject

class BootCountTracker
@Inject constructor(
    private val lastTrackedBootCountProvider: LastTrackedBootCountProvider,
    private val rebootEventUploader: RebootEventUploader,
    private val settingsProvider: SettingsProvider,
) {
    suspend fun trackIfNeeded(bootCount: Int) {
        if (!settingsProvider.rebootEventsSettings.dataSourceEnabled) return

        if (bootCount <= lastTrackedBootCountProvider.bootCount) {
            Logger.v("Boot $bootCount already tracked")
            return
        }

        rebootEventUploader.handleUntrackedBootCount(bootCount)

        lastTrackedBootCountProvider.bootCount = bootCount
        Logger.v("Tracked boot $bootCount")
    }
}
