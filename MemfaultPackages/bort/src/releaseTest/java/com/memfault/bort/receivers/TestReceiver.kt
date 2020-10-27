package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import androidx.preference.PreferenceManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.memfault.bort.LastTrackedBootCountProvider
import com.memfault.bort.PersistentProjectKeyProvider
import com.memfault.bort.selfTesting.SelfTestWorker
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider

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
        "com.memfault.intent.action.TEST_SELF_TEST"
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
        }
    }
}
