package com.memfault.bort.dropbox

import android.os.DropBoxManager
import android.os.RemoteException
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.TaskResult
import com.memfault.bort.metrics.CrashHandler
import com.memfault.bort.settings.DropBoxSettings
import com.memfault.bort.settings.RateLimitingSettings
import com.memfault.bort.time.boxed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class DropBoxGetEntriesTaskTest {
    private var mockEntryProcessor: EntryProcessor = mockk()
    private lateinit var task: DropBoxGetEntriesTask
    private lateinit var lastProcessedEntryProvider: FakeLastProcessedEntryProvider
    private lateinit var pendingTimeChangeProvider: DropBoxPendingTimeChangeProvider
    private lateinit var processedEntryCursorProvider: ProcessedEntryCursorProvider
    private lateinit var retryDelay: suspend () -> Unit
    private lateinit var entryProcessors: DropBoxEntryProcessors
    private lateinit var dropBoxManager: DropBoxManager
    private lateinit var crashHandler: CrashHandler
    private val dropBoxFilters = object : DropBoxFilters {
        override fun tagFilter(): List<String> = listOf(TEST_TAG)
    }

    private val mockRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 10,
        defaultPeriod = 15.minutes.boxed(),
        maxBuckets = 1,
    )

    private val mockDropboxSettings = object : DropBoxSettings {
        override val dataSourceEnabled = true
        override val anrRateLimitingSettings = mockRateLimitingSettings
        override val javaExceptionsRateLimitingSettings = mockRateLimitingSettings
        override val wtfsRateLimitingSettings = mockRateLimitingSettings
        override val wtfsTotalRateLimitingSettings = mockRateLimitingSettings
        override val kmsgsRateLimitingSettings = mockRateLimitingSettings
        override val structuredLogRateLimitingSettings = mockRateLimitingSettings
        override val tombstonesRateLimitingSettings = mockRateLimitingSettings
        override val metricReportRateLimitingSettings = mockRateLimitingSettings
        override val marFileRateLimitingSettings = mockRateLimitingSettings
        override val continuousLogFileRateLimitingSettings = mockRateLimitingSettings
        override val otherDropBoxEntryRateLimitingSettings = mockRateLimitingSettings
        override val excludedTags get() = emptySet<String>()
        override val forceEnableWtfTags: Boolean = true
        override val scrubTombstones: Boolean = false
        override val useNativeCrashTombstones: Boolean = false
        override val processImmediately: Boolean = true
        override val pollingInterval: Duration = 15.minutes
        override val otherTags: Set<String> = emptySet()
        override val ignoreCommonWtfs: Boolean = false
        override val ignoredWtfs: Set<String> = emptySet()
    }

    @Before
    fun setUp() {
        lastProcessedEntryProvider = FakeLastProcessedEntryProvider(0)
        pendingTimeChangeProvider = FakeDropBoxPendingTimeChangeProvider(false)
        processedEntryCursorProvider = ProcessedEntryCursorProvider(
            lastProcessedEntryProvider,
            pendingTimeChangeProvider,
        )
        entryProcessors = mapOfProcessors(
            TEST_TAG to mockEntryProcessor,
            TEST_TAG_TO_IGNORE to mockEntryProcessor,
        )
        dropBoxManager = mockk()
        crashHandler = mockk(relaxed = true)
        task = DropBoxGetEntriesTask(
            cursorProvider = processedEntryCursorProvider,
            entryProcessors = entryProcessors,
            settings = mockDropboxSettings,
            retryDelay = { retryDelay() },
            dropBoxManager = { dropBoxManager },
            dropBoxFilters = dropBoxFilters,
            processingMutex = DropboxProcessingMutex(),
            crashHandler = crashHandler,
        )
        retryDelay = suspend { }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun mockGetNextEntryResponses(vararg entries: DropBoxManager.Entry?): () -> Unit {
        coEvery {
            dropBoxManager.getNextEntry(any(), any())
        } returnsMany entries.toList()

        return {
            entries.forEach {
                it?.let { verify(exactly = 1) { it.close() } }
            }
        }
    }

    @Test
    fun failsTaskUponRemoteException() = runTest {
        coEvery { dropBoxManager.getNextEntry(any(), any()) } throws RemoteException("Boom!")

        val result = task.doWork()
        coVerify(exactly = 1) { dropBoxManager.getNextEntry(any(), any()) }
        assertThat(result).isEqualTo(TaskResult.FAILURE)
    }

    @Test
    fun retriesOnceMoreAfterNullEntry() = runTest {
        mockGetNextEntryResponses(null)

        val result = task.doWork()

        coVerify(exactly = 2) {
            dropBoxManager.getNextEntry(any(), any())
        }
        assertThat(result).isEqualTo(TaskResult.SUCCESS)
    }

    @Test
    fun refreshesCursorBeforeRetryAfterNullEntry() = runTest {
        mockGetNextEntryResponses(null)

        // Simulate that a time adjustment took place while waiting for the retry delay to pass:
        val changedTimeMillis = 12345L
        retryDelay = suspend {
            processedEntryCursorProvider.makeCursor().next(changedTimeMillis)
        }

        val result = task.doWork()

        coVerify {
            dropBoxManager.getNextEntry(null, 0)
            dropBoxManager.getNextEntry(null, changedTimeMillis)
        }
        assertThat(result).isEqualTo(TaskResult.SUCCESS)
    }

    @Test
    fun ignoreEntryProcessorExceptions() = runTest {
        val verifyCloseCalled = mockGetNextEntryResponses(
            mockEntry(10),
            mockEntry(20),
            null,
        )
        coEvery { mockEntryProcessor.process(any()) } throws Exception("Processing failed!")

        val result = task.doWork()

        coVerify(exactly = 4) {
            dropBoxManager.getNextEntry(any(), any())
        }
        coVerify(exactly = 2) {
            mockEntryProcessor.process(any())
        }
        assertThat(result).isEqualTo(TaskResult.SUCCESS)
        assertThat(lastProcessedEntryProvider.timeMillis).isEqualTo(20)
        verifyCloseCalled()
    }

    @Test
    fun ignoreEntryTagWithNoMatchingProcessor() = runTest {
        val verifyCloseCalled = mockGetNextEntryResponses(
            mockEntry(10, tag_ = "unknown"),
            null,
        )
        val result = task.doWork()

        coVerify(exactly = 3) {
            dropBoxManager.getNextEntry(any(), any())
        }
        coVerify(exactly = 0) {
            mockEntryProcessor.process(any())
        }
        assertThat(result).isEqualTo(TaskResult.SUCCESS)
        assertThat(lastProcessedEntryProvider.timeMillis).isEqualTo(10)
        verifyCloseCalled()
    }

    @Test
    fun ignoreEmptyEntry() = runTest {
        val entry = mockEntry(10, tag_ = TEST_TAG)
        val verifyCloseCalled = mockGetNextEntryResponses(entry, null)
        val result = task.doWork()

        coVerify(exactly = 1) {
            mockEntryProcessor.process(any())
        }
        assertThat(result).isEqualTo(TaskResult.SUCCESS)
        assertThat(lastProcessedEntryProvider.timeMillis).isEqualTo(10)
        verifyCloseCalled()
    }

    private fun runAndAssertNoop() = runTest {
        val result = task.doWork()
        assertThat(result).isEqualTo(TaskResult.SUCCESS)

        // There was nothing to do, so it must not connect to the reporter service at all:
        coVerify(exactly = 0) {
            dropBoxManager.getNextEntry(any(), any())
        }
    }

    @Test
    fun emptyEntryProcessorsMap() {
        task = DropBoxGetEntriesTask(
            cursorProvider = processedEntryCursorProvider,
            entryProcessors = mapOfProcessors(),
            settings = mockDropboxSettings,
            retryDelay = { retryDelay() },
            dropBoxManager = { dropBoxManager },
            dropBoxFilters = dropBoxFilters,
            processingMutex = DropboxProcessingMutex(),
            crashHandler = crashHandler,
        )
        runAndAssertNoop()
    }

    @Test
    fun dataSourceDisabled() {
        task = DropBoxGetEntriesTask(
            cursorProvider = processedEntryCursorProvider,
            entryProcessors = mapOfProcessors(TEST_TAG to mockEntryProcessor),
            settings = object : DropBoxSettings by mockDropboxSettings {
                override val dataSourceEnabled = false
            },
            retryDelay = { retryDelay() },
            dropBoxManager = { dropBoxManager },
            dropBoxFilters = dropBoxFilters,
            processingMutex = DropboxProcessingMutex(),
            crashHandler = crashHandler,
        )
        runAndAssertNoop()
    }

    private fun mapOfProcessors(vararg processors: Pair<String, EntryProcessor>): DropBoxEntryProcessors =
        mockk(relaxed = true) {
            every { map } returns mapOf(*processors)
        }
}
