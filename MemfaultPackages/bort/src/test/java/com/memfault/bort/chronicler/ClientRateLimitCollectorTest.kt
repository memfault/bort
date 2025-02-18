package com.memfault.bort.chronicler

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isTrue
import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.diagnostics.BortErrorType.BortRateLimit
import com.memfault.bort.diagnostics.BortErrors
import com.memfault.bort.settings.ChroniclerSettings
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class ClientRateLimitCollectorTest {

    private val chroniclerSettings = object : ChroniclerSettings {
        override var marEnabled: Boolean = true
    }

    private val bortErrors: BortErrors = mockk(relaxed = true)

    private val collector = ClientRateLimitCollector(
        chroniclerSettings = chroniclerSettings,
        bortErrors = bortErrors,
    )

    @Test
    fun `don't upload mar when mar disabled`() = runTest {
        chroniclerSettings.marEnabled = false

        collector.collect(
            collectionTime = FakeCombinedTimeProvider.now(),
            internalHeartbeatReportMetrics = mapOf(
                "rate_limit_applied_system_server_anr" to JsonPrimitive(1),
            ),
        )

        coVerify { bortErrors wasNot Called }
    }

    @Test
    fun `don't upload mar when rate limit metric missing`() = runTest {
        collector.collect(
            collectionTime = FakeCombinedTimeProvider.now(),
            internalHeartbeatReportMetrics = emptyMap(),
        )

        coVerify { bortErrors wasNot Called }
    }

    @Test
    fun `upload mar when rate limit metric present`() = runTest {
        val now = FakeCombinedTimeProvider.now()
        collector.collect(
            collectionTime = now,
            internalHeartbeatReportMetrics = mapOf(
                "rate_limit_applied_system_server_anr" to JsonPrimitive(1),
            ),
        )

        val eventData = slot<Map<String, String>>()

        coVerify(exactly = 1) {
            bortErrors.add(any(), BortRateLimit, capture(eventData))
        }

        assertThat(eventData.isCaptured).isTrue()
        assertThat(eventData.captured).containsOnly("system_server_anr" to "1")
    }
}
