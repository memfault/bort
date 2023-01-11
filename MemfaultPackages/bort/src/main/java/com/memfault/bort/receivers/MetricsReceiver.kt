package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.shared.InternalMetric
import com.memfault.bort.shared.InternalMetric.Companion.INTENT_ACTION_INTERNAL_METRIC
import com.memfault.bort.shared.Logger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MetricsReceiver : BortEnabledFilteringReceiver(
    setOf(INTENT_ACTION_INTERNAL_METRIC)
) {
    @Inject lateinit var metrics: BuiltinMetricsStore

    override fun onReceivedAndEnabled(context: Context, intent: Intent, action: String) {
        val metric = InternalMetric.fromIntent(intent) ?: return
        Logger.test("internal metric: ${metric.key}")
        metric.value?.let { metrics.addValue(metric.key, it.toDouble()) }
            ?: metrics.increment(metric.key)
    }
}
