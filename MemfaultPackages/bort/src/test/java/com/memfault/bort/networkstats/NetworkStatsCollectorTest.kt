package com.memfault.bort.networkstats

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isNotNull
import assertk.assertions.prop
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.makeFakeSharedPreferences
import com.memfault.bort.metrics.CrashFreeHoursMetricLogger.Companion.OPERATIONAL_CRASHES_METRIC_KEY
import com.memfault.bort.metrics.HighResTelemetry
import com.memfault.bort.metrics.HighResTelemetry.DataType
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.MetricType
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import com.memfault.bort.metrics.SignificantApp
import com.memfault.bort.metrics.SignificantAppsProvider
import com.memfault.bort.metrics.custom.CustomMetrics
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.metrics.custom.MetricReport
import com.memfault.bort.metrics.custom.RealCustomMetrics
import com.memfault.bort.metrics.database.MetricsDb
import com.memfault.bort.parsers.Package
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.reporting.FinishReport
import com.memfault.bort.reporting.MetricValue
import com.memfault.bort.reporting.RemoteMetricsService
import com.memfault.bort.reporting.StartReport
import com.memfault.bort.settings.DailyHeartbeatEnabled
import com.memfault.bort.settings.HighResMetricsEnabled
import com.memfault.bort.settings.NetworkUsageSettings
import com.memfault.bort.test.util.TemporaryFolderTemporaryFileFactory
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.boxed
import com.memfault.bort.tokenbucket.TokenBucketStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class NetworkStatsCollectorTest {
    private val fakeSharedPreferences = makeFakeSharedPreferences()
    private val lastNetworkStatsCollectionTimestamp = LastNetworkStatsCollectionTimestamp(fakeSharedPreferences)
    private val networkStatsQueries = mockk<NetworkStatsQueries>()
    private val packageManagerClient = mockk<PackageManagerClient> {
        coEvery { getPackageManagerReport() } answers { PackageManagerReport() }
    }

    private val fakeNetworkUsageSettings = object : NetworkUsageSettings {
        override var dataSourceEnabled: Boolean = true
        override var collectionReceiveThresholdKb: Long = 1000
        override var collectionTransmitThresholdKb: Long = 1000
    }

    private val significantAppsProvider = object : SignificantAppsProvider {
        val internalApps = listOf(
            SignificantApp(
                packageName = "com.memfault.bort",
                identifier = "bort",
                internal = true,
            ),
            SignificantApp(
                packageName = "com.memfault.bort.ota",
                identifier = "ota",
                internal = true,
            ),
        )
        val externalApps = mutableListOf<SignificantApp>()

        override fun internalApps(): List<SignificantApp> = internalApps
        override fun externalApps(): List<SignificantApp> = externalApps
    }

    private val collector = RealNetworkStatsCollector(
        lastNetworkStatsCollectionTimestamp = lastNetworkStatsCollectionTimestamp,
        networkStatsQueries = networkStatsQueries,
        packageManagerClient = packageManagerClient,
        networkUsageSettings = fakeNetworkUsageSettings,
        significantAppsProvider = significantAppsProvider,
    )

    private fun time(timeMs: Long) = CombinedTime(
        uptime = timeMs.milliseconds.boxed(),
        elapsedRealtime = timeMs.milliseconds.boxed(),
        linuxBootId = "bootid",
        bootCount = 1,
        timestamp = Instant.ofEpochMilli(timeMs),
    )

    private val fakeNetworkStatsSummary = NetworkStatsSummary(
        uid = NetworkStatsUid.fromUid(10_000),
        state = NetworkStatsState.ALL,
        metered = NetworkStatsMetered.ALL,
        roaming = NetworkStatsRoaming.ALL,
        defaultNetwork = null,
        connectivity = NetworkStatsConnectivity.ETHERNET,
        startEpochMillis = 0,
        endEpochMillis = 0,
        rxBytes = 1000,
        rxPackets = 1,
        txBytes = 2000,
        txPackets = 1,
    )

    @get:Rule val tempFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private lateinit var db: MetricsDb
    private lateinit var dao: CustomMetrics

    private val dailyHeartbeatEnabled = object : DailyHeartbeatEnabled {
        var enabled: Boolean = false
        override fun invoke(): Boolean = enabled
    }

    private val highResMetricsEnabled = HighResMetricsEnabled { true }

    private var rateLimited = false
    private val tokenBucketStore = mockk<TokenBucketStore> {
        every { takeSimple(any(), any(), any()) } answers { !rateLimited }
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MetricsDb::class.java)
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()

        dao = RealCustomMetrics(
            db = db,
            temporaryFileFactory = TemporaryFolderTemporaryFileFactory(tempFolder),
            dailyHeartbeatEnabled = dailyHeartbeatEnabled,
            highResMetricsEnabled = highResMetricsEnabled,
            sessionMetricsTokenBucketStore = tokenBucketStore,
        )

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
        RemoteMetricsService.context = mockk {
            every { getContentResolver() } returns contentResolver
        }
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        db.close()
    }

    @Test fun `return null when disabled`() = runTest {
        fakeNetworkUsageSettings.dataSourceEnabled = false

        collector.collectAndRecord(time(2.hours.inWholeMilliseconds), time(1.hours.inWholeMilliseconds))

        dao.collectHeartbeat(endTimestampMs = 2.hours.inWholeMilliseconds).apply {
            assertThat(hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                OPERATIONAL_CRASHES_METRIC_KEY to JsonPrimitive(0.0),
            )
        }
    }

    @Test fun `check total usage`() = runTest {
        coEvery {
            networkStatsQueries.getTotalUsage(start = any(), end = any(), connectivity = any())
        } coAnswers { call ->
            val connectivity = call.invocation.args[2] as NetworkStatsConnectivity
            fakeNetworkStatsSummary.copy(rxBytes = 10_000_000, connectivity = connectivity)
        }

        coEvery {
            networkStatsQueries.getUsageByApp(start = any(), end = any(), connectivity = any())
        } coAnswers { emptyMap() }

        collector.collectAndRecord(time(2.hours.inWholeMilliseconds), time(1.hours.inWholeMilliseconds))

        assertThat(dao.collectHeartbeat(endTimestampMs = 2.hours.inWholeMilliseconds)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "network.total.in.all.latest" to JsonPrimitive(40_000.0),
                "network.total.in.wifi.latest" to JsonPrimitive(10_000.0),
                "network.total.in.mobile.latest" to JsonPrimitive(10_000.0),
                "network.total.in.eth.latest" to JsonPrimitive(10_000.0),
                "network.total.in.bt.latest" to JsonPrimitive(10_000.0),

                "network.total.out.all.latest" to JsonPrimitive(8.0),
                "network.total.out.wifi.latest" to JsonPrimitive(2.0),
                "network.total.out.mobile.latest" to JsonPrimitive(2.0),
                "network.total.out.eth.latest" to JsonPrimitive(2.0),
                "network.total.out.bt.latest" to JsonPrimitive(2.0),

                OPERATIONAL_CRASHES_METRIC_KEY to JsonPrimitive(0.0),
            )

            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::internalMetrics).containsOnly(
                "network.app.in.all_bort" to JsonPrimitive(0.0),
                "network.app.in.all_ota" to JsonPrimitive(0.0),

                "network.app.out.all_bort" to JsonPrimitive(0.0),
                "network.app.out.all_ota" to JsonPrimitive(0.0),
            )

            prop(CustomReport::hrt).isNotNull()
                .transform { hrt -> HighResTelemetry.decodeFromStream(hrt) }
                .prop(HighResTelemetry::rollups)
                .containsExactlyInAnyOrder(
                    rollup("network.total.in.all", 40_000.0),
                    rollup("network.total.in.wifi", 10_000.0),
                    rollup("network.total.in.mobile", 10_000.0),
                    rollup("network.total.in.eth", 10_000.0),
                    rollup("network.total.in.bt", 10_000.0),

                    rollup("network.total.out.all", 8.0),
                    rollup("network.total.out.wifi", 2.0),
                    rollup("network.total.out.mobile", 2.0),
                    rollup("network.total.out.eth", 2.0),
                    rollup("network.total.out.bt", 2.0),

                    rollup("network.app.in.all_bort", 0.0, internal = true),
                    rollup("network.app.out.all_bort", 0.0, internal = true),

                    rollup("network.app.in.all_ota", 0.0, internal = true),
                    rollup("network.app.out.all_ota", 0.0, internal = true),
                )
        }
    }

    @Test fun `check per app usage`() = runTest {
        coEvery {
            networkStatsQueries.getTotalUsage(start = any(), end = any(), connectivity = any())
        } coAnswers { null }

        coEvery {
            networkStatsQueries.getUsageByApp(start = any(), end = any(), connectivity = any())
        } coAnswers {
            val connectivity = call.invocation.args[2] as NetworkStatsConnectivity
            mapOf(10_000 to listOf(fakeNetworkStatsSummary.copy(txBytes = 10_000_000, connectivity = connectivity)))
        }

        coEvery {
            packageManagerClient.getPackageManagerReport()
        } coAnswers {
            PackageManagerReport(
                listOf(Package(id = "com.memfault.bort", userId = 10_000)),
            )
        }

        collector.collectAndRecord(time(2.hours.inWholeMilliseconds), time(1.hours.inWholeMilliseconds))

        assertThat(dao.collectHeartbeat(endTimestampMs = 2.hours.inWholeMilliseconds)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "network.total.in.all.latest" to JsonPrimitive(0.0),
                "network.total.out.all.latest" to JsonPrimitive(0.0),

                OPERATIONAL_CRASHES_METRIC_KEY to JsonPrimitive(0.0),
            )

            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::internalMetrics).containsOnly(
                "network.app.in.all_bort" to JsonPrimitive(4.0),
                "network.app.in.wifi_bort" to JsonPrimitive(1.0),
                "network.app.in.mobile_bort" to JsonPrimitive(1.0),
                "network.app.in.eth_bort" to JsonPrimitive(1.0),
                "network.app.in.bt_bort" to JsonPrimitive(1.0),

                "network.app.out.all_bort" to JsonPrimitive(40_000.0),
                "network.app.out.wifi_bort" to JsonPrimitive(10_000.0),
                "network.app.out.mobile_bort" to JsonPrimitive(10_000.0),
                "network.app.out.eth_bort" to JsonPrimitive(10_000.0),
                "network.app.out.bt_bort" to JsonPrimitive(10_000.0),

                "network.app.in.all_ota" to JsonPrimitive(0.0),

                "network.app.out.all_ota" to JsonPrimitive(0.0),
            )

            prop(CustomReport::hrt).isNotNull()
                .transform { hrt -> HighResTelemetry.decodeFromStream(hrt) }
                .prop(HighResTelemetry::rollups)
                .containsExactlyInAnyOrder(
                    rollup("network.total.in.all", 0.0),
                    rollup("network.total.out.all", 0.0),

                    rollup("network.app.out.all_com.memfault.bort", 40_000.0),
                    rollup("network.app.out.wifi_com.memfault.bort", 10_000.0),
                    rollup("network.app.out.mobile_com.memfault.bort", 10_000.0),
                    rollup("network.app.out.eth_com.memfault.bort", 10_000.0),
                    rollup("network.app.out.bt_com.memfault.bort", 10_000.0),

                    rollup("network.app.in.all_bort", 4.0, internal = true),
                    rollup("network.app.in.wifi_bort", 1.0, internal = true),
                    rollup("network.app.in.mobile_bort", 1.0, internal = true),
                    rollup("network.app.in.eth_bort", 1.0, internal = true),
                    rollup("network.app.in.bt_bort", 1.0, internal = true),

                    rollup("network.app.out.all_bort", 40_000.0, internal = true),
                    rollup("network.app.out.wifi_bort", 10_000.0, internal = true),
                    rollup("network.app.out.mobile_bort", 10_000.0, internal = true),
                    rollup("network.app.out.eth_bort", 10_000.0, internal = true),
                    rollup("network.app.out.bt_bort", 10_000.0, internal = true),

                    rollup("network.app.in.all_ota", 0.0, internal = true),

                    rollup("network.app.out.all_ota", 0.0, internal = true),
                )
        }
    }

    @Test fun `always record total, ignore app usage below threshold`() = runTest {
        fakeNetworkUsageSettings.collectionReceiveThresholdKb = 3
        fakeNetworkUsageSettings.collectionTransmitThresholdKb = 3

        coEvery {
            networkStatsQueries.getTotalUsage(start = any(), end = any(), connectivity = any())
        } coAnswers { fakeNetworkStatsSummary.copy(rxBytes = 1) }

        coEvery {
            networkStatsQueries.getUsageByApp(start = any(), end = any(), connectivity = any())
        } coAnswers { call ->
            val connectivity = call.invocation.args[2] as NetworkStatsConnectivity
            mapOf(
                10_000 to listOf(
                    fakeNetworkStatsSummary.copy(
                        rxBytes = 2000,
                        txBytes = 5000,
                        connectivity = connectivity,
                    ),
                ),
                15_000 to listOf(
                    fakeNetworkStatsSummary.copy(
                        rxBytes = 4000,
                        txBytes = 2000,
                        connectivity = connectivity,
                    ),
                ),
            )
        }

        coEvery {
            packageManagerClient.getPackageManagerReport()
        } coAnswers {
            PackageManagerReport(
                listOf(
                    Package(id = "com.memfault.bort", userId = 10_000),
                    Package(id = "com.my.app", userId = 15_000),
                ),
            )
        }

        collector.collectAndRecord(time(2.hours.inWholeMilliseconds), time(1.hours.inWholeMilliseconds))

        assertThat(dao.collectHeartbeat(endTimestampMs = 2.hours.inWholeMilliseconds)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "network.total.in.all.latest" to JsonPrimitive(0.0),
                "network.total.in.wifi.latest" to JsonPrimitive(0.0),
                "network.total.in.mobile.latest" to JsonPrimitive(0.0),
                "network.total.in.eth.latest" to JsonPrimitive(0.0),
                "network.total.in.bt.latest" to JsonPrimitive(0.0),

                "network.total.out.all.latest" to JsonPrimitive(8.0),
                "network.total.out.wifi.latest" to JsonPrimitive(2.0),
                "network.total.out.mobile.latest" to JsonPrimitive(2.0),
                "network.total.out.eth.latest" to JsonPrimitive(2.0),
                "network.total.out.bt.latest" to JsonPrimitive(2.0),

                OPERATIONAL_CRASHES_METRIC_KEY to JsonPrimitive(0.0),
            )

            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::internalMetrics).containsOnly(
                "network.app.in.all_bort" to JsonPrimitive(8.0),
                "network.app.in.wifi_bort" to JsonPrimitive(2.0),
                "network.app.in.mobile_bort" to JsonPrimitive(2.0),
                "network.app.in.eth_bort" to JsonPrimitive(2.0),
                "network.app.in.bt_bort" to JsonPrimitive(2.0),

                "network.app.out.all_bort" to JsonPrimitive(20.0),
                "network.app.out.wifi_bort" to JsonPrimitive(5.0),
                "network.app.out.mobile_bort" to JsonPrimitive(5.0),
                "network.app.out.eth_bort" to JsonPrimitive(5.0),
                "network.app.out.bt_bort" to JsonPrimitive(5.0),

                "network.app.in.all_ota" to JsonPrimitive(0.0),

                "network.app.out.all_ota" to JsonPrimitive(0.0),
            )

            prop(CustomReport::hrt).isNotNull()
                .transform { hrt -> HighResTelemetry.decodeFromStream(hrt) }
                .prop(HighResTelemetry::rollups)
                .containsExactlyInAnyOrder(
                    rollup("network.total.in.all", 0.0),
                    rollup("network.total.in.eth", 0.0),
                    rollup("network.total.in.wifi", 0.0),
                    rollup("network.total.in.mobile", 0.0),
                    rollup("network.total.in.bt", 0.0),

                    rollup("network.total.out.all", 8.0),
                    rollup("network.total.out.eth", 2.0),
                    rollup("network.total.out.wifi", 2.0),
                    rollup("network.total.out.mobile", 2.0),
                    rollup("network.total.out.bt", 2.0),

                    rollup("network.app.in.all_bort", 8.0, internal = true),
                    rollup("network.app.in.wifi_bort", 2.0, internal = true),
                    rollup("network.app.in.mobile_bort", 2.0, internal = true),
                    rollup("network.app.in.eth_bort", 2.0, internal = true),
                    rollup("network.app.in.bt_bort", 2.0, internal = true),

                    rollup("network.app.out.all_bort", 20.0, internal = true),
                    rollup("network.app.out.wifi_bort", 5.0, internal = true),
                    rollup("network.app.out.mobile_bort", 5.0, internal = true),
                    rollup("network.app.out.eth_bort", 5.0, internal = true),
                    rollup("network.app.out.bt_bort", 5.0, internal = true),

                    rollup("network.app.in.all_ota", 0.0, internal = true),

                    rollup("network.app.out.all_ota", 0.0, internal = true),

                    rollup("network.app.in.all_com.memfault.bort", 8.0),

                    rollup("network.app.out.all_com.memfault.bort", 20.0),
                    rollup("network.app.out.wifi_com.memfault.bort", 5.0),
                    rollup("network.app.out.mobile_com.memfault.bort", 5.0),
                    rollup("network.app.out.eth_com.memfault.bort", 5.0),
                    rollup("network.app.out.bt_com.memfault.bort", 5.0),

                    rollup("network.app.out.all_com.my.app", 8.0),

                    rollup("network.app.in.all_com.my.app", 16.0),
                    rollup("network.app.in.wifi_com.my.app", 4.0),
                    rollup("network.app.in.mobile_com.my.app", 4.0),
                    rollup("network.app.in.eth_com.my.app", 4.0),
                    rollup("network.app.in.bt_com.my.app", 4.0),
                )
        }
    }

    @Test fun `bort and ota always track internal metrics`() = runTest {
        coEvery {
            networkStatsQueries.getTotalUsage(start = any(), end = any(), connectivity = any())
        } coAnswers { null }

        coEvery {
            networkStatsQueries.getUsageByApp(start = any(), end = any(), connectivity = any())
        } coAnswers {
            val connectivity = call.invocation.args[2] as NetworkStatsConnectivity
            mapOf(10_000 to listOf(fakeNetworkStatsSummary.copy(txBytes = 10_000_000, connectivity = connectivity)))
        }

        coEvery {
            packageManagerClient.getPackageManagerReport()
        } coAnswers {
            PackageManagerReport(
                listOf(Package(id = "com.memfault.bort", userId = 10_000)),
            )
        }

        collector.collectAndRecord(time(2.hours.inWholeMilliseconds), time(1.hours.inWholeMilliseconds))

        assertThat(dao.collectHeartbeat(endTimestampMs = 2.hours.inWholeMilliseconds)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::internalMetrics).containsOnly(
                "network.app.in.all_bort" to JsonPrimitive(4.0),
                "network.app.in.eth_bort" to JsonPrimitive(1.0),
                "network.app.in.mobile_bort" to JsonPrimitive(1.0),
                "network.app.in.wifi_bort" to JsonPrimitive(1.0),
                "network.app.in.bt_bort" to JsonPrimitive(1.0),

                "network.app.out.all_bort" to JsonPrimitive(40_000.0),
                "network.app.out.eth_bort" to JsonPrimitive(10_000.0),
                "network.app.out.mobile_bort" to JsonPrimitive(10_000.0),
                "network.app.out.wifi_bort" to JsonPrimitive(10_000.0),
                "network.app.out.bt_bort" to JsonPrimitive(10_000.0),

                "network.app.in.all_ota" to JsonPrimitive(0.0),

                "network.app.out.all_ota" to JsonPrimitive(0.0),
            )

            prop(CustomReport::hrt).isNotNull()
                .transform { hrt -> HighResTelemetry.decodeFromStream(hrt) }
                .prop(HighResTelemetry::rollups)
                .containsExactlyInAnyOrder(
                    rollup("network.total.in.all", 0.0),
                    rollup("network.total.out.all", 0.0),

                    rollup("network.app.in.all_bort", 4.0, internal = true),
                    rollup("network.app.in.wifi_bort", 1.0, internal = true),
                    rollup("network.app.in.mobile_bort", 1.0, internal = true),
                    rollup("network.app.in.eth_bort", 1.0, internal = true),
                    rollup("network.app.in.bt_bort", 1.0, internal = true),

                    rollup("network.app.out.all_bort", 40_000.0, internal = true),
                    rollup("network.app.out.wifi_bort", 10_000.0, internal = true),
                    rollup("network.app.out.mobile_bort", 10_000.0, internal = true),
                    rollup("network.app.out.eth_bort", 10_000.0, internal = true),
                    rollup("network.app.out.bt_bort", 10_000.0, internal = true),

                    rollup("network.app.in.all_ota", 0.0, internal = true),

                    rollup("network.app.out.all_ota", 0.0, internal = true),

                    rollup("network.app.out.all_com.memfault.bort", 40_000.0, internal = false),
                    rollup("network.app.out.wifi_com.memfault.bort", 10_000.0, internal = false),
                    rollup("network.app.out.mobile_com.memfault.bort", 10_000.0, internal = false),
                    rollup("network.app.out.eth_com.memfault.bort", 10_000.0, internal = false),
                    rollup("network.app.out.bt_com.memfault.bort", 10_000.0, internal = false),
                )
        }
    }

    @Test fun `query range invalid`() = runTest {
        collector.collectAndRecord(
            collectionTime = time(2.hours.inWholeMilliseconds),
            lastHeartbeatUptime = time(3.hours.inWholeMilliseconds),
        )

        assertThat(dao.collectHeartbeat(endTimestampMs = 4.hours.inWholeMilliseconds)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                OPERATIONAL_CRASHES_METRIC_KEY to JsonPrimitive(0.0),
            )
        }
    }

    private fun rollup(
        stringKey: String,
        value: Number,
        internal: Boolean = false,
    ) = HighResTelemetry.Rollup(
        metadata = RollupMetadata(
            stringKey = stringKey,
            metricType = MetricType.Gauge,
            dataType = DataType.DoubleType,
            internal = internal,
        ),
        data = listOf(
            Datum(
                t = 2.hours.inWholeMilliseconds,
                value = JsonPrimitive(value),
            ),
        ),
    )
}
