package com.memfault.bort.uploader

import android.content.SharedPreferences
import com.memfault.bort.FileAsAbsolutePathSerializer
import com.memfault.bort.LogcatFileUploadPayload
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.getJson
import com.memfault.bort.putJson
import com.memfault.bort.time.BaseBootRelativeTime
import com.memfault.bort.time.BoxedDuration
import com.memfault.bort.time.DurationAsMillisecondsLong
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val EVENT_TIMES_KEY = "event_times"
internal const val ENTRIES_KEY = "entries"

typealias ElapsedRealtime = Duration
typealias BoxedElapsedRealtime = BoxedDuration

@Serializable
data class PendingFileUploadEntry(
    @SerialName("time_span")
    val timeSpan: TimeSpan,

    @SerialName("payload")
    val payload: LogcatFileUploadPayload,

    @SerialName("file")
    @Serializable(with = FileAsAbsolutePathSerializer::class)
    val file: File,

    @SerialName("debug_tag")
    val debugTag: String,
) {
    @Serializable
    data class TimeSpan(
        @Serializable(with = DurationAsMillisecondsLong::class)
        @SerialName("start_ms")
        val start: BoxedElapsedRealtime,

        @Serializable(with = DurationAsMillisecondsLong::class)
        @SerialName("end_ms")
        val end: BoxedElapsedRealtime,
    ) {
        companion object {
            fun from(start: ElapsedRealtime, end: ElapsedRealtime) =
                TimeSpan(BoxedElapsedRealtime(start), BoxedElapsedRealtime(end))
        }
    }
}

fun PendingFileUploadEntry.TimeSpan.contains(time: ElapsedRealtime): Boolean =
    time >= start.duration && time < end.duration

/**
 * This class contains the logic to add log files in a "holding area" when no
 * "events of interest" (i.e. traces from DropBox sources) occurred during the span of the log file.
 * If events did happen during the span of the log file, they are enqueued for upload immediately.
 * If events happen in the "trailing margin" after the span of the log file, the log file will also
 * be enqueued for uploading as soon as this class handles the event.
 */
class FileUploadHoldingArea(
    private val sharedPreferences: SharedPreferences,
    private val enqueueUpload: EnqueueUpload,
    /**
     * Function that resets/pushed out the timeout task that will cleanup
     * expired entries by calling handleTimeout().
     */
    private val resetEventTimeout: () -> Unit,
    /**
     * The period *after* the end of an entry's span, within which occurrence
     * of an event of interest will cause the entry to be uploaded.
     */
    private val getTrailingMargin: () -> Duration,
    /**
     * Time that event of interest should remain in the list of recent
     * events of interest. Should be set to the duration of the largest
     * collection interval.
     */
    private val getEventOfInterestTTL: () -> Duration,
    private val getMaxStoredEventsOfInterest: () -> Int,
) {
    private val lock = ReentrantLock()
    private var eventTimes: List<ElapsedRealtime> = readEventTimes()
    private var entries: List<PendingFileUploadEntry> = readEntries()

    internal fun readEventTimes(): List<ElapsedRealtime> =
        sharedPreferences.getJson<List<BoxedDuration>>(EVENT_TIMES_KEY)?.map(BoxedDuration::duration) ?: emptyList()

    internal fun readEntries(): List<PendingFileUploadEntry> =
        sharedPreferences.getJson<List<PendingFileUploadEntry>>(ENTRIES_KEY) ?: emptyList()

    private fun persist() {
        sharedPreferences.edit()
            .putJson(EVENT_TIMES_KEY, eventTimes.map(::BoxedDuration))
            .putJson(ENTRIES_KEY, entries)
            .apply()
    }

    fun add(entry: PendingFileUploadEntry) =
        lock.withLock {
            if (eventTimes.any(entry.timeSpan.spanWithMargin()::contains)) {
                enqueueUpload.enqueue(entry.file, entry.payload, entry.debugTag, entry.payload.collectionTime)
            } else {
                entries = entries + listOf(entry)
                persist()
            }
        }

    /**
     * Records the time of the event of interest, prunes old event times that have outlived their TTL,
     * and checks, triggers and cleans pending file uploads.
     */
    fun handleEventOfInterest(bootRelativeEventTime: BaseBootRelativeTime) =
        handleEventOfInterest(bootRelativeEventTime.elapsedRealtime.duration)

    fun handleEventOfInterest(time: ElapsedRealtime) {
        lock.withLock {
            eventTimes = (eventTimes.filter { it.eventTimeStillAliveAt(time) } + listOf(time))
                .takeLast(getMaxStoredEventsOfInterest())
            entries = entries.filter { entry ->
                entry.timeSpan.spanWithMargin().let {
                    if (it.contains(time)) false.also {
                        enqueueUpload.enqueue(
                            entry.file, entry.payload, entry.debugTag, entry.payload.collectionTime
                        )
                    } else if (it.shouldBeDeletedAt(time)) false.also {
                        entry.file.deleteSilently()
                    } else true
                }
            }
            persist()
        }
        resetEventTimeout()
    }

    private fun clearEntriesAndEventTimes() {
        entries.forEach { it.file.deleteSilently() }
        entries = emptyList()
        eventTimes = emptyList()
    }

    /**
     * Cleans up entries and resets the stored state after a Linux reboot.
     */
    fun handleLinuxReboot() =
        lock.withLock {
            clearEntriesAndEventTimes()
            persist()
        }

    /**
     * Called from worker task.
     * In case no events of interest ever happen, we need to prevent accumulating files forever.
     */
    fun handleTimeout(now: ElapsedRealtime) =
        lock.withLock {
            entries = entries.filter { entry ->
                entry.timeSpan.spanWithMargin().let {
                    if (it.shouldBeDeletedAt(now)) false.also {
                        entry.file.deleteSilently()
                    } else true
                }
            }
            persist()
        }

    fun handleChangeBortEnabled() =
        lock.withLock {
            clearEntriesAndEventTimes()
            persist()
        }

    internal fun Duration.eventTimeStillAliveAt(now: ElapsedRealtime) = this + getEventOfInterestTTL() > now

    internal fun PendingFileUploadEntry.TimeSpan.shouldBeDeletedAt(now: ElapsedRealtime) = end.duration <= now

    internal fun PendingFileUploadEntry.TimeSpan.spanWithMargin() =
        copy(end = BoxedElapsedRealtime(end.duration + getTrailingMargin()))
}
