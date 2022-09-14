package com.memfault.bort.uploader

import android.content.SharedPreferences
import android.os.SystemClock
import com.memfault.bort.FileAsAbsolutePathSerializer
import com.memfault.bort.LogcatFileUploadPayload
import com.memfault.bort.UploadHoldingArea
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.getJson
import com.memfault.bort.putJson
import com.memfault.bort.settings.CurrentSamplingConfig
import com.memfault.bort.settings.FileUploadHoldingAreaSettings
import com.memfault.bort.settings.LogcatCollectionInterval
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.settings.Resolution.NORMAL
import com.memfault.bort.settings.Resolution.NOT_APPLICABLE
import com.memfault.bort.time.BaseAbsoluteTime
import com.memfault.bort.time.BaseBootRelativeTime
import com.memfault.bort.time.BoxedDuration
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.time.DurationAsMillisecondsLong
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking
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

interface HandleEventOfInterest {
    fun handleEventOfInterest(bootRelativeEventTime: BaseBootRelativeTime)
    fun handleEventOfInterest(time: ElapsedRealtime)
    fun handleEventOfInterest(absoluteTime: BaseAbsoluteTime)
}

/**
 * This class contains the logic to add log files in a "holding area" when no
 * "events of interest" (i.e. traces from DropBox sources) occurred during the span of the log file.
 * If events did happen during the span of the log file, they are enqueued for upload immediately.
 * If events happen in the "trailing margin" after the span of the log file, the log file will also
 * be enqueued for uploading as soon as this class handles the event.
 */
@Singleton
@ContributesBinding(SingletonComponent::class)
class FileUploadHoldingArea @Inject constructor(
    @UploadHoldingArea private val sharedPreferences: SharedPreferences,
    private val enqueueUpload: EnqueueUpload,
    private val settings: FileUploadHoldingAreaSettings,
    private val logcatCollectionInterval: LogcatCollectionInterval,
    private val logcatSettings: LogcatSettings,
    private val currentSamplingConfig: CurrentSamplingConfig,
    private val combinedTimeProvider: CombinedTimeProvider,
) : HandleEventOfInterest {
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

    fun add(entry: PendingFileUploadEntry) {
        lock.withLock {
            if (eventTimes.any(entry.timeSpan.spanWithMargin()::contains)) {
                enqueueUpload.enqueue(entry.file, entry.payload, entry.debugTag, entry.payload.collectionTime)
            } else {
                entries = entries + listOf(entry)
                persist()
            }
        }
        handleTimeout(combinedTimeProvider.now().elapsedRealtime.duration)
    }

    /**
     * Records the time of the event of interest, prunes old event times that have outlived their TTL,
     * and checks, triggers and cleans pending file uploads.
     */
    override fun handleEventOfInterest(bootRelativeEventTime: BaseBootRelativeTime) =
        handleEventOfInterest(bootRelativeEventTime.elapsedRealtime.duration)

    override fun handleEventOfInterest(time: ElapsedRealtime) {
        lock.withLock {
            eventTimes = (eventTimes.filter { it.eventTimeStillAliveAt(time) } + listOf(time))
                .takeLast(settings.maxStoredEventsOfInterest)
            entries = entries.filter { entry ->
                entry.timeSpan.spanWithMargin().let {
                    if (it.contains(time)) false.also {
                        enqueueUpload.enqueue(
                            entry.file, entry.payload, entry.debugTag, entry.payload.collectionTime
                        )
                    } else if (it.shouldBeDeletedAt(time)) false.also {
                        removeEntry(entry)
                    } else true
                }
            }
            persist()
        }
        handleTimeout(combinedTimeProvider.now().elapsedRealtime.duration)
    }

    override fun handleEventOfInterest(absoluteTime: BaseAbsoluteTime) {
        val millisAgo = maxOf(0, System.currentTimeMillis() - absoluteTime.timestamp.toEpochMilli())
        val elapsedRealtime = SystemClock.elapsedRealtime() - millisAgo
        handleEventOfInterest(elapsedRealtime.milliseconds)
    }

    private fun clearEntriesAndEventTimes() {
        entries.forEach { removeEntry(it) }
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
                        removeEntry(entry)
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

    private fun removeEntry(entry: PendingFileUploadEntry) = runBlocking {
        // Only keep logs that didn't overlap an event, if (1) we are configured to store them, or, (2) we are in a
        // logging resolution which would upload them immediately.
        if (logcatSettings.storeUnsampled || currentSamplingConfig.get().loggingResolution == NORMAL) {
            // Did not overlap with an event of interest: only has a logging aspect.
            enqueueUpload.enqueue(
                file = entry.file,
                metadata = entry.payload.copy(debuggingResolution = NOT_APPLICABLE),
                debugTag = entry.debugTag,
                collectionTime = entry.payload.collectionTime,
            )
        } else {
            entry.file.deleteSilently()
        }
    }

    internal fun Duration.eventTimeStillAliveAt(now: ElapsedRealtime) = this + logcatCollectionInterval() > now

    internal fun PendingFileUploadEntry.TimeSpan.shouldBeDeletedAt(now: ElapsedRealtime) = end.duration <= now

    internal fun PendingFileUploadEntry.TimeSpan.spanWithMargin() =
        copy(end = BoxedElapsedRealtime(end.duration + settings.trailingMargin))
}
