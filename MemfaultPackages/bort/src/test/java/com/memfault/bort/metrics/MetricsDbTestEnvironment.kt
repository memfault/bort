package com.memfault.bort.metrics

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.memfault.bort.DeviceInfo
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.battery.BatterySessionVitalsCalculator
import com.memfault.bort.connectivity.ConnectivityTimeCalculator
import com.memfault.bort.metrics.CrashFreeHoursMetricLogger.Companion.OPERATIONAL_CRASHES_METRIC_KEY
import com.memfault.bort.metrics.DropBoxTraceCountDerivedAggregations.Companion.DROP_BOX_TAGS
import com.memfault.bort.metrics.HighResTelemetry.DataType.DoubleType
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.MetricType.Counter
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import com.memfault.bort.metrics.custom.CustomMetrics
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.metrics.custom.RealCustomMetrics
import com.memfault.bort.metrics.database.MetricsDb
import com.memfault.bort.reporting.FinishReport
import com.memfault.bort.reporting.MetricValue
import com.memfault.bort.reporting.RemoteMetricsService
import com.memfault.bort.reporting.StartReport
import com.memfault.bort.settings.DailyHeartbeatEnabled
import com.memfault.bort.settings.HighResMetricsEnabled
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.settings.RateLimitingSettings
import com.memfault.bort.test.util.TemporaryFolderTemporaryFileFactory
import com.memfault.bort.tokenbucket.TokenBucketStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.rules.ExternalResource
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration

class MetricsDbTestEnvironment : ExternalResource() {

    lateinit var db: MetricsDb
    lateinit var dao: CustomMetrics

    private val temporaryFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    var dailyHeartbeatEnabledValue: Boolean = false
    private val dailyHeartbeatEnabled = DailyHeartbeatEnabled { dailyHeartbeatEnabledValue }

    private val tokenBucketStore = mockk<TokenBucketStore> {
        val rateLimited = false
        every { takeSimple(any(), any(), any()) } answers { !rateLimited }
    }

    var deviceInfo = DeviceInfo(
        deviceSerial = "device-serial",
        hardwareVersion = "hardware-version",
        softwareVersion = "software-version",
    )
    private val deviceInfoProvider = object : DeviceInfoProvider {
        override suspend fun getDeviceInfo(): DeviceInfo = deviceInfo
    }

    var highResMetricsEnabledValue = false
    var thermalCollectLegacyMetricsValue = false
    private val highResMetricsEnabled = HighResMetricsEnabled { highResMetricsEnabledValue }
    private val metricsSettings = object : MetricsSettings {
        override val dataSourceEnabled: Boolean get() = TODO("Not used")
        override val dailyHeartbeatEnabled: Boolean get() = TODO("Not used")
        override val sessionsRateLimitingSettings: RateLimitingSettings get() = TODO("Not used")
        override val collectionInterval: Duration get() = TODO("Not used")
        override val systemProperties: List<String> get() = TODO("Not used")
        override val appVersions: List<String> get() = TODO("Not used")
        override val maxNumAppVersions: Int get() = TODO("Not used")
        override val reporterCollectionInterval: Duration get() = TODO("Not used")
        override val cachePackageManagerReport: Boolean get() = TODO("Not used")
        override val recordImei: Boolean get() = TODO("Not used")
        override val operationalCrashesExclusions: List<String> get() = TODO("Not used")
        override val operationalCrashesComponentGroups: JsonObject get() = TODO("Not used")
        override val pollingInterval: Duration get() = TODO("Not used")
        override val collectMemory: Boolean get() = TODO("Not used")
        override val thermalMetricsEnabled: Boolean get() = TODO("Not used")
        override val thermalCollectLegacyMetrics: Boolean get() = thermalCollectLegacyMetricsValue
        override val thermalCollectStatus: Boolean get() = TODO("Not used")
    }

    /**
     * By default, exclude any metrics which get added to all reports (e.g. operational_crashes), so that we don't need
     * to paste them everywhere in our unit tests. Include them by changing this setting for a specific test.
     */
    var excludeEverPresentMetrics = true

    inner class TestCustomMetrics(
        private val customMetrics: CustomMetrics,
    ) : CustomMetrics by customMetrics {
        override suspend fun collectHeartbeat(
            endTimestampMs: Long,
            forceEndAllReports: Boolean,
        ): CustomReport {
            val report = customMetrics.collectHeartbeat(
                endTimestampMs = endTimestampMs,
                forceEndAllReports = forceEndAllReports,
            )
            if (!excludeEverPresentMetrics) {
                return report
            } else {
                return report.copy(
                    hourlyHeartbeatReport = report.hourlyHeartbeatReport.copy(
                        metrics = report.hourlyHeartbeatReport.metrics
                            .minus(setOf(OPERATIONAL_CRASHES_METRIC_KEY) + DROP_BOX_TAGS),
                    ),
                    dailyHeartbeatReport = report.dailyHeartbeatReport?.copy(
                        metrics = report.dailyHeartbeatReport?.metrics
                            ?.minus(setOf(OPERATIONAL_CRASHES_METRIC_KEY) + DROP_BOX_TAGS)
                            ?: emptyMap(),
                    ),
                    sessions = report.sessions.map {
                        it.copy(
                            metrics = it.metrics.minus(OPERATIONAL_CRASHES_METRIC_KEY),
                        )
                    },
                )
            }
        }
    }

    override fun before() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MetricsDb::class.java)
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()
        temporaryFolder.create()

        val customMetrics = RealCustomMetrics(
            db = db,
            temporaryFileFactory = TemporaryFolderTemporaryFileFactory(temporaryFolder),
            dailyHeartbeatEnabled = dailyHeartbeatEnabled,
            highResMetricsEnabled = highResMetricsEnabled,
            sessionMetricsTokenBucketStore = tokenBucketStore,
            deviceInfoProvider = deviceInfoProvider,
            derivedAggregations = setOf(
                BatterySessionVitalsCalculator(),
                ConnectivityTimeCalculator(),
                ThermalDerivedCalculator(metricsSettings),
                DropBoxTraceCountDerivedAggregations(),
            ),
        )
        dao = TestCustomMetrics(customMetrics)

        val contentResolver = mockk<ContentResolver> {
            every { insert(any(), any()) } answers {
                runBlocking {
                    val uri = it.invocation.args[0] as Uri
                    val values = it.invocation.args[1] as ContentValues
                    when (uri) {
                        RemoteMetricsService.URI_ADD_CUSTOM_METRIC -> {
                            val metricJson = values.getAsString(RemoteMetricsService.KEY_CUSTOM_METRIC)
                            val metricValue = MetricValue.fromJson(metricJson)
                            dao.add(metricValue)
                            uri
                        }

                        RemoteMetricsService.URI_START_CUSTOM_REPORT -> {
                            val startJson = values.getAsString(RemoteMetricsService.KEY_CUSTOM_METRIC)
                            val startValue = StartReport.fromJson(startJson)
                            if (dao.start(startValue) != -1L) uri else null
                        }

                        RemoteMetricsService.URI_FINISH_CUSTOM_REPORT -> {
                            val finishJson = values.getAsString(RemoteMetricsService.KEY_CUSTOM_METRIC)
                            val finishValue = FinishReport.fromJson(finishJson)
                            if (dao.finish(finishValue) != -1L) uri else null
                        }

                        else -> {
                            error("Unhandled uri: $uri")
                        }
                    }
                }
            }
        }
        RemoteMetricsService.staticContext = mockk {
            every { getContentResolver() } returns contentResolver
        }
    }

    override fun after() {
        db.close()
        temporaryFolder.delete()
    }

    fun dropBoxTagCountRollups(timestamp: Long) = listOf(
        rollup(
            stringKey = "drop_box_anr_count",
            t = timestamp,
        ),
        rollup(
            stringKey = "drop_box_exception_count",
            t = timestamp,
        ),
        rollup(
            stringKey = "drop_box_wtf_count",
            t = timestamp,
        ),
        rollup(
            stringKey = "drop_box_native_count",
            t = timestamp,
        ),
        rollup(
            stringKey = "drop_box_kmsg_count",
            t = timestamp,
        ),
        rollup(
            stringKey = "drop_box_panic_count",
            t = timestamp,
        ),
    )

    private fun rollup(
        stringKey: String,
        t: Long,
    ) = HighResTelemetry.Rollup(
        metadata = RollupMetadata(
            stringKey = stringKey,
            metricType = Counter,
            dataType = DoubleType,
            internal = false,
        ),
        data = listOf(
            Datum(
                t = t,
                value = JsonPrimitive(0.0),
            ),
        ),
    )
}
