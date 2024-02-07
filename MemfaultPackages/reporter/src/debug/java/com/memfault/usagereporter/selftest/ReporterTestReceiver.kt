package com.memfault.usagereporter.selftest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import com.memfault.bort.connectivity.ConnectivityMetrics
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.goAsync
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class ReporterTestReceiver : BroadcastReceiver() {

    @Inject lateinit var connectivityReceiver: ConnectivityMetrics

    override fun onReceive(context: Context, intent: Intent) {
        Logger.v("Received action=${intent.action}")
        when (intent.action) {
            "com.memfault.intent.action.TEST_REPORTER_SELF_TEST" -> {
                val request = OneTimeWorkRequestBuilder<ReporterSelfTesterWorker>().build()
                ReporterSelfTesterWorker.schedule(context, request)
            }
            "com.memfault.intent.action.TEST_REPORTER_REPLAY_CONNECTIVITY" -> goAsync {
                connectivityReceiver.replayLatest()
                delay(2.seconds.inWholeMilliseconds)
            }
        }
    }
}
