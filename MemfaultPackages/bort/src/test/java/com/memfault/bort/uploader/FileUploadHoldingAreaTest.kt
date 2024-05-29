package com.memfault.bort.uploader

import com.memfault.bort.BortJson
import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.FakeSharedPreferences
import com.memfault.bort.LogcatCollectionId
import com.memfault.bort.clientserver.MarMetadata.LogcatMarMetadata
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.makeFakeSharedPreferences
import com.memfault.bort.settings.CurrentSamplingConfig
import com.memfault.bort.settings.FileUploadHoldingAreaSettings
import com.memfault.bort.settings.LogcatCollectionMode
import com.memfault.bort.settings.LogcatCollectionMode.PERIODIC
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.settings.RateLimitingSettings
import com.memfault.bort.settings.Resolution.NOT_APPLICABLE
import com.memfault.bort.settings.SamplingConfig
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.time.BoxedDuration
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.uploader.PendingFileUploadEntry.TimeSpan
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FileUploadHoldingAreaTest {
    lateinit var mockSharedPreferences: FakeSharedPreferences
    lateinit var mockEnqueueUpload: EnqueueUpload
    lateinit var fileUploadHoldingArea: FileUploadHoldingArea
    private val currentSamplingConfig = mockk<CurrentSamplingConfig> { coEvery { get() } returns SamplingConfig() }

    val trailingMarginVal = 5.seconds
    val eventOfInterestTTL = 7.seconds
    val maxStoredEventsOfInterestVal = 4
    val collectionTime = FakeCombinedTimeProvider.now()
    var logcatCollectionMode = PERIODIC

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
            override val collectionMode: LogcatCollectionMode get() = logcatCollectionMode
            override val continuousLogDumpThresholdBytes: Int get() = TODO("Not used")
            override val continuousLogDumpThresholdTime: Duration get() = TODO("Not used")
            override val continuousLogDumpWrappingTimeout: Duration get() = TODO("Not used")
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

    @Suppress("DEPRECATION")
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
                collectionTime = collectionTime,
                command = emptyList(),
                cid = LogcatCollectionId(UUID.randomUUID()),
                nextCid = LogcatCollectionId(UUID.randomUUID()),
            ),
            file = makeTempFile(),
        )

    private fun PendingFileUploadEntry.asMarMetadata() = LogcatMarMetadata(
        logFileName = file.name,
        command = payload.command,
        cid = payload.cid,
        nextCid = payload.nextCid,
        containsOops = payload.containsOops,
        collectionMode = payload.collectionMode,
    )

    @Test
    fun addEnqueueImmediately() = runTest {
        val entry = makeEntry(9.seconds, 10.seconds)
        fileUploadHoldingArea.handleEventOfInterest(10.seconds)
        fileUploadHoldingArea.add(entry)
        coVerify { mockEnqueueUpload.enqueue(entry.file, entry.asMarMetadata(), entry.payload.collectionTime) }
    }

    @Test
    fun addAndHold() {
        val entry = makeEntry(9.seconds, 10.seconds)
        fileUploadHoldingArea.add(entry)
        coVerify(exactly = 0) { mockEnqueueUpload.enqueue(any(), any(), any(), any()) }
        assertEquals(listOf(entry), fileUploadHoldingArea.readEntries())
    }

    @Test
    fun prunesOldEventTimesWhenHandingEvents() = runTest {
        fileUploadHoldingArea.handleEventOfInterest(1.seconds)
        assertEquals(listOf(1.seconds), fileUploadHoldingArea.readEventTimes())

        assertEquals(8.seconds, 1.seconds + eventOfInterestTTL)
        fileUploadHoldingArea.handleEventOfInterest(8.seconds)
        assertEquals(listOf(8.seconds), fileUploadHoldingArea.readEventTimes())
        fileUploadHoldingArea.handleEventOfInterest(9.seconds)
        assertEquals(listOf(8.seconds, 9.seconds), fileUploadHoldingArea.readEventTimes())
    }

    @Test
    fun checksTriggersAndCleansPendingUploadsWhenHandingEvents() = runTest {
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
        coVerify {
            mockEnqueueUpload.enqueue(
                expectToUploadEntry.file,
                expectToUploadEntry.asMarMetadata(),
                expectToUploadEntry.payload.collectionTime,
            )
        }
        assertEquals(false, expectToDeleteEntry.file.exists())
    }

    @Test
    fun handleBootCompletedWipesIfLinuxRebooted() = runTest {
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
        coVerify(exactly = 0) {
            mockEnqueueUpload.enqueue(
                file = expectToDeleteEntry.file,
                metadata = any(),
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
        coVerify(exactly = 1) {
            mockEnqueueUpload.enqueue(
                file = expectToStoreEntry.file,
                metadata = expectToStoreEntry.asMarMetadata(),
                collectionTime = collectionTime,
                overrideDebuggingResolution = NOT_APPLICABLE,
            )
        }
        assertEquals(listOf(expectToHoldEntry), fileUploadHoldingArea.readEntries())
    }

    @Test
    fun maxStoredEventsOfInterest() = runTest {
        val eventTimes = (0..10).map { it.seconds }
        eventTimes.forEach { duration ->
            fileUploadHoldingArea.handleEventOfInterest(duration)
        }
        assertEquals(eventTimes.takeLast(maxStoredEventsOfInterestVal), fileUploadHoldingArea.readEventTimes())
    }

    @Test
    fun deserializeLegacyUploadMetadata() {
        // json taken from before legacy upload path was removed, to verify that persisted log files can still be read
        // after that change.
        val json =
            """[{"time_span":{"start_ms":-581638,"end_ms":318362},"payload":{"file":{"token":"","md5":
                |"f1aac8411c696d59a66997f9d244cb16","name":"logcat.txt"},"hardware_version":"cutf","device_serial":
                |"CUTTLEFISHCVD02","software_version":"eng.memfau.20200430.234000","software_type":"android-build",
                |"collection_time":{"uptime_ms":318362,"elapsed_realtime_ms":318362,"linux_boot_id":
                |"9649a5cb-8e35-4041-8b84-6b8abfb3750d","boot_count":1,"timestamp":"2023-04-07T23:22:58.110Z"},
                |"command":["logcat","-b","all","-D","-d","-T","1680906178.098000000","-v","threadtime","-v","nsec",
                |"-v","printable","-v","uid","-v","UTC","-v","year","*:W"],"cid":{
                |"uuid":"798d0fa6-2cce-438f-9dcc-c5abb6cfe867"},"next_cid":{"uuid":
                |"4c0d08a3-bd2a-4dc0-ad62-4a252f4d3abe"},"contains_oops":false,"collection_mode":"periodic"},
                |"file":"/data/user/0/com.memfault.smartfridge.bort/cache/logcat900531055540371522.txt",
                |"debug_tag":"UPLOAD_LOGCAT"}]
            """.trimMargin()
        val decoded = BortJson.decodeFromString<List<PendingFileUploadEntry>>(json)
        assertEquals(
            listOf(
                PendingFileUploadEntry(
                    timeSpan = TimeSpan(
                        start = BoxedDuration((-581638).milliseconds),
                        end = BoxedDuration((318362).milliseconds),
                    ),
                    payload = LogcatFileUploadPayload(
                        collectionTime = CombinedTime(
                            uptime = BoxedDuration(318362.milliseconds),
                            elapsedRealtime = BoxedDuration(318362.milliseconds),
                            linuxBootId = "9649a5cb-8e35-4041-8b84-6b8abfb3750d",
                            bootCount = 1,
                            timestamp = Instant.parse("2023-04-07T23:22:58.110Z"),
                        ),
                        command = listOf(
                            "logcat",
                            "-b",
                            "all",
                            "-D",
                            "-d",
                            "-T",
                            "1680906178.098000000",
                            "-v",
                            "threadtime",
                            "-v",
                            "nsec",
                            "-v",
                            "printable",
                            "-v",
                            "uid",
                            "-v",
                            "UTC",
                            "-v",
                            "year",
                            "*:W",
                        ),
                        cid = LogcatCollectionId(UUID.fromString("798d0fa6-2cce-438f-9dcc-c5abb6cfe867")),
                        nextCid = LogcatCollectionId(UUID.fromString("4c0d08a3-bd2a-4dc0-ad62-4a252f4d3abe")),
                        containsOops = false,
                        collectionMode = PERIODIC,
                    ),
                    file = File("/data/user/0/com.memfault.smartfridge.bort/cache/logcat900531055540371522.txt"),
                ),
            ),
            decoded,
        )
    }
}
