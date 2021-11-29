package com.memfault.bort.uploader

import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.FileUploadToken
import com.memfault.bort.LogcatCollectionId
import com.memfault.bort.LogcatFileUploadPayload
import com.memfault.bort.MockSharedPreferences
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.makeFakeSharedPreferences
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.seconds
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FileUploadHoldingAreaTest {
    lateinit var mockSharedPreferences: MockSharedPreferences
    lateinit var mockEnqueueUpload: EnqueueUpload
    lateinit var mockResetEventTimeout: () -> Unit
    lateinit var fileUploadHoldingArea: FileUploadHoldingArea

    val trailingMargin = 5.seconds
    val eventOfInterestTTL = 7.seconds
    val maxStoredEventsOfInterest = 4
    val collectionTime = FakeCombinedTimeProvider.now()

    lateinit var tempFiles: MutableList<File>

    @BeforeEach
    fun setUp() {
        tempFiles = mutableListOf()
        mockSharedPreferences = makeFakeSharedPreferences()
        mockEnqueueUpload = mockk(relaxed = true)
        mockResetEventTimeout = mockk(relaxed = true)
        fileUploadHoldingArea = FileUploadHoldingArea(
            sharedPreferences = mockSharedPreferences,
            enqueueUpload = mockEnqueueUpload,
            resetEventTimeout = mockResetEventTimeout,
            getTrailingMargin = { trailingMargin },
            getEventOfInterestTTL = { eventOfInterestTTL },
            getMaxStoredEventsOfInterest = { maxStoredEventsOfInterest },
        )
    }

    @AfterEach
    fun tearDown() {
        tempFiles.forEach(File::deleteSilently)
    }

    private fun makeTempFile() =
        createTempFile().also {
            tempFiles.add(it)
        }

    private fun makeSpan(start: Duration, end: Duration) =
        PendingFileUploadEntry.TimeSpan.from(start, end)

    private fun makeEntry(start: Duration, end: Duration) =
        PendingFileUploadEntry(
            timeSpan = makeSpan(start, end),
            payload = LogcatFileUploadPayload(
                file = FileUploadToken(md5 = "", name = ""),
                hardwareVersion = "",
                deviceSerial = "",
                softwareVersion = "",
                softwareType = "",
                collectionTime = collectionTime,
                command = emptyList(),
                cid = LogcatCollectionId(UUID.randomUUID()),
                nextCid = LogcatCollectionId(UUID.randomUUID()),
            ),
            debugTag = UUID.randomUUID().toString(),
            file = makeTempFile(),
        )

    @Test
    fun addEnqueueImmediately() {
        val entry = makeEntry(9.seconds, 10.seconds)
        fileUploadHoldingArea.handleEventOfInterest(10.seconds)
        fileUploadHoldingArea.add(entry)
        verify { mockEnqueueUpload.enqueue(entry.file, entry.payload, entry.debugTag, collectionTime) }
    }

    @Test
    fun addAndHold() {
        val entry = makeEntry(9.seconds, 10.seconds)
        fileUploadHoldingArea.add(entry)
        verify(exactly = 0) { mockEnqueueUpload.enqueue(any(), any(), any(), any()) }
        assertEquals(listOf(entry), fileUploadHoldingArea.readEntries())
    }

    @Test
    fun prunesOldEventTimesWhenHandingEvents() {
        fileUploadHoldingArea.handleEventOfInterest(1.seconds)
        assertEquals(listOf(1.seconds), fileUploadHoldingArea.readEventTimes())

        assertEquals(8.seconds, 1.seconds + eventOfInterestTTL)
        fileUploadHoldingArea.handleEventOfInterest(8.seconds)
        assertEquals(listOf(8.seconds), fileUploadHoldingArea.readEventTimes())
        fileUploadHoldingArea.handleEventOfInterest(9.seconds)
        assertEquals(listOf(8.seconds, 9.seconds), fileUploadHoldingArea.readEventTimes())
    }

    @Test
    fun checksTriggersAndCleansPendingUploadsWhenHandingEvents() {
        val expectToDeleteEntry = makeEntry(1.seconds, 2.seconds)

        assertEquals(7.seconds, 2.seconds + trailingMargin)
        val expectToUploadEntry = makeEntry(7.seconds, 8.seconds)
        val expectToHoldEntry = makeEntry(100.seconds, 200.seconds)
        listOf(expectToDeleteEntry, expectToUploadEntry, expectToHoldEntry).forEach {
            fileUploadHoldingArea.add(it)
        }
        assertEquals(3, fileUploadHoldingArea.readEntries().size)
        fileUploadHoldingArea.handleEventOfInterest(7.seconds)

        assertEquals(listOf(expectToHoldEntry), fileUploadHoldingArea.readEntries())
        verify {
            mockEnqueueUpload.enqueue(
                expectToUploadEntry.file, expectToUploadEntry.payload, expectToUploadEntry.debugTag, collectionTime
            )
        }
        assertEquals(false, expectToDeleteEntry.file.exists())
    }

    @Test
    fun eventTimeoutGetsResetWhenHandingEvents() {
        verify(exactly = 0) { mockResetEventTimeout() }
        fileUploadHoldingArea.handleEventOfInterest(1.seconds)
        verify(exactly = 1) { mockResetEventTimeout() }
    }

    @Test
    fun handleBootCompletedWipesIfLinuxRebooted() {
        val eventTime = 1.seconds
        fileUploadHoldingArea.handleEventOfInterest(eventTime)
        val entry = makeEntry(2.seconds, 3.seconds)
        fileUploadHoldingArea.add(entry)

        // Linux rebooted:
        fileUploadHoldingArea.handleLinuxReboot()

        // State is wiped:
        assertEquals(emptyList<PendingFileUploadEntry>(), fileUploadHoldingArea.readEntries())
        assertEquals(emptyList<Duration>(), fileUploadHoldingArea.readEventTimes())
    }

    @Test
    fun handleTimeout() {
        val expectToDeleteEntry = makeEntry(1.seconds, 2.seconds)

        assertEquals(7.seconds, 2.seconds + trailingMargin)
        val expectToHoldEntry = makeEntry(7.seconds, 8.seconds)

        listOf(expectToDeleteEntry, expectToHoldEntry).forEach {
            fileUploadHoldingArea.add(it)
        }

        fileUploadHoldingArea.handleTimeout(7.seconds)
        assertEquals(false, expectToDeleteEntry.file.exists())
        assertEquals(listOf(expectToHoldEntry), fileUploadHoldingArea.readEntries())
    }

    @Test
    fun maxStoredEventsOfInterest() {
        val eventTimes = (0..10).map(Int::seconds)
        eventTimes.forEach(fileUploadHoldingArea::handleEventOfInterest)
        assertEquals(eventTimes.takeLast(maxStoredEventsOfInterest), fileUploadHoldingArea.readEventTimes())
    }
}
