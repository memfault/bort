package com.memfault.bort.metrics

import com.memfault.bort.BortSystemCapabilities
import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.dropbox.MetricReport
import com.memfault.bort.dropbox.MetricReportWithHighResFile
import com.memfault.bort.metrics.custom.CustomMetrics
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.settings.StructuredLogSettings
import com.memfault.bort.time.CombinedTimeProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.seconds

internal class HeartbeatReportCollectorTest {
    private var metricReportEnabledSetting = true
    private var highResMetricsEnabledSetting = true
    private var useBortMetricsDb = false
    private val settings = object : StructuredLogSettings {
        override val dataSourceEnabled get() = TODO("Not used")
        override val rateLimitingSettings get() = TODO("Not used")
        override val dumpPeriod get() = TODO("Not used")
        override val numEventsBeforeDump get() = TODO("Not used")
        override val maxMessageSizeBytes get() = TODO("Not used")
        override val minStorageThresholdBytes get() = TODO("Not used")
        override val metricsReportEnabled get() = metricReportEnabledSetting
        override val highResMetricsEnabled get() = highResMetricsEnabledSetting
    }
    private var finishReportSuccess = true
    private val reportFinisher = object : ReportFinisher {
        override fun finishHeartbeat(): Boolean {
            if (useBortMetricsDb) {
                throw IllegalStateException("Shouldn't be calling structured log db")
            }
            sendResults()
            return finishReportSuccess
        }
    }
    private val report = MetricReport(
        version = 1,
        startTimestampMs = 2,
        endTimestampMs = 3,
        reportType = "4",
        metrics = mapOf(),
        internalMetrics = mapOf(),
    )
    private val customMetrics: CustomMetrics = mockk {
        coEvery { finishReport(any(), any()) } answers {
            if (!useBortMetricsDb) {
                throw IllegalStateException("Shouldn't be calling bort metrics db")
            }
            CustomReport(report, highResFile)
        }
    }
    private val bortSystemCapabilities: BortSystemCapabilities = mockk {
        every { useBortMetricsDb() } answers {
            useBortMetricsDb
        }
    }
    private val combinedTimeProvider: CombinedTimeProvider = FakeCombinedTimeProvider
    private val collector =
        HeartbeatReportCollector(settings, reportFinisher, customMetrics, bortSystemCapabilities, combinedTimeProvider)
    private var sendReport = false
    private var sendHighRes = false
    private var reverseOrder = false

    private fun sendResults() {
        if (sendReport && !reverseOrder) collector.handleFinishedHeartbeatReport(report)
        if (sendHighRes) collector.handleHighResMetricsFile(highResFile)
        if (sendReport && reverseOrder) collector.handleFinishedHeartbeatReport(report)
    }

    @get:Rule
    val tempFolder = TemporaryFolder.builder().assureDeletion().build()
    private lateinit var highResFile: File

    @Before
    fun setup() {
        tempFolder.create()
        highResFile = tempFolder.newFile("high_res")
    }

    @Test
    fun structuredLogD_timesOutWhenNoReportOrHighResFile() {
        metricReportEnabledSetting = true
        highResMetricsEnabledSetting = true
        finishReportSuccess = true
        sendReport = false
        sendHighRes = false
        useBortMetricsDb = false
        runTest {
            val result = collector.finishAndCollectHeartbeatReport(timeout = 1.seconds)
            assertNull(result)
        }
    }

    @Test
    fun structuredLogD_timesOutWhenNoReport() {
        metricReportEnabledSetting = true
        highResMetricsEnabledSetting = true
        finishReportSuccess = true
        sendReport = false
        sendHighRes = true
        useBortMetricsDb = false
        runTest {
            val result = collector.finishAndCollectHeartbeatReport(timeout = 1.seconds)
            assertNull(result)
            assertFalse(highResFile.exists())
        }
    }

    @Test
    fun structuredLogD_successWithHighRes() {
        metricReportEnabledSetting = true
        highResMetricsEnabledSetting = true
        finishReportSuccess = true
        sendReport = true
        sendHighRes = true
        useBortMetricsDb = false
        runTest {
            val result = collector.finishAndCollectHeartbeatReport(timeout = 1.seconds)
            assertEquals(MetricReportWithHighResFile(report, highResFile), result)
            assertTrue(highResFile.exists())
        }
    }

    @Test
    fun structuredLogD_successWithHighRes_reverseOrder() {
        metricReportEnabledSetting = true
        highResMetricsEnabledSetting = true
        finishReportSuccess = true
        sendReport = true
        sendHighRes = true
        reverseOrder = true
        useBortMetricsDb = false
        runTest {
            val result = collector.finishAndCollectHeartbeatReport(timeout = 1.seconds)
            assertEquals(MetricReportWithHighResFile(report, highResFile), result)
            assertTrue(highResFile.exists())
        }
    }

    @Test
    fun structuredLogD_successWithoutHighRes_enabled() {
        metricReportEnabledSetting = true
        highResMetricsEnabledSetting = true
        finishReportSuccess = true
        sendReport = true
        sendHighRes = false
        useBortMetricsDb = false
        runTest {
            val result = collector.finishAndCollectHeartbeatReport(timeout = 1.seconds)
            assertEquals(MetricReportWithHighResFile(report, null), result)
            assertTrue(highResFile.exists())
        }
    }

    @Test
    fun structuredLogD_successWithoutHighRes_disabled() {
        metricReportEnabledSetting = true
        highResMetricsEnabledSetting = false
        finishReportSuccess = true
        sendReport = true
        sendHighRes = false
        useBortMetricsDb = false
        runTest {
            val result = collector.finishAndCollectHeartbeatReport(timeout = 1.seconds)
            assertEquals(MetricReportWithHighResFile(report, null), result)
            assertTrue(highResFile.exists())
        }
    }

    @Test
    fun bortMetricsDb_successWithHighRes() {
        metricReportEnabledSetting = true
        highResMetricsEnabledSetting = true
        finishReportSuccess = false
        sendReport = false
        sendHighRes = false
        useBortMetricsDb = true
        runTest {
            val result = collector.finishAndCollectHeartbeatReport(timeout = 1.seconds)
            assertEquals(MetricReportWithHighResFile(report, highResFile), result)
            assertTrue(highResFile.exists())
        }
    }
}
