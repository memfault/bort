package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.DataScrubber
import com.memfault.bort.EmailScrubbingRule
import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.PackageNameAllowList
import com.memfault.bort.logcat.FakeNextLogcatCidProvider
import com.memfault.bort.logcat.KernelOopsDetector
import com.memfault.bort.logcat.NextLogcatCidProvider
import com.memfault.bort.parsers.Package
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.settings.LogcatCollectionMode
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.settings.RateLimitingSettings
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.test.util.TestTemporaryFileFactory
import com.memfault.bort.time.boxed
import com.memfault.bort.uploader.FileUploadHoldingArea
import com.memfault.bort.uploader.PendingFileUploadEntry
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ContinuousLogcatEntryProcessorTest {
    lateinit var addedFileSlot: CapturingSlot<PendingFileUploadEntry>
    lateinit var logcatCidProvider: NextLogcatCidProvider
    lateinit var mockFileUploadingArea: FileUploadHoldingArea
    lateinit var mockKernelOopsDetector: KernelOopsDetector
    lateinit var mockPackageManagerClient: PackageManagerClient
    lateinit var mockPackageNameAllowList: PackageNameAllowList
    lateinit var processor: ContinuousLogcatEntryProcessor
    private var logcatDataSourceEnabled: Boolean = true

    @BeforeEach
    fun setup() {
        addedFileSlot = slot()
        mockFileUploadingArea = mockk {
            coEvery { add(capture(addedFileSlot)) } returns Unit
        }

        mockPackageManagerClient = mockk {
            coEvery { getPackageManagerReport(null) } returns PackageManagerReport(
                listOf(
                    Package(id = "android", userId = 1000),
                    Package(id = "com.memfault.bort", userId = 9008),
                    Package(id = "org.smartcompany.smartcupholder", userId = 9020),
                )
            )
        }

        mockPackageNameAllowList = mockk {
            every { contains(any()) } answers {
                firstArg() in setOf("android", "com.memfault.bort")
            }
        }

        mockKernelOopsDetector = mockk(relaxed = true)

        logcatCidProvider = FakeNextLogcatCidProvider.incrementing()

        logcatDataSourceEnabled = true

        val logcatSettings = object : LogcatSettings {
            override val dataSourceEnabled: Boolean get() = logcatDataSourceEnabled
            override val collectionInterval: Duration = 1.seconds
            override val commandTimeout: Duration = 1.seconds
            override val filterSpecs: List<LogcatFilterSpec> = listOf()
            override val kernelOopsDataSourceEnabled: Boolean = true
            override val kernelOopsRateLimitingSettings: RateLimitingSettings =
                RateLimitingSettings(1, 1.seconds.boxed(), 1)
            override val storeUnsampled: Boolean = false
            override val collectionMode: LogcatCollectionMode = LogcatCollectionMode.CONTINUOUS
            override val continuousLogDumpThresholdBytes: Int = 128 * 1024
            override val continuousLogDumpThresholdTime: Duration = 30.minutes
            override val continuousLogDumpWrappingTimeout: Duration = 30.minutes
        }

        processor = ContinuousLogcatEntryProcessor(
            logcatSettings = logcatSettings,
            dataScrubber = DataScrubber(listOf(EmailScrubbingRule)),
            packageManagerClient = mockPackageManagerClient,
            packageNameAllowList = mockPackageNameAllowList,
            temporaryFileFactory = TestTemporaryFileFactory,
            nextLogcatCidProvider = logcatCidProvider,
            combinedTimeProvider = FakeCombinedTimeProvider,
            fileUploadingArea = mockFileUploadingArea,
            kernelOopsDetector = { mockKernelOopsDetector },
            tokenBucketStore = mockk {
                every { takeSimple(any(), any(), any()) } returns true
            },
        )
    }

    @Test
    fun `happy path`() = withProcessedEntry {
        verify { mockKernelOopsDetector.process(any()) }
        verify(exactly = 1) { mockKernelOopsDetector.finish(any()) }
        verify(exactly = 1) { mockFileUploadingArea.add(any()) }
        assertTrue(addedFileSlot.isCaptured)

        assertEquals(
            expectedScrubbedLogcat,
            addedFileSlot.captured.file.readText()
        )
    }

    @Test
    fun `cid rotates`() = repeat(5) {
        withProcessedEntry {
            assertEquals(java.util.UUID(0, it.toLong() + 2), logcatCidProvider.cid.uuid)
        }
    }

    @Test
    fun `span elapsed calculation`() = withProcessedEntry {
        assertTrue(addedFileSlot.isCaptured)

        val start = addedFileSlot.captured.timeSpan.start
        val end = addedFileSlot.captured.timeSpan.end

        val durationSpan = end.duration - start.duration
        assertEquals(0, durationSpan.inWholeHours)
        assertEquals(2, durationSpan.inWholeMinutes)
        assertEquals(121, durationSpan.inWholeSeconds)
    }

    @Test
    fun `disabled data source`() {
        logcatDataSourceEnabled = false
        withProcessedEntry {
            verify(exactly = 0) { mockFileUploadingArea.add(any()) }
        }
    }

    private fun withProcessedEntry(text: String = sampleLogcat, block: DropBoxManager.Entry.() -> Unit) {
        runBlocking {
            val entry = mockEntry(text = text)
            processor.process(entry)
            block(entry)
        }
    }

    private val sampleLogcat = """
          --------- beginning of main
          2021-01-18 12:33:17.718860224 +0000  root     0 I chatty  : uid=0(root) logd identical 11 lines
          --------- beginning of kernel
          2021-01-18 12:34:02.168273087 +0000  9008  9009 I ServiceManager: Waiting for service AtCmdFwd...
          --------- switch to main
          2021-01-18 12:35:19.022471886 +0000  root     0 E foo  : This should be scrubbed PII: someone@mflt.io

    """.trimIndent()

    private val expectedScrubbedLogcat = """
          --------- beginning of main
          2021-01-18 12:33:17.718860224 +0000  root     0 I chatty  : uid=0(root) logd identical 11 lines
          --------- beginning of kernel
          2021-01-18 12:34:02.168273087 +0000  9008  9009 I ServiceManager: Waiting for service AtCmdFwd...
          --------- switch to main
          2021-01-18 12:35:19.022471886 +0000  root     0 E foo  : This should be scrubbed PII: {{EMAIL}}

    """.trimIndent()
}
