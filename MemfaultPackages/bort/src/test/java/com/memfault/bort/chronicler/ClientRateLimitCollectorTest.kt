package com.memfault.bort.chronicler

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.clientserver.MarMetadata.ClientChroniclerMarMetadata
import com.memfault.bort.settings.ChroniclerSettings
import com.memfault.bort.uploader.EnqueueUpload
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

class ClientRateLimitCollectorTest {

    private val chroniclerSettings = object : ChroniclerSettings {
        override var marEnabled: Boolean = true
    }

    private val enqueueUpload: EnqueueUpload = mockk {
        every { enqueue(any(), any(), any()) } returns Unit
    }

    private val collector = ClientRateLimitCollector(
        chroniclerSettings = chroniclerSettings,
        enqueueUpload = enqueueUpload,
    )

    @Test
    fun `don't upload mar when mar disabled`() {
        chroniclerSettings.marEnabled = false

        collector.collect(
            collectionTime = FakeCombinedTimeProvider.now(),
            internalHeartbeatReportMetrics = mapOf(
                "rate_limit_applied_system_server_anr" to JsonPrimitive(1),
            ),
        )

        verify(exactly = 0) { enqueueUpload.enqueue(any(), any(), any()) }
    }

    @Test
    fun `don't upload mar when rate limit metric missing`() {
        collector.collect(
            collectionTime = FakeCombinedTimeProvider.now(),
            internalHeartbeatReportMetrics = emptyMap(),
        )

        verify(exactly = 0) { enqueueUpload.enqueue(any(), any(), any()) }
    }

    @Test
    fun `upload mar when rate limit metric present`() {
        val now = FakeCombinedTimeProvider.now()
        collector.collect(
            collectionTime = now,
            internalHeartbeatReportMetrics = mapOf(
                "rate_limit_applied_system_server_anr" to JsonPrimitive(1),
            ),
        )

        val manifest = slot<ClientChroniclerMarMetadata>()

        verify(exactly = 1) {
            enqueueUpload.enqueue(any(), capture(manifest), any())
        }

        assertThat(manifest.isCaptured).isTrue()
        assertThat(manifest.captured.entries.isNotEmpty())

        val entry = manifest.captured.entries.first()
        assertThat(entry.eventType).isEqualTo("AndroidDeviceCollectionRateLimitExceeded")
        assertThat(entry.source).isEqualTo("android-collection-rate-limits")
        assertThat(entry.eventData).isEqualTo(mapOf("system_server_anr" to "1"))
    }
}
