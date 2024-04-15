package com.memfault.bort.storage

import android.app.usage.StorageStats
import android.app.usage.StorageStatsManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
import android.os.storage.StorageManager
import com.memfault.bort.IO
import com.memfault.bort.metrics.HighResTelemetry
import com.memfault.bort.metrics.HighResTelemetry.DataType
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.MetricType
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import com.memfault.bort.metrics.SignificantAppsProvider
import com.memfault.bort.settings.StorageSettings
import com.memfault.bort.time.CombinedTime
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

data class AppStorageStatsResult(
    val heartbeatMetrics: Map<String, JsonPrimitive>,
    val internalHeartbeatMetrics: Map<String, JsonPrimitive>,
    val hrtRollup: Set<HighResTelemetry.Rollup>,
) {
    companion object {
        val EMPTY = AppStorageStatsResult(
            heartbeatMetrics = emptyMap(),
            internalHeartbeatMetrics = emptyMap(),
            hrtRollup = emptySet(),
        )
    }
}

interface AppStorageStatsCollector {
    suspend fun collect(collectionTime: CombinedTime): AppStorageStatsResult
}

@ContributesBinding(SingletonComponent::class)
class RealAppStorageStatsCollector
@Inject constructor(
    private val storageSettings: StorageSettings,
    private val significantAppsProvider: SignificantAppsProvider,
    private val storageStatsManager: StorageStatsManager,
    @IO private val ioCoroutineContext: CoroutineContext,
) : AppStorageStatsCollector {

    override suspend fun collect(collectionTime: CombinedTime): AppStorageStatsResult {
        if (!storageSettings.appsSizeDataSourceEnabled) return AppStorageStatsResult.EMPTY

        val heartbeatMetrics = mutableMapOf<String, JsonPrimitive>()
        val internalHeartbeatMetrics = mutableMapOf<String, JsonPrimitive>()
        val hrtRollup = mutableSetOf<HighResTelemetry.Rollup>()

        fun recordStorageStats(
            key: String,
            storageStats: StorageStats?,
            internal: Boolean,
        ) {
            if (storageStats == null) return

            fun record(
                key: String,
                value: Long?,
            ) {
                if (value == null) return

                val jsonValue = JsonPrimitive(value)
                if (internal) {
                    internalHeartbeatMetrics[key] = jsonValue
                } else {
                    heartbeatMetrics[key] = jsonValue
                }

                val rollup = HighResTelemetry.Rollup(
                    metadata = RollupMetadata(
                        stringKey = key,
                        metricType = MetricType.Gauge,
                        dataType = DataType.DoubleType,
                        internal = internal,
                    ),
                    data = listOf(
                        Datum(t = collectionTime.timestamp.toEpochMilli(), value = jsonValue),
                    ),
                )
                hrtRollup.add(rollup)
            }

            record("storage.app.app_$key", storageStats.appBytes)
            record("storage.app.cache_$key", storageStats.cacheBytes)
            record("storage.app.data_$key", storageStats.dataBytes)
            if (Build.VERSION.SDK_INT >= 31) {
                record("storage.app.extcache_$key", storageStats.externalCacheBytes)
            }
        }

        significantAppsProvider.apps()
            .forEach { app ->
                recordStorageStats(
                    key = app.identifier,
                    storageStats = queryForPackage(app.packageName),
                    internal = app.internal,
                )
            }

        return AppStorageStatsResult(
            heartbeatMetrics = heartbeatMetrics,
            internalHeartbeatMetrics = internalHeartbeatMetrics,
            hrtRollup = hrtRollup,
        )
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
