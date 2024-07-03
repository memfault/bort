package com.memfault.bort.storage

import android.app.usage.StorageStats
import android.app.usage.StorageStatsManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
import android.os.storage.StorageManager
import com.memfault.bort.IO
import com.memfault.bort.metrics.InMemoryMetric
import com.memfault.bort.metrics.InMemoryMetricCollector
import com.memfault.bort.metrics.SignificantAppsProvider
import com.memfault.bort.reporting.MetricType.GAUGE
import com.memfault.bort.settings.StorageSettings
import com.memfault.bort.time.CombinedTime
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

interface AppStorageStatsCollector : InMemoryMetricCollector

@ContributesBinding(SingletonComponent::class)
class RealAppStorageStatsCollector
@Inject constructor(
    private val storageSettings: StorageSettings,
    private val significantAppsProvider: SignificantAppsProvider,
    private val storageStatsManager: StorageStatsManager,
    @IO private val ioCoroutineContext: CoroutineContext,
) : AppStorageStatsCollector {

    override suspend fun collect(
        collectionTime: CombinedTime,
        heartbeatInterval: Duration,
    ): List<InMemoryMetric> {
        if (!storageSettings.appsSizeDataSourceEnabled) return emptyList()

        fun storageStatsMetrics(
            key: String,
            storageStats: StorageStats?,
            internal: Boolean,
        ): List<InMemoryMetric> {
            if (storageStats == null) {
                return emptyList()
            }

            fun metric(
                key: String,
                value: Long,
            ): InMemoryMetric = InMemoryMetric(
                metricName = key,
                metricValue = JsonPrimitive(value),
                metricType = GAUGE,
                internal = internal,
            )

            return listOfNotNull(
                metric("storage.app.app_$key", storageStats.appBytes),
                metric("storage.app.cache_$key", storageStats.cacheBytes),
                metric("storage.app.data_$key", storageStats.dataBytes),
                if (Build.VERSION.SDK_INT >= 31) {
                    metric("storage.app.extcache_$key", storageStats.externalCacheBytes)
                } else {
                    null
                },
            )
        }

        return significantAppsProvider.apps()
            .flatMap { app ->
                storageStatsMetrics(
                    key = app.identifier,
                    storageStats = queryForPackage(app.packageName),
                    internal = app.internal,
                )
            }
    }

    private suspend fun queryForPackage(packageName: String?): StorageStats? = withContext(ioCoroutineContext) {
        packageName?.let {
            try {
                storageStatsManager.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    it,
                    android.os.Process.myUserHandle(),
                )
            } catch (e: NameNotFoundException) {
                null
            } catch (e: IOException) {
                null
            }
        }
    }
}
