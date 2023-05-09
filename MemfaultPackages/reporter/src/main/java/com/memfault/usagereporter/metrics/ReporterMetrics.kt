package com.memfault.usagereporter.metrics

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.memfault.bort.shared.Logger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/**
 * Manages regular metric collection.
 */
@Singleton
class ReporterMetrics @Inject constructor(
    application: Application,
    private val reporterMetricsPreferenceProvider: ReporterMetricsPreferenceProvider,
) {

    private val handler: Handler = Handler(Looper.getMainLooper())

    private val collectors: Set<MetricCollector> = setOf(
        TemperatureMetricCollector(application),
    )

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
}

interface MetricCollector {
    fun collect()
}
