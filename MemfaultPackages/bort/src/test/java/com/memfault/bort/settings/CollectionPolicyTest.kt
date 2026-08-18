package com.memfault.bort.settings

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.settings.CollectedData.CONTINUOUS_LOGS
import com.memfault.bort.settings.CollectedData.CRASH_ARTIFACT
import com.memfault.bort.settings.CollectedData.DEBUGGING_ARTIFACT
import com.memfault.bort.settings.CollectedData.DEVICE_PROPERTIES
import com.memfault.bort.settings.CollectedData.HIGH_RES_TELEMETRY
import com.memfault.bort.settings.CollectedData.LOGCAT_CAPTURE
import com.memfault.bort.settings.CollectedData.METRICS
import com.memfault.bort.settings.CollectedData.SESSION
import com.memfault.bort.settings.CollectionDecision.FULL
import com.memfault.bort.settings.CollectionDecision.NONE
import com.memfault.bort.settings.CollectionDecision.TRANSIENT
import com.memfault.bort.settings.Resolution.HIGH
import com.memfault.bort.settings.Resolution.LOW
import com.memfault.bort.settings.Resolution.NORMAL
import com.memfault.bort.settings.Resolution.OFF
import org.junit.Test

/**
 * The visibility levels are combinations of the per-aspect resolutions, so each level is asserted as a whole.
 */
class CollectionPolicyTest {
    private fun config(
        debugging: Resolution,
        logging: Resolution,
        monitoring: Resolution,
        sessions: Resolution,
    ) = SamplingConfig(
        debuggingResolution = debugging,
        loggingResolution = logging,
        monitoringResolution = monitoring,
        sessionsResolution = sessions,
    )

    private val low = config(debugging = OFF, logging = OFF, monitoring = OFF, sessions = OFF)
    private val medium = config(debugging = OFF, logging = OFF, monitoring = NORMAL, sessions = NORMAL)
    private val high = config(debugging = NORMAL, logging = OFF, monitoring = NORMAL, sessions = NORMAL)
    private val highPlusLogs = config(debugging = NORMAL, logging = NORMAL, monitoring = NORMAL, sessions = NORMAL)

    @Test
    fun devicePropertiesAreCollectedAtEveryLevel() {
        listOf(low, medium, high, highPlusLogs).forEach { config ->
            assertThat(config.shouldCollect(DEVICE_PROPERTIES)).isEqualTo(FULL)
        }
    }

    @Test
    fun lowCollectsNothingExceptDeviceProperties() {
        assertThat(low.shouldCollect(DEVICE_PROPERTIES)).isEqualTo(FULL)
        assertThat(low.shouldCollect(METRICS)).isEqualTo(NONE)
        assertThat(low.shouldCollect(SESSION)).isEqualTo(NONE)
        assertThat(low.shouldCollect(HIGH_RES_TELEMETRY)).isEqualTo(NONE)
        assertThat(low.shouldCollect(CRASH_ARTIFACT)).isEqualTo(NONE)
        assertThat(low.shouldCollect(DEBUGGING_ARTIFACT)).isEqualTo(NONE)
        assertThat(low.shouldCollect(LOGCAT_CAPTURE)).isEqualTo(NONE)
        assertThat(low.shouldCollect(CONTINUOUS_LOGS)).isEqualTo(NONE)
    }

    @Test
    fun mediumCollectsMetricsAndParsesCrashes() {
        assertThat(medium.shouldCollect(METRICS)).isEqualTo(FULL)
        assertThat(medium.shouldCollect(SESSION)).isEqualTo(FULL)
        assertThat(medium.shouldCollect(CRASH_ARTIFACT)).isEqualTo(TRANSIENT)
        assertThat(medium.shouldCollect(HIGH_RES_TELEMETRY)).isEqualTo(NONE)
        assertThat(medium.shouldCollect(DEBUGGING_ARTIFACT)).isEqualTo(NONE)
        assertThat(medium.shouldCollect(LOGCAT_CAPTURE)).isEqualTo(NONE)
        assertThat(medium.shouldCollect(CONTINUOUS_LOGS)).isEqualTo(NONE)
    }

    @Test
    fun highCollectsDebuggingArtifactsButNotContinuousLogs() {
        assertThat(high.shouldCollect(METRICS)).isEqualTo(FULL)
        assertThat(high.shouldCollect(SESSION)).isEqualTo(FULL)
        assertThat(high.shouldCollect(HIGH_RES_TELEMETRY)).isEqualTo(FULL)
        assertThat(high.shouldCollect(CRASH_ARTIFACT)).isEqualTo(FULL)
        assertThat(high.shouldCollect(DEBUGGING_ARTIFACT)).isEqualTo(FULL)
        assertThat(high.shouldCollect(LOGCAT_CAPTURE)).isEqualTo(FULL)
        assertThat(high.shouldCollect(CONTINUOUS_LOGS)).isEqualTo(NONE)
    }

    @Test
    fun highPlusLogsCollectsEverything() {
        CollectedData.entries.forEach { data ->
            assertThat(highPlusLogs.shouldCollect(data), name = data.name).isEqualTo(FULL)
        }
    }

    @Test
    fun crashArtifactsAreParsedWheneverMonitoringIsActive() {
        // Not a combination any level produces, but the device must still behave sensibly.
        val debuggingOffMonitoringOn = config(debugging = OFF, logging = NORMAL, monitoring = NORMAL, sessions = OFF)
        assertThat(debuggingOffMonitoringOn.shouldCollect(CRASH_ARTIFACT)).isEqualTo(TRANSIENT)

        val bothOff = config(debugging = OFF, logging = NORMAL, monitoring = OFF, sessions = OFF)
        assertThat(bothOff.shouldCollect(CRASH_ARTIFACT)).isEqualTo(NONE)
    }

    @Test
    fun sessionsAreIndependentOfMonitoring() {
        val monitoringWithoutSessions = config(debugging = OFF, logging = OFF, monitoring = NORMAL, sessions = OFF)
        assertThat(monitoringWithoutSessions.shouldCollect(METRICS)).isEqualTo(FULL)
        assertThat(monitoringWithoutSessions.shouldCollect(SESSION)).isEqualTo(NONE)
    }

    @Test
    fun lowResolutionIsBelowThresholdAndHighIsAbove() {
        val lowResolutions = config(debugging = LOW, logging = LOW, monitoring = LOW, sessions = LOW)
        val monitoringAtLow = setOf(DEVICE_PROPERTIES, METRICS, CRASH_ARTIFACT)
        CollectedData.entries.filterNot { it in monitoringAtLow }.forEach { data ->
            assertThat(lowResolutions.shouldCollect(data), name = data.name).isEqualTo(NONE)
        }

        val highResolutions = config(debugging = HIGH, logging = HIGH, monitoring = HIGH, sessions = HIGH)
        CollectedData.entries.forEach { data ->
            assertThat(highResolutions.shouldCollect(data), name = data.name).isEqualTo(FULL)
        }
    }

    @Test
    fun dailyHeartbeatsCollectMetrics() {
        // The backend sends monitoring=low for a device entitled to daily but not hourly heartbeats.
        val dailyOnly = config(debugging = OFF, logging = OFF, monitoring = LOW, sessions = OFF)
        assertThat(dailyOnly.shouldCollect(METRICS)).isEqualTo(FULL)
        assertThat(dailyOnly.shouldCollect(CRASH_ARTIFACT)).isEqualTo(TRANSIENT)
        assertThat(dailyOnly.shouldCollect(HIGH_RES_TELEMETRY)).isEqualTo(NONE)
        assertThat(dailyOnly.shouldCollect(SESSION)).isEqualTo(NONE)
    }
}
