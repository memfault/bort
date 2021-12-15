package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.preference.PreferenceManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.memfault.bort.TestOverrideSettings
import com.memfault.bort.clientserver.MarUploadTask.Companion.enqueueOneTimeMarUpload
import com.memfault.bort.logcat.RealNextLogcatCidProvider
import com.memfault.bort.metrics.DevicePropertiesDb
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.requester.restartPeriodicLogcatCollection
import com.memfault.bort.requester.restartPeriodicMetricsCollection
import com.memfault.bort.selfTesting.SelfTestWorker
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.settings.StoredSettingsPreferenceProvider
import com.memfault.bort.settings.restartPeriodicSettingsUpdate
import com.memfault.bort.shared.JitterDelayProvider
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BootRelativeTime
import com.memfault.bort.time.BootRelativeTimeProvider
import com.memfault.bort.time.BoxedDuration
import com.memfault.bort.tokenbucket.TokenBucketStoreRegistry
import com.memfault.bort.uploader.FileUploadHoldingArea
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.days
import kotlin.time.hours
import kotlin.time.milliseconds
import kotlinx.coroutines.runBlocking

private const val INTENT_EXTRA_ECHO_STRING = "echo"
private const val WORK_UNIQUE_NAME_SELF_TEST = "com.memfault.bort.work.SELF_TEST"

@AndroidEntryPoint
class TestReceiver : FilteringReceiver(
    setOf(
        "com.memfault.intent.action.TEST_BORT_ECHO",
        "com.memfault.intent.action.TEST_SETTING_SET_PROJECT_KEY",
        "com.memfault.intent.action.TEST_SETTING_SET_USE_MAR",
        "com.memfault.intent.action.TEST_SELF_TEST",
        "com.memfault.intent.action.TEST_REQUEST_LOGCAT_COLLECTION",
        "com.memfault.intent.action.TEST_REQUEST_METRICS_COLLECTION",
        "com.memfault.intent.action.TEST_REQUEST_SETTINGS_UPDATE",
        "com.memfault.intent.action.TEST_RESET_DYNAMIC_SETTINGS",
        "com.memfault.intent.action.TEST_RESET_RATE_LIMITS",
        "com.memfault.intent.action.TEST_SETUP",
        "com.memfault.intent.action.TEST_UPLOAD_MAR",
    )
) {
    @Inject lateinit var settingsProvider: SettingsProvider
    @Inject lateinit var fileUploadHoldingArea: FileUploadHoldingArea
    @Inject lateinit var jitterDelayProvider: JitterDelayProvider
    @Inject lateinit var devicePropertiesDb: DevicePropertiesDb
    @Inject lateinit var storedSettingsPreferenceProvider: StoredSettingsPreferenceProvider
    @Inject lateinit var tokenBucketStoreRegistry: TokenBucketStoreRegistry
    @Inject lateinit var bootRelativeTimeProvider: BootRelativeTimeProvider

    override fun onIntentReceived(context: Context, intent: Intent, action: String) {
        when (action) {
            "com.memfault.intent.action.TEST_SETTING_SET_PROJECT_KEY" -> {
                val projectKey = intent.getStringExtra(
                    "project_key"
                ) ?: return
                Logger.test("project_key: $projectKey")
                TestOverrideSettings(
                    PreferenceManager.getDefaultSharedPreferences(context)
                ).also {
                    Logger.test("Key was: ${it.projectKeyProvider.getValue()}")
                    it.projectKeyProvider.setValue(projectKey)
                    Logger.test("Updated to key: ${it.projectKeyProvider.getValue()}")
                }
            }
            "com.memfault.intent.action.TEST_SETTING_SET_USE_MAR" -> {
                val useMar = intent.getBooleanExtra(
                    "project_key", false
                )
                Logger.test("use_mar_upload: $useMar")
                TestOverrideSettings(
                    PreferenceManager.getDefaultSharedPreferences(context)
                ).also {
                    Logger.test("use_mar_upload was: ${it.useMarUpload.getValue()}")
                    it.useMarUpload.setValue(useMar)
                    Logger.test("Updated to use_mar_upload: ${it.useMarUpload.getValue()}")
                }
            }
            "com.memfault.intent.action.TEST_BORT_ECHO" -> {
                Logger.test("bort echo ${intent.getStringExtra(INTENT_EXTRA_ECHO_STRING)}")
            }
            "com.memfault.intent.action.TEST_SELF_TEST" -> {
                OneTimeWorkRequestBuilder<SelfTestWorker>().build().also {
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        WORK_UNIQUE_NAME_SELF_TEST, ExistingWorkPolicy.REPLACE, it
                    )
                }
            }
            "com.memfault.intent.action.TEST_REQUEST_LOGCAT_COLLECTION" -> {
                // Pretend an event of interest occurred, so that the logcat file gets uploaded immediately:
                fileUploadHoldingArea.handleEventOfInterest(SystemClock.elapsedRealtime().milliseconds)

                restartPeriodicLogcatCollection(
                    context = context,
                    // Something long to ensure it does not re-run & interfere with tests:
                    collectionInterval = 1.days,
                    // Pretend the logcat started an hour ago:
                    lastLogcatEnd = AbsoluteTime.now() - 1.hours,
                    collectImmediately = true,
                )
                PreferenceManager.getDefaultSharedPreferences(context).let {
                    RealNextLogcatCidProvider(it).cid
                }.also {
                    Logger.test("cid=${it.uuid}")
                }
            }
            "com.memfault.intent.action.TEST_REQUEST_SETTINGS_UPDATE" -> {
                restartPeriodicSettingsUpdate(
                    context = context,
                    // Something long to ensure it does not re-run & interfere with tests:
                    updateInterval = 4.days,
                    httpApiSettings = settingsProvider.httpApiSettings,
                    delayAfterSettingsUpdate = false,
                    testRequest = true,
                    jitterDelayProvider = jitterDelayProvider,
                )
            }
            "com.memfault.intent.action.TEST_REQUEST_METRICS_COLLECTION" -> {
                // Drop stored properties so that consecutive e2e tests get the full set of properties
                runBlocking {
                    devicePropertiesDb.deviceProperty()
                        .deleteAll()
                }

                restartPeriodicMetricsCollection(
                    context = context,
                    // Something long to ensure it does not re-run & interfere with tests:
                    collectionInterval = 1.days,
                    // Pretend the heartbeat started an hour ago:
                    lastHeartbeatEnd = bootRelativeTimeProvider.now() - 1.hours,
                    collectImmediately = true,
                )
                Reporting.report()
                    .counter("report_test_metric")
                    .increment()
                Reporting.report()
                    .counter("report_test_metric_internal", internal = true)
                    .increment()
            }
            "com.memfault.intent.action.TEST_RESET_RATE_LIMITS" -> {
                resetRateLimits()
            }
            "com.memfault.intent.action.TEST_RESET_DYNAMIC_SETTINGS" -> {
                resetDynamicSettings()
            }
            // Sent before each E2E test:
            "com.memfault.intent.action.TEST_SETUP" -> {
                resetDynamicSettings()
                resetRateLimits()
            }
            "com.memfault.intent.action.TEST_UPLOAD_MAR" -> enqueueOneTimeMarUpload(
                context = context,
                constraints = settingsProvider.httpApiSettings.uploadConstraints,
            )
        }
    }

    private fun resetDynamicSettings() {
        // Reset to built-in settings values:
        storedSettingsPreferenceProvider.reset()
        settingsProvider.invalidate()
    }

    private fun resetRateLimits() {
        tokenBucketStoreRegistry.reset()
    }
}

private operator fun BootRelativeTime.minus(duration: Duration): BootRelativeTime =
    this.copy(
        // Note: this is incorrect, but for the purpose of the test it does not matter:
        uptime = BoxedDuration(this.uptime.duration - duration),
        elapsedRealtime = BoxedDuration(this.elapsedRealtime.duration - duration),
    )
