package com.memfault.bort.settings

import android.content.Context
import android.os.RemoteException
import com.github.michaelbull.result.onFailure
import com.memfault.bort.BortJson
import com.memfault.bort.DumpsterClient
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.shared.Logger
import com.memfault.bort.structuredlog.StructuredLog

fun realSettingsUpdateCallback(
    context: Context,
    reporterServiceConnector: ReporterServiceConnector,
    dumpsterClient: DumpsterClient,
): SettingsUpdateCallback = { settingsProvider, fetchedSettingsUpdate ->
    applyReporterServiceSettings(reporterServiceConnector, settingsProvider)

    // Pass the new settings to structured logging
    StructuredLog.reloadConfig(
        BortJson.encodeToString(
            StructuredLogDaemonSettings.serializer(), fetchedSettingsUpdate.new.toStructuredLogDaemonSettings()
        )
    )

    dumpsterClient.setStructuredLogEnabled(settingsProvider.structuredLogSettings.dataSourceEnabled)

    // Update periodic tasks that might have changed after a settings update
    PeriodicRequesterRestartTask.schedule(context, fetchedSettingsUpdate)
}

suspend fun applyReporterServiceSettings(
    reporterServiceConnector: ReporterServiceConnector,
    settingsProvider: SettingsProvider
) {
    try {
        reporterServiceConnector.connect { getConnection ->
            getConnection().setLogLevel(settingsProvider.minLogLevel).onFailure {
                Logger.w("could not send log level to reporter service", it)
            }
        }
    } catch (e: RemoteException) {
        // This happens if UsageReporter is so old that it does not contain the ReporterService at all:
        Logger.w("Unable to connect to ReporterService to set log level")
    }
}

private fun FetchedSettings.toStructuredLogDaemonSettings(): StructuredLogDaemonSettings =
    StructuredLogDaemonSettings(
        structuredLogDataSourceEnabled = structuredLogDataSourceEnabled,
        structuredLogDumpPeriod = structuredLogDumpPeriod,
        structuredLogMaxMessageSizeBytes = structuredLogMaxMessageSizeBytes,
        structuredLogMinStorageThresholdBytes = structuredLogMinStorageThresholdBytes,
        structuredLogNumEventsBeforeDump = structuredLogNumEventsBeforeDump,
        structuredLogRateLimitingSettings = structuredLogRateLimitingSettings
    )
