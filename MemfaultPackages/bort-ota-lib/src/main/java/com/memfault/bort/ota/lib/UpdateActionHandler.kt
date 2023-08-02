package com.memfault.bort.ota.lib

import android.app.Application
import com.memfault.bort.shared.BuildConfig
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

interface ScheduleDownload {
    fun scheduleDownload(ota: Ota)
}

@ContributesBinding(SingletonComponent::class)
class RealScheduleDownload @Inject constructor(
    private val context: Application,
    private val settings: SoftwareUpdateSettingsProvider,
    private val rulesProvider: OtaRulesProvider,
) : ScheduleDownload {
    override fun scheduleDownload(ota: Ota) {
        OtaDownloadWorker.schedule(context, settings, rulesProvider, ota)
    }
}

/**
 * An update action handler takes actions that manipulate the state and may issue events. Most work is done
 * in action handlers.
 */
interface UpdateActionHandler {
    suspend fun handle(
        state: State,
        action: Action,
    )

    suspend fun handleUpdateAvailable(
        updater: Updater,
        ota: Ota,
        action: Action.CheckForUpdate,
        scheduleDownload: ScheduleDownload,
    ) {
        val scheduleAutoDownload = action.background && (BuildConfig.OTA_AUTO_INSTALL || (ota.isForced == true))
        val showNotification = action.background && !scheduleAutoDownload
        updater.setState(State.UpdateAvailable(ota, showNotification = showNotification))
        if (scheduleAutoDownload) {
            scheduleDownload.scheduleDownload(ota)
        }
    }

    fun initialize()
}
