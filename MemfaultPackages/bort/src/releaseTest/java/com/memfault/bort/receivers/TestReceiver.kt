package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import androidx.preference.PreferenceManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.memfault.bort.Bort
import com.memfault.bort.LastTrackedBootCountProvider
import com.memfault.bort.PersistentProjectKeyProvider
import com.memfault.bort.logcat.RealNextLogcatCidProvider
import com.memfault.bort.requester.restartPeriodicLogcatCollection
import com.memfault.bort.requester.restartPeriodicMetricsCollection
import com.memfault.bort.selfTesting.SelfTestWorker
import com.memfault.bort.settings.restartPeriodicSettingsUpdate
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BootRelativeTime
import com.memfault.bort.time.BoxedDuration
import com.memfault.bort.time.RealBootRelativeTimeProvider
import kotlin.time.Duration
import kotlin.time.days
import kotlin.time.hours
import kotlin.time.milliseconds

private const val INTENT_EXTRA_ECHO_STRING = "echo"
private const val WORK_UNIQUE_NAME_SELF_TEST = "com.memfault.bort.work.SELF_TEST"

class TestLastTrackedBootCountProvider(
    sharedPreferences: SharedPreferences
) : LastTrackedBootCountProvider, PreferenceKeyProvider<Int>(
    sharedPreferences = sharedPreferences,
    defaultValue = 0,
    preferenceKey = "com.memfault.preference.TEST_LAST_TRACKED_BOOT_COUNT"
) {
    override var bootCount
        get() = super.getValue()
        set(value) = super.setValue(value)
}

class TestReceiver : FilteringReceiver(
    setOf(
        "android.intent.action.BOOT_COMPLETED",
        "com.memfault.intent.action.TEST_QUERY_BOOT_COMPLETED",
        "com.memfault.intent.action.TEST_BORT_ECHO",
        "com.memfault.intent.action.TEST_SETTING_SET",
        "com.memfault.intent.action.TEST_SELF_TEST",
        "com.memfault.intent.action.TEST_REQUEST_LOGCAT_COLLECTION",
        "com.memfault.intent.action.TEST_REQUEST_METRICS_COLLECTION",
        "com.memfault.intent.action.TEST_REQUEST_SETTINGS_UPDATE",
        "com.memfault.intent.action.TEST_RESET_DYNAMIC_SETTINGS",
        "com.memfault.intent.action.TEST_RESET_RATE_LIMITS",
        "com.memfault.intent.action.TEST_SETUP",
    )
) {
    override fun onIntentReceived(context: Context, intent: Intent, action: String) {
        when (action) {
            "com.memfault.intent.action.TEST_SETTING_SET" -> {
                val projectKey = intent.getStringExtra(
                    "project_key"
                ) ?: return
                Logger.test("project_key: $projectKey")
                PersistentProjectKeyProvider(
                    PreferenceManager.getDefaultSharedPreferences(context)
                ).also {
                    Logger.test("Key was: ${it.getValue()}")
                    it.setValue(projectKey)
                    Logger.test("Updated to key: ${it.getValue()}")
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
                    delayInitially = false,
                    testRequest = true,
                )
            }
            "com.memfault.intent.action.TEST_REQUEST_METRICS_COLLECTION" -> {
                restartPeriodicMetricsCollection(
                    context = context,
                    // Something long to ensure it does not re-run & interfere with tests:
                    collectionInterval = 1.days,
                    // Pretend the heartbeat started an hour ago:
                    lastHeartbeatEnd = RealBootRelativeTimeProvider(context).now() - 1.hours,
                    collectImmediately = true,
                )
            }
            "android.intent.action.BOOT_COMPLETED" -> {
                TestLastTrackedBootCountProvider(
                    PreferenceManager.getDefaultSharedPreferences(context)
                ).bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
            }
            "com.memfault.intent.action.TEST_QUERY_BOOT_COMPLETED" -> {
                val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
                val lastTrackedBootCount = TestLastTrackedBootCountProvider(
                    PreferenceManager.getDefaultSharedPreferences(context)
                ).bootCount
                Logger.test("BOOT_COMPLETED handled: ${bootCount == lastTrackedBootCount}")
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
        }
    }

    private fun resetDynamicSettings() {
        // Reset to built-in settings values:
        with(Bort.appComponents()) {
            storedSettingsPreferenceProvider.reset()
            settingsProvider.invalidate()
        }
    }

    private fun resetRateLimits() {
        Bort.appComponents().tokenBucketStoreRegistry.reset()
    }
}

private operator fun BootRelativeTime.minus(duration: Duration): BootRelativeTime =
    this.copy(
        // Note: this is incorrect, but for the purpose of the test it does not matter:
        uptime = BoxedDuration(this.uptime.duration - duration),
        elapsedRealtime = BoxedDuration(this.elapsedRealtime.duration - duration),
    )
