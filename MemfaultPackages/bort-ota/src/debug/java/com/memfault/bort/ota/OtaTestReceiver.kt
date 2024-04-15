package com.memfault.bort.ota

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memfault.bort.ota.lib.TestOtaModePreferenceProvider
import com.memfault.bort.ota.lib.TestUpdateEngine
import com.memfault.bort.shared.Logger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val INTENT_EXTRA_ECHO_STRING = "echo"
private const val INTENT_EXTRA_MODE = "mode"

/**
 * A broadcast receiver that listens to test events.
 */
@AndroidEntryPoint
class OtaTestReceiver : BroadcastReceiver() {
    @Inject lateinit var testOtaModePreferenceProvider: TestOtaModePreferenceProvider

    @Inject lateinit var testUpdateEngine: TestUpdateEngine

    override fun onReceive(context: Context, intent: Intent) {
        Logger.test("TestReceiver action=${intent.action}")
        when (intent.action) {
            "com.memfault.intent.action.TEST_BORT_OTA_MODE" -> {
                val modeExtra = intent.getStringExtra(INTENT_EXTRA_MODE) ?: return
                testOtaModePreferenceProvider.setValue(modeExtra)
                Logger.test("bort-ota updater mode set to $modeExtra")
            }

            "com.memfault.intent.action.TEST_BORT_OTA_ECHO" -> {
                Logger.test("bort-ota echo ${intent.getStringExtra(INTENT_EXTRA_ECHO_STRING)}")
            }

            "com.memfault.intent.action.TEST_BORT_OTA_NEED_REBOOT" -> {
                testUpdateEngine.needsReboot()
            }

            else -> {
                Logger.test("bort-ota unhandled action ${intent.action}")
            }
        }
    }
}
