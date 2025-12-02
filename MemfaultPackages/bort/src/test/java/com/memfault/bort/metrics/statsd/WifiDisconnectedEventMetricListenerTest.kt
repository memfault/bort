package com.memfault.bort.metrics.statsd

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.memfault.bort.metrics.MetricsDbTestEnvironment
import com.memfault.bort.metrics.database.MetricsDb
import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.metrics.statsd.proto.WifiDisconnectReported
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WifiDisconnectedEventMetricListenerTest {
    @get:Rule()
    val metricsDbTestEnvironment = MetricsDbTestEnvironment()

    private val db: MetricsDb get() = metricsDbTestEnvironment.db

    @Test
    fun producesWifiSessionMetricsCorrectly() = runTest {
        val listener = WifiDisconnectedEventMetricListener()
        listener.reportEventMetric(
            eventTimestampMillis = 10000L,
            atom = Atom(
                wifi_disconnect_reported = WifiDisconnectReported(
                    connected_duration_seconds = 5,
                    band = WifiDisconnectReported.WifiBandBucket.BAND_2G,
                    failure_code = WifiDisconnectReported.FailureCode.WIFI_DISABLED,
                    last_rssi = -40,
                    last_link_speed = 10000,
                ),
            ),
        )

        val report = metricsDbTestEnvironment.dao.collectHeartbeat(10000L, false)
        assertThat(report.sessions).hasSize(1)

        val wifiSessionReport = report.sessions[0]
        assertThat(wifiSessionReport.startTimestampMs).isEqualTo(5000)
        assertThat(wifiSessionReport.endTimestampMs).isEqualTo(10000)
        assertThat(
            wifiSessionReport.reportName,
        ).isEqualTo(WifiDisconnectedEventMetricListener.WIFI_DISCONNECTED_SESSION_NAME)

        assertThat(wifiSessionReport.metrics).isEqualTo(
            mapOf(
                WifiDisconnectedEventMetricListener.WIFI_BAND_PROPERTY + ".latest" to JsonPrimitive("2.4"),
                WifiDisconnectedEventMetricListener.WIFI_BAND_BUCKET_PROPERTY + ".latest" to JsonPrimitive("2400"),
                WifiDisconnectedEventMetricListener.WIFI_FAILURE_CODE_PROPERTY + ".latest" to JsonPrimitive(10001.0),
                WifiDisconnectedEventMetricListener.WIFI_FAILURE_CODE_NAME_PROPERTY + ".latest" to
                    JsonPrimitive("WIFI_DISABLED"),
                WifiDisconnectedEventMetricListener.WIFI_LAST_LINK_SPEED_PROPERTY + ".latest" to JsonPrimitive(10000.0),
                WifiDisconnectedEventMetricListener.WIFI_LAST_RSSI_PROPERTY + ".latest" to JsonPrimitive(-40.0),
            ),
        )
    }
}
