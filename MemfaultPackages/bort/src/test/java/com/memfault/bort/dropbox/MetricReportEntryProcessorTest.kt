package com.memfault.bort.dropbox

import com.memfault.bort.metrics.HeartbeatReportCollector
import com.memfault.bort.test.util.TestTemporaryFileFactory
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals

class MetricReportEntryProcessorTest {
    lateinit var processor: MetricReportEntryProcessor
    lateinit var heartbeat: CapturingSlot<MetricReport>
    lateinit var heartbeatReportCollector: HeartbeatReportCollector
    var metricReportEnabled: Boolean = true
    private var allowedByRateLimit = true

    @Before
    fun setUp() {
        metricReportEnabled = true
        heartbeatReportCollector = mockk(relaxed = true)

        heartbeat = slot()
        every {
            heartbeatReportCollector.handleFinishedHeartbeatReport(capture(heartbeat))
        } returns Unit

        processor = MetricReportEntryProcessor(
            temporaryFileFactory = TestTemporaryFileFactory,
            tokenBucketStore = mockk {
                every { takeSimple(any(), any(), any()) } answers { allowedByRateLimit }
            },
            metricReportEnabledConfig = { metricReportEnabled },
            heartbeatReportCollector = heartbeatReportCollector,
        )
    }

    @Test
    fun metricExtraction() {
        runBlocking {
            processor.process(mockEntry(text = VALID_HEARTBEAT_REPORT_FIXTURE, tag_ = "memfault_report"))

            assertEquals(
                mapOf(
                    "string_metric" to JsonPrimitive("some value"),
                    "double_metric" to JsonPrimitive(3.0),
                ),
                heartbeat.captured.metrics,
            )

            assertEquals(
                mapOf(
                    "internal_string_metric" to JsonPrimitive("some value"),
                    "internal_double_metric" to JsonPrimitive(3.0),
                ),
                heartbeat.captured.internalMetrics,
            )
        }
    }

    @Test
    fun metricExtractionNoInternal() {
        runBlocking {
            processor.process(mockEntry(text = VALID_HEARTBEAT_REPORT_FIXTURE_NO_INTERNAL, tag_ = "memfault_report"))

            assertEquals(
                mapOf(
                    "string_metric" to JsonPrimitive("some value"),
                    "double_metric" to JsonPrimitive(3.0),
                ),
                heartbeat.captured.metrics,
            )
            assertEquals(
                emptyMap<String, JsonPrimitive>(),
                heartbeat.captured.internalMetrics,
            )
        }
    }

    @Test
    fun noProcessingWhenReportIsEmpty() {
        runBlocking {
            processor.process(mockEntry(text = "", tag_ = "memfault_report"))
            verify(exactly = 0) { heartbeatReportCollector.handleFinishedHeartbeatReport(any()) }
        }
    }

    @Test
    fun noProcessingWhenReportIsNotParseable() {
        runBlocking {
            processor.process(mockEntry(text = MALFORMED_REPORT_FIXTURE, tag_ = "memfault_report"))
            verify(exactly = 0) { heartbeatReportCollector.handleFinishedHeartbeatReport(any()) }
        }
    }

    @Test
    fun noProcessingForNonHeartbeatReports() {
        runBlocking {
            processor.process(mockEntry(text = NON_HEARTBEAT_REPORT_FIXTURE, tag_ = "memfault_report"))
            verify(exactly = 0) { heartbeatReportCollector.handleFinishedHeartbeatReport(any()) }
        }
    }

    @Test
    fun rateLimiting() {
        runBlocking {
            allowedByRateLimit = true
            processor.process(mockEntry(text = VALID_HEARTBEAT_REPORT_FIXTURE, tag_ = "memfault_report"))
            allowedByRateLimit = false
            processor.process(mockEntry(text = VALID_HEARTBEAT_REPORT_FIXTURE, tag_ = "memfault_report"))
            // The second call will be ignored by rate limiting
            verify(exactly = 1) { heartbeatReportCollector.handleFinishedHeartbeatReport(any()) }
        }
    }

    @Test
    fun metricReportDisabled() {
        metricReportEnabled = false
        runBlocking {
            repeat(2) {
                processor.process(mockEntry(text = VALID_HEARTBEAT_REPORT_FIXTURE, tag_ = "memfault_report"))
            }
            // All calls will be ignored because reports are disabled by settings
            verify(exactly = 0) { heartbeatReportCollector.handleFinishedHeartbeatReport(any()) }
        }
    }
}

private val VALID_HEARTBEAT_REPORT_FIXTURE = """{
 "version": 1,
 "startTimestampMs": "123456789",
 "endTimestampMs": "987654321",
 "reportType": "Heartbeat",
 "metrics": {
    "string_metric": "some value",
    "double_metric": 3.0
 },
 "internalMetrics": {
    "internal_string_metric": "some value",
    "internal_double_metric": 3.0
 }
}
""".trimIndent()

private val VALID_HEARTBEAT_REPORT_FIXTURE_NO_INTERNAL = """{
 "version": 1,
 "startTimestampMs": "123456789",
 "endTimestampMs": "987654321",
 "reportType": "Heartbeat",
 "metrics": {
    "string_metric": "some value",
    "double_metric": 3.0
 }
}
""".trimIndent()

private val MALFORMED_REPORT_FIXTURE = "[}"

private val NON_HEARTBEAT_REPORT_FIXTURE = """{
 "version": 1,
 "startTimestampMs": "123456789",
 "endTimestampMs": "987654321",
 "reportType": "smart_chair_metrics",
 "metrics": {
    "string_metric": "some value",
    "double_metric": 3.0
 },
}
""".trimIndent()
