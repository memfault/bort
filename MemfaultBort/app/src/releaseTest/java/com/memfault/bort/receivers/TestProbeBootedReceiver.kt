package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.*

class TestProbeBootedReceiver : SingleActionBroadcastReceiver(
    "com.memfault.intent.action.TEST_PROBE_BOOTED"
) {
    override fun onIntentReceived(context: Context, intent: Intent) {
        Logger.test("bort booted")
    }
}
