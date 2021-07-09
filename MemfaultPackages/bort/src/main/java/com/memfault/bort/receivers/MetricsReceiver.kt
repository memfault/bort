package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.metrics.metrics
import com.memfault.bort.shared.InternalMetric
import com.memfault.bort.shared.InternalMetric.Companion.INTENT_ACTION_INTERNAL_METRIC
import com.memfault.bort.shared.Logger

class MetricsReceiver : BortEnabledFilteringReceiver(
    setOf(INTENT_ACTION_INTERNAL_METRIC)
) {
    override fun onReceivedAndEnabled(context: Context, intent: Intent, action: String) {
        val metric = InternalMetric.fromIntent(intent) ?: return
        Logger.test("internal metric: ${metric.key}")
        metric.value?.let { metrics()?.addValue(metric.key, it, metric.synchronous) }
            ?: metrics()?.increment(metric.key, metric.synchronous)
    }
}
