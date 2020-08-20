package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import com.memfault.bort.*
import kotlinx.coroutines.runBlocking

private const val INTENT_EXTRA_ECHO_STRING = "echo"

class TestReceiver : FilteringReceiver(
    setOf(
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
                runBlocking {
                    val output = DumpsterClient().getprop()
                    val result = if (output?.containsKey("ro.boot.serialno") == true) "success" else "failed"
                    Logger.test("Bort self test: $result")
                }
            }
        }
    }
}
