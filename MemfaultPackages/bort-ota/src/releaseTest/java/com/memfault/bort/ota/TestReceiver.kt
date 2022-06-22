package com.memfault.bort.ota

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memfault.bort.ota.lib.Action
import com.memfault.bort.ota.lib.State
import com.memfault.bort.ota.lib.updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val INTENT_EXTRA_ECHO_STRING = "echo"
private const val INTENT_EXTRA_BACKGROUND = "background"
private const val INTENT_EXTRA_MODE = "mode"

/**
 * A broadcast receiver that listens to test events.
 */
class TestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        testLog("TestReceiver action=${intent.action}")
        when (intent.action) {
            "com.memfault.intent.action.TEST_BORT_OTA_MODE" -> {
                val modeExtra = intent.getStringExtra(INTENT_EXTRA_MODE)
                val mode = when (modeExtra) {
                    "ab" -> TestApp.OtaTestMode.AB
                    "recovery" -> TestApp.OtaTestMode.Recovery
                    else -> throw IllegalArgumentException("Invalid test mode")
                }
                (context.applicationContext as TestApp).changeMode(mode)
                testLog("bort-ota updater mode set to $modeExtra")
            }
            "com.memfault.intent.action.TEST_BORT_OTA_ECHO" -> {
                testLog("bort-ota echo ${intent.getStringExtra(INTENT_EXTRA_ECHO_STRING)}")
            }
            "com.memfault.intent.action.TEST_BORT_OTA_CHECK_FOR_UPDATE" -> {
                val background = intent.getBooleanExtra(INTENT_EXTRA_BACKGROUND, false)
                CoroutineScope(Dispatchers.Default).launch {
                    with(context.applicationContext.updater()) {
                        setState(State.Idle)
                        perform(Action.CheckForUpdate(background = background))
                    }
                }
            }
            else -> {
                testLog("bort-ota unhandled action ${intent.action}")
            }
        }
    }
}
