package com.memfault.usagereporter.metrics

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import com.memfault.bort.shared.Logger
import kotlin.time.Duration

/**
 * Manages regular metric collection.
 */
class ReporterMetrics(
    private val reporterMetricsPreferenceProvider: ReporterMetricsPreferenceProvider,
    private val handler: Handler,
    private val collectors: Set<MetricCollector>,
) {
    private var collectionInterval = reporterMetricsPreferenceProvider.getDurationValue()

    fun init() {
        if (collectionInterval.isPositive()) startCollection()
    }

    fun setCollectionInterval(interval: Duration) {
        Logger.d("setCollectionInterval: $interval")
        collectionInterval = interval
        reporterMetricsPreferenceProvider.setValue(interval)
        stopCollection()
        if (interval.isPositive()) startCollection()
    }

    private fun startCollection() {
        handler.post(::collectMetrics)
    }

    private fun stopCollection() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun collectMetrics() {
        collectors.forEach { it.collect() }
        collectionInterval.let { interval ->
            if (!interval.isPositive()) return
            handler.postDelayed(::collectMetrics, interval.inWholeMilliseconds)
        }
    }

    companion object {
        fun create(context: Context) = ReporterMetrics(
            reporterMetricsPreferenceProvider =
                ReporterMetricsPreferenceProvider(PreferenceManager.getDefaultSharedPreferences(context)),
            handler = Handler(Looper.getMainLooper()),
            collectors = setOf(
                TemperatureMetricCollector(context),
            ),
        ).also { it.init() }
    }
}

interface MetricCollector {
    fun collect()
}
