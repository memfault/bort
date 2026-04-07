package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class GnssMetricsStorageTest {

    private fun storage(initial: GnssMetricsState = GnssMetricsState.empty("boot-0")): GnssMetricsStorage {
        var stored = initial
        return object : GnssMetricsStorage {
            override var state: GnssMetricsState
                get() = stored
                set(value) {
                    stored = value
                }
        }
    }

    private fun metrics(cn0Above: Double? = null, cn0Below: Double? = null, energy: Double? = null) =
        GnssKpiMetrics(
            locationReportCount = null, locationFailurePct = null, ttffReportCount = null,
            ttffMeanSec = null, ttffStddevSec = null, positionAccuracyReportCount = null,
            positionAccuracyMeanM = null, positionAccuracyStddevM = null, cn0ReportCount = null,
            cn0Top4MeanDbHz = null, cn0Top4StddevDbHz = null, svTotalCount = null,
            svL5TotalCount = null, svUsedInFixCount = null, svL5UsedInFixCount = null,
            l5Cn0ReportCount = null, l5Cn0Top4MeanDbHz = null, l5Cn0Top4StddevDbHz = null,
            timeOnBatteryMin = null,
            cn0AboveThresholdTimeMin = cn0Above,
            cn0BelowThresholdTimeMin = cn0Below,
            energyConsumedMah = energy,
        )

    @Test
    fun `first collection — no previous baseline — reports full since-boot value`() {
        val storage = storage(GnssMetricsState.empty("boot-1"))

        val delta = storage.update(metrics(cn0Above = 10.0, cn0Below = 2.0, energy = 5.0), "boot-1")

        assertThat(delta.cn0AboveThresholdTimeMin!!).isCloseTo(10.0, delta = 0.001)
        assertThat(delta.cn0BelowThresholdTimeMin!!).isCloseTo(2.0, delta = 0.001)
        assertThat(delta.energyConsumedMah!!).isCloseTo(5.0, delta = 0.001)
    }

    @Test
    fun `same boot — reports delta between current and stored baseline`() {
        val storage = storage(
            GnssMetricsState(
                bootId = "boot-1",
                cn0AboveThresholdTimeMin = 5.0,
                cn0BelowThresholdTimeMin = 1.0,
                energyConsumedMah = 2.0,
            ),
        )

        val delta = storage.update(metrics(cn0Above = 12.0, cn0Below = 3.0, energy = 6.5), "boot-1")

        assertThat(delta.cn0AboveThresholdTimeMin!!).isCloseTo(7.0, delta = 0.001)
        assertThat(delta.cn0BelowThresholdTimeMin!!).isCloseTo(2.0, delta = 0.001)
        assertThat(delta.energyConsumedMah!!).isCloseTo(4.5, delta = 0.001)
    }

    @Test
    fun `cross-boot resets baseline — reports full since-boot value`() {
        val storage = storage(
            GnssMetricsState(
                bootId = "boot-0",
                cn0AboveThresholdTimeMin = 100.0,
                cn0BelowThresholdTimeMin = 50.0,
                energyConsumedMah = 30.0,
            ),
        )

        val delta = storage.update(metrics(cn0Above = 3.0, cn0Below = 1.0, energy = 0.5), "boot-1")

        assertThat(delta.cn0AboveThresholdTimeMin!!).isCloseTo(3.0, delta = 0.001)
        assertThat(delta.cn0BelowThresholdTimeMin!!).isCloseTo(1.0, delta = 0.001)
        assertThat(delta.energyConsumedMah!!).isCloseTo(0.5, delta = 0.001)
    }

    @Test
    fun `zero delta — metrics unchanged between heartbeats — reports zero`() {
        val storage = storage(
            GnssMetricsState(
                bootId = "boot-1",
                cn0AboveThresholdTimeMin = 8.0,
                cn0BelowThresholdTimeMin = 2.0,
                energyConsumedMah = 3.0,
            ),
        )

        val delta = storage.update(metrics(cn0Above = 8.0, cn0Below = 2.0, energy = 3.0), "boot-1")

        assertThat(delta.cn0AboveThresholdTimeMin!!).isCloseTo(0.0, delta = 0.001)
        assertThat(delta.cn0BelowThresholdTimeMin!!).isCloseTo(0.0, delta = 0.001)
        assertThat(delta.energyConsumedMah!!).isCloseTo(0.0, delta = 0.001)
    }

    @Test
    fun `null metrics — delta fields are null`() {
        val storage = storage(GnssMetricsState.empty("boot-1"))

        val delta = storage.update(metrics(cn0Above = null, cn0Below = null, energy = null), "boot-1")

        assertThat(delta.cn0AboveThresholdTimeMin).isNull()
        assertThat(delta.cn0BelowThresholdTimeMin).isNull()
        assertThat(delta.energyConsumedMah).isNull()
    }

    @Test
    fun `new state is persisted after update`() {
        val storage = storage(GnssMetricsState.empty("boot-1"))

        storage.update(metrics(cn0Above = 10.0, cn0Below = 2.0, energy = 5.0), "boot-1")

        assertThat(storage.state.bootId).isEqualTo("boot-1")
        assertThat(storage.state.cn0AboveThresholdTimeMin!!).isCloseTo(10.0, delta = 0.001)
        assertThat(storage.state.cn0BelowThresholdTimeMin!!).isCloseTo(2.0, delta = 0.001)
        assertThat(storage.state.energyConsumedMah!!).isCloseTo(5.0, delta = 0.001)
    }
}
