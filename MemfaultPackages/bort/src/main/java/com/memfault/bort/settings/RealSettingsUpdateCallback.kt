@file:Suppress("DEPRECATION")

package com.memfault.bort.settings

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.RemoteException
import com.github.michaelbull.result.onFailure
import com.memfault.bort.BortJson
import com.memfault.bort.DumpsterClient
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.dropbox.DropBoxTagEnabler
import com.memfault.bort.receivers.DropBoxEntryAddedReceiver
import com.memfault.bort.reporting.CustomEvent
import com.memfault.bort.requester.PeriodicWorkRequester.PeriodicWorkManager
import com.memfault.bort.shared.BuildConfig
import com.memfault.bort.shared.INTENT_ACTION_OTA_SETTINGS_CHANGED
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.OTA_RECEIVER_CLASS
import com.memfault.bort.shared.SetReporterSettingsRequest
import com.memfault.bort.time.boxed
import javax.inject.Inject
import kotlin.time.Duration.Companion.ZERO

class SettingsUpdateCallback @Inject constructor(
    private val application: Application,
    private val reporterServiceConnector: ReporterServiceConnector,
    private val dumpsterClient: DumpsterClient,
    private val bortEnabledProvider: BortEnabledProvider,
    private val continuousLoggingController: ContinuousLoggingController,
    private val periodicWorkManager: PeriodicWorkManager,
    private val dropBoxEntryAddedReceiver: DropBoxEntryAddedReceiver,
    private val dropBoxTagEnabler: DropBoxTagEnabler,
) {
    suspend fun onSettingsUpdated(
        settingsProvider: SettingsProvider,
        fetchedSettingsUpdate: FetchedSettingsUpdate,
    ) {
        applyReporterServiceSettings(
            reporterServiceConnector = reporterServiceConnector,
            settingsProvider = settingsProvider,
            bortEnabledProvider = bortEnabledProvider,
        )

        dumpsterClient.setStructuredLogEnabled(settingsProvider.structuredLogSettings.dataSourceEnabled)

        Logger.test("logcat.collection_mode=${settingsProvider.logcatSettings.collectionMode}")
        continuousLoggingController.configureContinuousLogging()

        // Update periodic tasks that might have changed after a settings update
        periodicWorkManager.maybeRestartTasksAfterSettingsChange(fetchedSettingsUpdate)
        dropBoxTagEnabler.enableTagsIfRequired()
        dropBoxEntryAddedReceiver.initialize()

        with(settingsProvider) {
            Logger.initSettings(asLoggerSettings())
            Logger.i("settings.updated", selectSettingsToMap())
        }

        // Notify OTA app that settings changed (if it is installed).
        application.sendBroadcast(
            Intent(INTENT_ACTION_OTA_SETTINGS_CHANGED).apply {
                component = ComponentName.createRelative(BuildConfig.BORT_OTA_APPLICATION_ID, OTA_RECEIVER_CLASS)
            },
        )
    }
}

fun reloadCustomEventConfigFrom(settings: StructuredLogSettings) {
    CustomEvent.reloadConfig(
        BortJson.encodeToString(
            StructuredLogDaemonSettings.serializer(),
            settings.toStructuredLogDaemonSettings(),
        ),
    )
}

suspend fun applyReporterServiceSettings(
    reporterServiceConnector: ReporterServiceConnector,
    settingsProvider: SettingsProvider,
    bortEnabledProvider: BortEnabledProvider,
) {
    try {
        reporterServiceConnector.connect { getConnection ->
            val connection = getConnection()
            val isBortEnabled = bortEnabledProvider.isEnabled()

            connection.setLogLevel(settingsProvider.minLogcatLevel).onFailure {
                Logger.w("could not send log level to reporter service", it)
            }
            // Now that Bort collects temperature metrics, reporter does not. Note: temperature is the only metric
            // supported by reporter; if we ever add new ones, add a new message for configuring them. This is left here
            // in case an older version of UsageReporter is installed, so its metric collection will be disabled.
            val metricCollectionPeriod = ZERO
            connection.setMetricsCollectionInterval(metricCollectionPeriod).onFailure {
                Logger.w("could not send metric collection interval to reporter service", it)
            }
            connection.setReporterSettings(
                SetReporterSettingsRequest(
                    maxFileTransferStorageBytes =
                    settingsProvider.storageSettings.maxClientServerFileTransferStorageBytes,
                    maxFileTransferStorageAge =
                    settingsProvider.storageSettings.maxClientServerFileTransferStorageAge.boxed(),
                    maxReporterTempStorageBytes = settingsProvider.storageSettings.usageReporterTempMaxStorageBytes,
                    maxReporterTempStorageAge = settingsProvider.storageSettings.usageReporterTempMaxStorageAge.boxed(),
                    bortEnabled = isBortEnabled,
                ),
            ).onFailure {
                Logger.w("could not send settings to reporter service", it)
            }
        }
    } catch (e: RemoteException) {
        // This happens if UsageReporter is so old that it does not contain the ReporterService at all:
        Logger.w("Unable to connect to ReporterService to set log level", e)
    }
}

private fun StructuredLogSettings.toStructuredLogDaemonSettings(): StructuredLogDaemonSettings =
    StructuredLogDaemonSettings(
        structuredLogDataSourceEnabled = dataSourceEnabled,
        structuredLogDumpPeriod = dumpPeriod.boxed(),
        structuredLogMaxMessageSizeBytes = maxMessageSizeBytes,
        structuredLogMinStorageThresholdBytes = minStorageThresholdBytes,
        structuredLogNumEventsBeforeDump = numEventsBeforeDump,
        structuredLogRateLimitingSettings = rateLimitingSettings,
        structuredLogMetricReportEnabled = metricsReportEnabled,
        structuredLogHighResMetricsEnabled = highResMetricsEnabled,
    )
