package com.memfault.bort.diagnostics

import com.memfault.bort.TimezoneWithId
import com.memfault.bort.chronicler.ClientChroniclerEntry
import com.memfault.bort.clientserver.MarMetadata.ClientChroniclerMarMetadata
import com.memfault.bort.diagnostics.BortErrors.Companion.CLEANUP_AGE
import com.memfault.bort.settings.BatchMarUploads
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.AbsoluteTimeProvider
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.uploader.EnqueueUpload
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

/**
 * Stores interesting failures in Bort. These are (potentially) uploaded as Chronicler entries, and also made available
 * via [BortDiagnosticsProvider] for the SDK validation tool.
 *
 * - All errors are stored in a database.
 * - If mar batching is disabled, then they are also uploaded immediately as Chronicler entries (if enabled for the
 *   error type).
 * - If mar batching is enabled, then the [MarBatchingTask] will trigger adding a single mar entry containing all errors
 *   which weren't uploaded yet.
 *
 * Once uploaded, errors are marked as such in the database (so that they don't get uploaded again). They still remain
 * in the database, for the diagnostics provider to query (for [CLEANUP_AGE]).
 */
class BortErrors @Inject constructor(
    private val bortErrorsDb: BortErrorsDb,
    private val batchMarUploads: BatchMarUploads,
    private val enqueueUpload: EnqueueUpload,
    private val combinedTimeProvider: CombinedTimeProvider,
    private val absoluteTimeProvider: AbsoluteTimeProvider,
) {
    suspend fun add(
        type: BortErrorType,
        error: Throwable,
    ) {
        add(type, mapOf(ERROR_KEY to error.stackTraceToString().take(STACKTRACE_SIZE)))
    }

    suspend fun add(
        type: BortErrorType,
        eventData: Map<String, String>,
    ) {
        add(timestamp = absoluteTimeProvider(), type = type, eventData = eventData)
    }

    suspend fun add(
        timestamp: AbsoluteTime,
        type: BortErrorType,
        eventData: Map<String, String>,
    ) {
        val bortError = BortError(timestamp = timestamp, type = type, eventData = eventData)
        batchMarUploads().let { batchingUploads ->
            if (!batchingUploads) {
                // If not batching, then cleanup would otherwise never be called - so do it here.
                cleanup()
                // If Dev Mode, then upload each error immediately (the MarBatchingTask will never run, to batch them).
                enqueueErrorsForUpload(listOf(bortError))
            }
            bortErrorsDb.dao().insert(error = bortError, uploaded = !batchingUploads)
        }
    }

    suspend fun getAllErrors(): List<BortError> = bortErrorsDb.dao()
        .getAllBortErrorsForDiagnostics()

    suspend fun enqueueBortErrorsForUpload() {
        val errorsToUpload = bortErrorsDb.dao()
            .getErrorsForUpload()
            .filter { it.type.upload }
        if (errorsToUpload.isEmpty()) {
            return
        }
        enqueueErrorsForUpload(errorsToUpload)
        cleanup()
    }

    private suspend fun enqueueErrorsForUpload(errors: List<BortError>) {
        enqueueUpload.enqueue(
            file = null,
            metadata = ClientChroniclerMarMetadata(entries = errors.map { it.toChroniclerEntry() }),
            collectionTime = combinedTimeProvider.now(),
        )
    }

    suspend fun cleanup() {
        bortErrorsDb.dao()
            .deleteErrorsEarlierThan(timeMs = absoluteTimeProvider().minus(CLEANUP_AGE).timestamp.toEpochMilli())
    }

    companion object {
        private const val ERROR_KEY = "error"
        const val STACKTRACE_SIZE = 750
        private val CLEANUP_AGE = 1.days
    }
}

data class BortError(
    val timestamp: AbsoluteTime,
    val type: BortErrorType,
    val eventData: Map<String, String>,
)

fun BortError.toChroniclerEntry() = ClientChroniclerEntry(
    eventType = type.eventType,
    source = type.source,
    eventData = eventData,
    entryTime = timestamp,
    timezone = TimezoneWithId.deviceDefault,
)

/**
 * Ensure any new error types are handled in ProcessingLogCard.tsx and BortErrorCard.tsx.
 */
enum class BortErrorType(
    val eventType: String,
    val source: String,
    /** Upload to Chronicler? (Will still get reported via ContentProvider, if not) */
    val upload: Boolean,
) {
    // Remember that events are serialized using the eventType - don't change/remove these.
    BatteryStatsHistoryParseError(
        eventType = "AndroidBatteryStatsHistoryParseError",
        source = "android-batterystats",
        upload = true,
    ),
    BatteryStatsSummaryParseError(
        eventType = "AndroidBatteryStatsSummaryParseError",
        source = "android-batterystats",
        upload = true,
    ),
    FileCleanupError(eventType = "AndroidFileCleanupError", source = "android-filecleanup", upload = true),
    BortRateLimit(
        eventType = "AndroidDeviceCollectionRateLimitExceeded",
        source = "android-collection-rate-limits",
        upload = true,
    ),
    JobError(
        eventType = "AndroidJobError",
        source = "android-jobs",
        upload = true,
    ),

    // Should only be used when failing to deserialize errors from the database.
    UnknownError(eventType = "AndroidUnknownError", source = "android", upload = true),
}
