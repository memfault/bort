package com.memfault.usagereporter.metrics

import android.app.Application
import android.os.Handler
import android.os.HandlerThread
import com.memfault.bort.scopes.Scope
import com.memfault.bort.scopes.Scoped
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/**
 * Manages regular metric collection.
 */
@ContributesMultibinding(SingletonComponent::class)
@Singleton
class ReporterMetrics @Inject constructor(
    application: Application,
    private val reporterMetricsPreferenceProvider: ReporterMetricsPreferenceProvider,
) : Scoped {
    private val handlerThread = HandlerThread("metrics").also { it.start() }
    private val handler: Handler = Handler(handlerThread.looper)

    private val collectors: Set<MetricCollector> = setOf(
        TemperatureMetricCollector(application),
    )

    private var collectionInterval = reporterMetricsPreferenceProvider.getDurationValue()

    override fun onEnterScope(scope: Scope) {
        if (collectionInterval.isPositive()) startCollection()
    }

    override fun onExitScope() {
        stopCollection()
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
