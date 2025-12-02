package com.memfault.bort.metrics.statsd

import assertk.assertThat
import assertk.assertions.hasSize
import com.memfault.bort.metrics.statsd.proto.ConfigMetricsReport
import com.memfault.bort.metrics.statsd.proto.ConfigMetricsReportList
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class StatsdManagerServiceServiceTest {
    private val statsdManagerProxy: StatsdManagerProxy = mockk {
        every { getReports(any()) } answers { reportsByteBuffer }
    }
    private var reportsByteBuffer: ByteArray = ConfigMetricsReportList.ADAPTER.encode(
        ConfigMetricsReportList(
            reports = listOf(
                ConfigMetricsReport(
                    metrics = listOf(),
                ),
                ConfigMetricsReport(
                    metrics = listOf(),
                ),
            ),
        ),
    )
    private val service: RealStatsdManagerService = RealStatsdManagerService(statsdManagerProxy)

    @Test
    fun `decodes correctly formed report buffers`() {
        val reports = service.getReports(123)
        assertThat(reports).hasSize(2)
    }

    @Test
    fun `incorrectly formed buffers result in empty report lists`() {
        reportsByteBuffer = reportsByteBuffer.copyOfRange(0, 1)
        val reports = service.getReports(123)
        assertThat(reports).hasSize(0)
    }
}
