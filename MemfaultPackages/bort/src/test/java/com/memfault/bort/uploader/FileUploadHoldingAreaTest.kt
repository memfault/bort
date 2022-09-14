package com.memfault.bort.uploader

import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.FileUploadToken
import com.memfault.bort.LogcatCollectionId
import com.memfault.bort.LogcatFileUploadPayload
import com.memfault.bort.MockSharedPreferences
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.makeFakeSharedPreferences
import com.memfault.bort.settings.CurrentSamplingConfig
import com.memfault.bort.settings.FileUploadHoldingAreaSettings
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.settings.RateLimitingSettings
import com.memfault.bort.settings.Resolution.NOT_APPLICABLE
import com.memfault.bort.settings.SamplingConfig
import com.memfault.bort.shared.LogcatFilterSpec
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FileUploadHoldingAreaTest {
    lateinit var mockSharedPreferences: MockSharedPreferences
    lateinit var mockEnqueueUpload: EnqueueUpload
    lateinit var fileUploadHoldingArea: FileUploadHoldingArea
    private val currentSamplingConfig = mockk<CurrentSamplingConfig> { coEvery { get() } returns SamplingConfig() }

    val trailingMarginVal = 5.seconds
    val eventOfInterestTTL = 7.seconds
    val maxStoredEventsOfInterestVal = 4
    val collectionTime = FakeCombinedTimeProvider.now()

    lateinit var tempFiles: MutableList<File>

    private var storeUnsampledFiles = false

    @BeforeEach
    fun setUp() {
        tempFiles = mutableListOf()
        mockSharedPreferences = makeFakeSharedPreferences()
        mockEnqueueUpload = mockk(relaxed = true)
        val settings = object : FileUploadHoldingAreaSettings {
            override val trailingMargin = trailingMarginVal
            override val maxStoredEventsOfInterest = maxStoredEventsOfInterestVal
        }
        val logcatSettings = object : LogcatSettings {
            override val dataSourceEnabled: Boolean get() = TODO("Not used")
            override val collectionInterval: Duration get() = TODO("Not used")
            override val commandTimeout: Duration get() = TODO("Not used")
            override val filterSpecs: List<LogcatFilterSpec> get() = TODO("Not used")
            override val kernelOopsDataSourceEnabled: Boolean get() = TODO("Not used")
            override val kernelOopsRateLimitingSettings: RateLimitingSettings get() = TODO("Not used")
            override val storeUnsampled: Boolean get() = storeUnsampledFiles
        }
        fileUploadHoldingArea = FileUploadHoldingArea(
            sharedPreferences = mockSharedPreferences,
            enqueueUpload = mockEnqueueUpload,
            settings = settings,
            logcatCollectionInterval = { eventOfInterestTTL },
            logcatSettings = logcatSettings,
            currentSamplingConfig = currentSamplingConfig,
            combinedTimeProvider = FakeCombinedTimeProvider,
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

        assertEquals(7.seconds, 2.seconds + trailingMarginVal)
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

        assertEquals(7.seconds, 2.seconds + trailingMarginVal)
        val expectToHoldEntry = makeEntry(7.seconds, 8.seconds)

        listOf(expectToDeleteEntry, expectToHoldEntry).forEach {
            fileUploadHoldingArea.add(it)
        }

        fileUploadHoldingArea.handleTimeout(7.seconds)
        assertEquals(false, expectToDeleteEntry.file.exists())
        verify(exactly = 0) {
            mockEnqueueUpload.enqueue(
                file = expectToDeleteEntry.file,
                metadata = any(),
                debugTag = expectToDeleteEntry.debugTag,
                collectionTime = collectionTime,
            )
        }
        assertEquals(listOf(expectToHoldEntry), fileUploadHoldingArea.readEntries())
    }

    @Test
    fun handleTimeout_storeUnsampled() {
        storeUnsampledFiles = true
        val expectToStoreEntry = makeEntry(1.seconds, 2.seconds)

        assertEquals(7.seconds, 2.seconds + trailingMarginVal)
        val expectToHoldEntry = makeEntry(7.seconds, 8.seconds)

        listOf(expectToStoreEntry, expectToHoldEntry).forEach {
            fileUploadHoldingArea.add(it)
        }

        fileUploadHoldingArea.handleTimeout(7.seconds)
        verify(exactly = 1) {
            mockEnqueueUpload.enqueue(
                file = expectToStoreEntry.file,
                metadata = expectToStoreEntry.payload.copy(debuggingResolution = NOT_APPLICABLE),
                debugTag = expectToStoreEntry.debugTag,
                collectionTime = collectionTime,
            )
        }
        assertEquals(listOf(expectToHoldEntry), fileUploadHoldingArea.readEntries())
    }

    @Test
    fun maxStoredEventsOfInterest() {
        val eventTimes = (0..10).map { it.seconds }
        eventTimes.forEach(fileUploadHoldingArea::handleEventOfInterest)
        assertEquals(eventTimes.takeLast(maxStoredEventsOfInterestVal), fileUploadHoldingArea.readEventTimes())
    }
}
