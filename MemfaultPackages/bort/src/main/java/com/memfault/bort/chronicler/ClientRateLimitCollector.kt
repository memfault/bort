package com.memfault.bort.chronicler

import com.memfault.bort.diagnostics.BortErrorType.BortRateLimit
import com.memfault.bort.diagnostics.BortErrors
import com.memfault.bort.metrics.RATE_LIMIT_APPLIED
import com.memfault.bort.settings.ChroniclerSettings
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.toAbsoluteTime
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import javax.inject.Inject
import kotlin.math.roundToLong

class ClientRateLimitCollector
@Inject constructor(
    private val chroniclerSettings: ChroniclerSettings,
    private val bortErrors: BortErrors,
) {
    suspend fun collect(
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
            bortErrors.add(
                collectionTime.timestamp.toAbsoluteTime(),
                BortRateLimit,
                rateLimitHits.associate {
                    it.tag to it.count.toString()
                },
            )
        }
    }

    private data class RateLimitHit(
        val key: String,
        val tag: String,
        val count: Long,
    )
}
