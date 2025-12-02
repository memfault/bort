package com.memfault.bort.metrics.statsd

import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.metrics.statsd.proto.WifiDisconnectReported
import com.memfault.bort.reporting.Reporting
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@ContributesMultibinding(scope = SingletonComponent::class)
class WifiDisconnectedEventMetricListener @Inject constructor() : StatsdEventMetricListener {
    override fun reportEventMetric(
        eventTimestampMillis: Long,
        atom: Atom,
    ) {
        if (atom.wifi_disconnect_reported != null) {
            val wifiDisconnectReported = atom.wifi_disconnect_reported

            Reporting.report().counter(
                name = WIFI_DISCONNECT_REPORTED_COUNT,
            ).increment(timestamp = eventTimestampMillis)

            Reporting.report().event(
                name = WIFI_DISCONNECT_REPORTED_EVENT,
                latestInReport = true,
            ).add(wifiDisconnectReported.toString(), timestamp = eventTimestampMillis)

            val connectionDuration = (wifiDisconnectReported.connected_duration_seconds ?: 0)
                .seconds.inWholeMilliseconds
            val connectionStart = eventTimestampMillis - connectionDuration
            if (connectionDuration > 0 && connectionStart < eventTimestampMillis) {
                val session = Reporting.session(
                    WIFI_DISCONNECTED_SESSION_NAME,
                )
                session.start(timestampMs = connectionStart)

                session.stringProperty(WIFI_BAND_BUCKET_PROPERTY)
                    .update(value = bucketedWifiBand(wifiDisconnectReported.band), timestamp = eventTimestampMillis)
                session.stringProperty(WIFI_BAND_PROPERTY)
                    .update(value = shortWifiBand(wifiDisconnectReported.band), timestamp = eventTimestampMillis)

                session.stringProperty(WIFI_FAILURE_CODE_NAME_PROPERTY).update(
                    value = wifiDisconnectReported.failure_code?.name ?: "",
                    timestamp = eventTimestampMillis,
                )
                session.numberProperty(WIFI_FAILURE_CODE_PROPERTY).update(
                    value = wifiDisconnectReported.failure_code?.value ?: 0,
                    timestamp = eventTimestampMillis,
                )
                session.numberProperty(WIFI_LAST_RSSI_PROPERTY).update(
                    value = wifiDisconnectReported.last_rssi ?: 0,
                    timestamp = eventTimestampMillis,
                )
                session.numberProperty(
                    WIFI_LAST_LINK_SPEED_PROPERTY,
                ).update(value = wifiDisconnectReported.last_link_speed ?: 0, timestamp = eventTimestampMillis)
                session.finish(eventTimestampMillis)
            }
        }
    }

    override fun atoms(): Set<Int> = setOf(WIFI_DISCONNECT_REPORTED_ATOM_FIELD_ID)

    // Suppress the error because while it's redundant with current atoms, the platform
    // might introduce new ones at runtime and we still want to log them.
    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    private fun bucketedWifiBand(band: WifiDisconnectReported.WifiBandBucket?): String = when (band) {
        WifiDisconnectReported.WifiBandBucket.BAND_UNKNOWN, null -> "unknown"
        WifiDisconnectReported.WifiBandBucket.BAND_2G -> "2400"
        WifiDisconnectReported.WifiBandBucket.BAND_5G_LOW -> "[5150, 5250)"
        WifiDisconnectReported.WifiBandBucket.BAND_5G_MIDDLE -> "[5250, 5725)"
        WifiDisconnectReported.WifiBandBucket.BAND_5G_HIGH -> "[5725, 5850)"
        WifiDisconnectReported.WifiBandBucket.BAND_6G_LOW -> "[5925, 6492)"
        WifiDisconnectReported.WifiBandBucket.BAND_6G_MIDDLE -> "[6425, 6875)"
        WifiDisconnectReported.WifiBandBucket.BAND_6G_HIGH -> "[6875, 7125)"
        else -> "unknown (${band.value})"
    }

    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    private fun shortWifiBand(band: WifiDisconnectReported.WifiBandBucket?): String = when (band) {
        WifiDisconnectReported.WifiBandBucket.BAND_2G -> "2.4"
        WifiDisconnectReported.WifiBandBucket.BAND_5G_LOW -> "5"
        WifiDisconnectReported.WifiBandBucket.BAND_5G_MIDDLE -> "5"
        WifiDisconnectReported.WifiBandBucket.BAND_5G_HIGH -> "5"
        WifiDisconnectReported.WifiBandBucket.BAND_6G_LOW -> "6"
        WifiDisconnectReported.WifiBandBucket.BAND_6G_MIDDLE -> "6"
        WifiDisconnectReported.WifiBandBucket.BAND_6G_HIGH -> "6"
        WifiDisconnectReported.WifiBandBucket.BAND_UNKNOWN, null -> "?"
        else -> "? (${band.value})"
    }

    companion object {
        private const val WIFI_DISCONNECT_REPORTED_COUNT = "connectivity.wifi.disconnects"
        private const val WIFI_DISCONNECT_REPORTED_EVENT = "connectivity.wifi.disconnected"
        private const val WIFI_DISCONNECT_REPORTED_ATOM_FIELD_ID = 307
        internal const val WIFI_DISCONNECTED_SESSION_NAME = "wifi_disconnect_session"
        internal const val WIFI_BAND_BUCKET_PROPERTY = "wifi.frequency-band-bucket"
        internal const val WIFI_BAND_PROPERTY = "wifi.frequency-band"
        internal const val WIFI_FAILURE_CODE_NAME_PROPERTY = "wifi.failure-code-name"
        internal const val WIFI_FAILURE_CODE_PROPERTY = "wifi.failure-code"
        internal const val WIFI_LAST_RSSI_PROPERTY = "wifi.last-rssi"
        internal const val WIFI_LAST_LINK_SPEED_PROPERTY = "wifi.last-link-speed"
    }
}
