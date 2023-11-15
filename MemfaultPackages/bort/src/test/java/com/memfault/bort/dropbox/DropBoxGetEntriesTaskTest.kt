package com.memfault.bort.dropbox

import android.os.DropBoxManager
import android.os.RemoteException
import com.memfault.bort.TaskResult
import com.memfault.bort.metrics.CrashHandler
import com.memfault.bort.settings.DropBoxSettings
import com.memfault.bort.settings.RateLimitingSettings
import com.memfault.bort.time.boxed
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class DropBoxGetEntriesTaskTest {
    @RelaxedMockK
    private lateinit var mockEntryProcessor: EntryProcessor
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
        override val excludedTags get() = emptySet<String>()
        override val scrubTombstones: Boolean = false
        override val processImmediately: Boolean = true
        override val pollingInterval: Duration = 15.minutes
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
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
            metrics = mockk(relaxed = true),
            dropBoxManager = { dropBoxManager },
            dropBoxFilters = dropBoxFilters,
            processingMutex = DropboxProcessingMutex(),
            crashHandler = crashHandler,
        )
        retryDelay = suspend { }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun mockGetNextEntryReponses(vararg entries: DropBoxManager.Entry?): () -> Unit {
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
    fun failsTaskUponRemoteException() {
        coEvery { dropBoxManager.getNextEntry(any(), any()) } throws RemoteException("Boom!")

        val result = runBlocking {
            task.doWork()
        }
        coVerify(exactly = 1) { dropBoxManager.getNextEntry(any(), any()) }
        assertEquals(TaskResult.FAILURE, result)
    }

    @Test
    fun retriesOnceMoreAfterNullEntry() {
        mockGetNextEntryReponses(null)

        val result = runBlocking {
            task.doWork()
        }

        coVerify(exactly = 2) {
            dropBoxManager.getNextEntry(any(), any())
        }
        assertEquals(TaskResult.SUCCESS, result)
    }

    @Test
    fun refreshesCursorBeforeRetryAfterNullEntry() {
        mockGetNextEntryReponses(null)

        // Simulate that a time adjustment took place while waiting for the retry delay to pass:
        val changedTimeMillis = 12345L
        retryDelay = suspend {
            processedEntryCursorProvider.makeCursor().next(changedTimeMillis)
        }

        val result = runBlocking {
            task.doWork()
        }

        coVerify {
            dropBoxManager.getNextEntry(null, 0)
            dropBoxManager.getNextEntry(null, changedTimeMillis)
        }
        assertEquals(TaskResult.SUCCESS, result)
    }

    @Test
    fun ignoreEntryProcessorExceptions() {
        val verifyCloseCalled = mockGetNextEntryReponses(
            mockEntry(10),
            mockEntry(20),
            null,
        )
        coEvery { mockEntryProcessor.process(any()) } throws Exception("Processing failed!")

        val result = runBlocking {
            task.doWork()
        }

        coVerify(exactly = 4) {
            dropBoxManager.getNextEntry(any(), any())
        }
        coVerify(exactly = 2) {
            mockEntryProcessor.process(any())
        }
        assertEquals(TaskResult.SUCCESS, result)
        assertEquals(20, lastProcessedEntryProvider.timeMillis)
        verifyCloseCalled()
    }

    @Test
    fun ignoreEntryTagWithNoMatchingProcessor() {
        val verifyCloseCalled = mockGetNextEntryReponses(
            mockEntry(10, tag_ = "unknown"),
            null,
        )
        val result = runBlocking {
            task.doWork()
        }

        coVerify(exactly = 3) {
            dropBoxManager.getNextEntry(any(), any())
        }
        coVerify(exactly = 0) {
            mockEntryProcessor.process(any())
        }
        assertEquals(TaskResult.SUCCESS, result)
        assertEquals(10, lastProcessedEntryProvider.timeMillis)
        verifyCloseCalled()
    }

    @Test
    fun ignoreEmptyEntry() {
        val entry = mockEntry(10, tag_ = TEST_TAG)
        val verifyCloseCalled = mockGetNextEntryReponses(entry, null)
        val result = runBlocking {
            task.doWork()
        }

        coVerify(exactly = 1) {
            mockEntryProcessor.process(any())
        }
        assertEquals(TaskResult.SUCCESS, result)
        assertEquals(10, lastProcessedEntryProvider.timeMillis)
        verifyCloseCalled()
    }

    private fun runAndAssertNoop() {
        val result = runBlocking {
            task.doWork()
        }
        assertEquals(TaskResult.SUCCESS, result)

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
            metrics = mockk(relaxed = true),
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
            metrics = mockk(relaxed = true),
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
