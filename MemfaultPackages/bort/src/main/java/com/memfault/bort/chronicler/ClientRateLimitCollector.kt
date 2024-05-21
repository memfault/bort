package com.memfault.bort.chronicler

import com.memfault.bort.TimezoneWithId
import com.memfault.bort.clientserver.MarMetadata.ClientChroniclerMarMetadata
import com.memfault.bort.metrics.RATE_LIMIT_APPLIED
import com.memfault.bort.settings.ChroniclerSettings
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.toAbsoluteTime
import com.memfault.bort.uploader.EnqueueUpload
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import javax.inject.Inject
import kotlin.math.roundToLong

class ClientRateLimitCollector
@Inject constructor(
    private val chroniclerSettings: ChroniclerSettings,
    private val enqueueUpload: EnqueueUpload,
) {
    fun collect(
        collectionTime: CombinedTime,
        internalHeartbeatReportMetrics: Map<String, JsonPrimitive>,
    ) {
        if (!chroniclerSettings.marEnabled) return

        val rateLimitedKeys = internalHeartbeatReportMetrics.keys
            .filter { key -> key.startsWith(RATE_LIMIT_APPLIED) }

        if (rateLimitedKeys.isEmpty()) return

        val rateLimitHits = rateLimitedKeys
            .mapNotNull { key ->
                RateLimitHit(
                    key = key,
                    tag = key.removePrefix("${RATE_LIMIT_APPLIED}_"),
                    count = internalHeartbeatReportMetrics[key]?.doubleOrNull?.roundToLong() ?: return@mapNotNull null,
                )
            }

        if (rateLimitHits.isNotEmpty()) {
            val entry = ClientChroniclerEntry(
                eventType = "AndroidDeviceCollectionRateLimitExceeded",
                source = "android-collection-rate-limits",
                eventData = rateLimitHits.associate { it.tag to it.count.toString() },
                entryTime = collectionTime.timestamp.toAbsoluteTime(),
                timezone = TimezoneWithId.deviceDefault,
            )
            enqueueUpload.enqueue(
                file = null,
                metadata = ClientChroniclerMarMetadata(entries = listOf(entry)),
                collectionTime = collectionTime,
            )
        }
    }

    private data class RateLimitHit(
        val key: String,
        val tag: String,
        val count: Long,
    )
}
