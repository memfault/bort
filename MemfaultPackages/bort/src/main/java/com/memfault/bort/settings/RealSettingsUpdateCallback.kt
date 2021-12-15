package com.memfault.bort.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.RemoteException
import com.github.michaelbull.result.onFailure
import com.memfault.bort.BortJson
import com.memfault.bort.DumpsterClient
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.reporting.CustomEvent
import com.memfault.bort.shared.BuildConfig
import com.memfault.bort.shared.INTENT_ACTION_OTA_SETTINGS_CHANGED
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.OTA_RECEIVER_CLASS
import javax.inject.Inject
import kotlin.time.Duration.Companion.ZERO

class SettingsUpdateCallback @Inject constructor(
    private val context: Context,
    private val reporterServiceConnector: ReporterServiceConnector,
    private val dumpsterClient: DumpsterClient,
    private val bortEnabledProvider: BortEnabledProvider,
) {
    suspend fun onSettingsUpdated(
        settingsProvider: SettingsProvider,
        fetchedSettingsUpdate: FetchedSettingsUpdate
    ) {
        applyReporterServiceSettings(reporterServiceConnector, settingsProvider, bortEnabledProvider)

        // Pass the new settings to structured logging
        CustomEvent.reloadConfig(
            BortJson.encodeToString(
                StructuredLogDaemonSettings.serializer(), fetchedSettingsUpdate.new.toStructuredLogDaemonSettings()
            )
        )

        dumpsterClient.setStructuredLogEnabled(settingsProvider.structuredLogSettings.dataSourceEnabled)

        // Update periodic tasks that might have changed after a settings update
        PeriodicRequesterRestartTask.schedule(context, fetchedSettingsUpdate)

        with(settingsProvider) {
            Logger.minLogcatLevel = minLogcatLevel
            Logger.minStructuredLevel = minStructuredLogLevel
            Logger.eventLogEnabled = this::eventLogEnabled
            Logger.logToDisk = this::internalLogToDiskEnabled
            Logger.i("settings.updated", selectSettingsToMap())
        }

        // Notify OTA app that settings changed (if it is installed).
        context.sendBroadcast(
            Intent(INTENT_ACTION_OTA_SETTINGS_CHANGED).apply {
                component = ComponentName.createRelative(BuildConfig.BORT_OTA_APPLICATION_ID, OTA_RECEIVER_CLASS)
            }
        )
    }
}

suspend fun applyReporterServiceSettings(
    reporterServiceConnector: ReporterServiceConnector,
    settingsProvider: SettingsProvider,
    bortEnabledProvider: BortEnabledProvider,
) {
    try {
        reporterServiceConnector.connect { getConnection ->
            val connection = getConnection()
            connection.setLogLevel(settingsProvider.minLogcatLevel).onFailure {
                Logger.w("could not send log level to reporter service", it)
            }
            val metricCollectionPeriod = if (bortEnabledProvider.isEnabled()) {
                settingsProvider.metricsSettings.reporterCollectionInterval
            } else ZERO
            connection.setMetricsCollectionInterval(metricCollectionPeriod).onFailure {
                Logger.w("could not send metric collection interval to reporter service", it)
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
        structuredLogRateLimitingSettings = structuredLogRateLimitingSettings,
        structuredLogMetricReportEnabled = metricReportEnabled,
    )
