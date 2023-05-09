package com.memfault.usagereporter.selftest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ReporterTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.memfault.intent.action.TEST_REPORTER_SELF_TEST" -> {
                val request = OneTimeWorkRequestBuilder<ReporterSelfTesterWorker>().build()
                ReporterSelfTesterWorker.schedule(context, request)
            }
        }
    }
}
