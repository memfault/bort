package com.memfault.bort.settings

import android.content.Context
import com.github.michaelbull.result.onFailure
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.shared.Logger

fun realSettingsUpdateCallback(
    context: Context,
    reporterServiceConnector: ReporterServiceConnector,
): SettingsUpdateCallback = { settingsProvider ->
    applyReporterServiceSettings(reporterServiceConnector, settingsProvider)

    // Update periodic tasks that might have changed after a settings update
    PeriodicRequesterRestartTask.schedule(context)
}

suspend fun applyReporterServiceSettings(
    reporterServiceConnector: ReporterServiceConnector,
    settingsProvider: SettingsProvider
) {
    reporterServiceConnector.connect { getConnection ->
        getConnection().setLogLevel(settingsProvider.minLogLevel).onFailure {
            Logger.w("could not send log level to reporter service", it)
        }
    }
}
