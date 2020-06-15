package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.*

private const val INTENT_EXTRA_ECHO_STRING = "echo"

class TestBortEchoReceiver : SingleActionReceiver(
    "com.memfault.intent.action.TEST_BORT_ECHO"
) {
    override fun onIntentReceived(context: Context, intent: Intent) {
        Logger.test("bort echo ${intent.getStringExtra(INTENT_EXTRA_ECHO_STRING)}")
    }
}
