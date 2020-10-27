package com.memfault.bort.dropbox

import android.os.DropBoxManager
import android.os.RemoteException
import com.memfault.bort.DropBoxSettings
import com.memfault.bort.ReporterClient
import com.memfault.bort.ReporterServiceConnection
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.ServiceGetter
import com.memfault.bort.TaskResult
import com.memfault.bort.shared.DropBoxGetNextEntryRequest
import com.memfault.bort.shared.DropBoxGetNextEntryResponse
import com.memfault.bort.shared.DropBoxSetTagFilterRequest
import com.memfault.bort.shared.DropBoxSetTagFilterResponse
import com.memfault.bort.shared.ErrorResponse
import com.memfault.bort.shared.VersionRequest
import com.memfault.bort.shared.VersionResponse
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val TEST_TAG = "TEST"
private const val TEST_SERVICE_VERSION = 1

fun mockEntry(timeMillis_: Long, tag_: String = TEST_TAG) = mockk<DropBoxManager.Entry> {
    every { tag } returns tag_
    every { timeMillis } returns timeMillis_
}

data class FakeLastProcessedEntryProvider(override var timeMillis: Long) : DropBoxLastProcessedEntryProvider

class DropBoxGetEntriesTaskTest {
    lateinit var mockServiceConnection: ReporterServiceConnection
    lateinit var mockServiceConnector: ReporterServiceConnector
    @RelaxedMockK
    lateinit var mockEntryProcessor: EntryProcessor
    lateinit var task: DropBoxGetEntriesTask
    lateinit var lastProcessedEntryProvider: FakeLastProcessedEntryProvider
    var serviceVersion: Int = TEST_SERVICE_VERSION

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        serviceVersion = TEST_SERVICE_VERSION
        mockServiceConnection = mockk {
            coEvery {
                sendAndReceive(ofType(DropBoxSetTagFilterRequest::class))
            } returns DropBoxSetTagFilterResponse()
            coEvery {
                sendAndReceive(ofType(VersionRequest::class))
            } returns VersionResponse(serviceVersion)
        }
        val connectBlockSlot = slot<suspend (getService: ServiceGetter<ReporterClient>) -> Any>()
        mockServiceConnector = mockk {
            coEvery {
                connect(capture(connectBlockSlot))
            } coAnswers {
                connectBlockSlot.captured({ ReporterClient(mockServiceConnection, mockk()) })
            }
        }
        lastProcessedEntryProvider = FakeLastProcessedEntryProvider(0)
        task = DropBoxGetEntriesTask(
            reporterServiceConnector = mockServiceConnector,
            lastProcessedEntryProvider = lastProcessedEntryProvider,
            entryProcessors = mapOf(TEST_TAG to mockEntryProcessor),
            settings = object : DropBoxSettings {
                override val dataSourceEnabled = true
            },
            retryDelayMillis = 0
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun mockGetNextEntryReponses(vararg entries: DropBoxManager.Entry?) {
        coEvery {
            mockServiceConnection.sendAndReceive(ofType(DropBoxGetNextEntryRequest::class))
        } returnsMany
            entries.map { DropBoxGetNextEntryResponse(it) }
    }

    @Test
    fun failsTaskUponRemoteException() {
        coEvery { mockServiceConnection.sendAndReceive(any()) } throws RemoteException("Boom!")

        val result = runBlocking {
            task.doWork()
        }
        coVerify(exactly = 1) { mockServiceConnection.sendAndReceive(any()) }
        assertEquals(TaskResult.FAILURE, result)
    }

    @Test
    fun failsTaskIfSetTagFilterFails() {
        coEvery {
            mockServiceConnection.sendAndReceive(ofType(DropBoxSetTagFilterRequest::class))
        } returns ErrorResponse("Can't do!")

        val result = runBlocking {
            task.doWork()
        }
        coVerify(exactly = 1) {
            mockServiceConnection.sendAndReceive(ofType(DropBoxSetTagFilterRequest::class))
        }
        assertEquals(TaskResult.FAILURE, result)
    }

    @Test
    fun failsTaskIfGetNextEntryFails() {
        coEvery {
            mockServiceConnection.sendAndReceive(ofType(DropBoxGetNextEntryRequest::class))
        } returns ErrorResponse("Can't do!")

        val result = runBlocking {
            task.doWork()
        }
        coVerify(exactly = 1) {
            mockServiceConnection.sendAndReceive(ofType(DropBoxGetNextEntryRequest::class))
        }
        assertEquals(TaskResult.FAILURE, result)
    }

    @Test
    fun retriesOnceMoreAfterNullEntry() {
        mockGetNextEntryReponses(null)

        val result = runBlocking {
            task.doWork()
        }

        coVerify(exactly = 2) {
            mockServiceConnection.sendAndReceive(ofType(DropBoxGetNextEntryRequest::class))
        }
        assertEquals(TaskResult.SUCCESS, result)
    }

    @Test
    fun ignoreEntryProcessorExceptions() {
        mockGetNextEntryReponses(
            mockEntry(10),
            mockEntry(20),
            null
        )
        every { mockEntryProcessor.process(any()) } throws Exception("Processing failed!")

        val result = runBlocking {
            task.doWork()
        }

        coVerify(exactly = 4) {
            mockServiceConnection.sendAndReceive(ofType(DropBoxGetNextEntryRequest::class))
        }
        coVerify(exactly = 2) {
            mockEntryProcessor.process(any())
        }
        assertEquals(TaskResult.SUCCESS, result)
        assertEquals(20, lastProcessedEntryProvider.timeMillis)
    }

    @Test
    fun ignoreEntryTagWithNoMatchingProcessor() {
        mockGetNextEntryReponses(
            mockEntry(10, tag_ = "unknown"),
            null
        )
        val result = runBlocking {
            task.doWork()
        }

        coVerify(exactly = 3) {
            mockServiceConnection.sendAndReceive(ofType(DropBoxGetNextEntryRequest::class))
        }
        coVerify(exactly = 0) {
            mockEntryProcessor.process(any())
        }
        assertEquals(TaskResult.SUCCESS, result)
        assertEquals(10, lastProcessedEntryProvider.timeMillis)
    }

    private fun runAndAssertNoop() {
        val result = runBlocking {
            task.doWork()
        }
        assertEquals(TaskResult.SUCCESS, result)

        // There was nothing to do, so it must not connect to the reporter service at all:
        coVerify(exactly = 0) {
            mockServiceConnector.connect(any())
        }
        coVerify(exactly = 0) {
            mockServiceConnection.sendAndReceive(any())
        }
    }

    @Test
    fun emptyEntryProcessorsMap() {
        task = DropBoxGetEntriesTask(
            reporterServiceConnector = mockServiceConnector,
            lastProcessedEntryProvider = lastProcessedEntryProvider,
            entryProcessors = mapOf(),
            object : DropBoxSettings {
                override val dataSourceEnabled = true
            },
        )
        runAndAssertNoop()
    }

    @Test
    fun dataSourceDisabled() {
        task = DropBoxGetEntriesTask(
            reporterServiceConnector = mockServiceConnector,
            lastProcessedEntryProvider = lastProcessedEntryProvider,
            entryProcessors = mapOf(TEST_TAG to mockEntryProcessor),
            object : DropBoxSettings {
                override val dataSourceEnabled = false
            },
        )
        runAndAssertNoop()
    }
}
