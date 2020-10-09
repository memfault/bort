package com.memfault.bort.dropbox

import android.os.DropBoxManager
import android.os.RemoteException
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.*
import com.memfault.bort.shared.*
import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.Exception

private const val TEST_TAG = "TEST"

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

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockServiceConnection = mockk {
            coEvery {
                sendAndReceive(ofType(SetTagFilterRequest::class))
            } returns SetTagFilterResponse()
        }
        val connectBlockSlot = slot<suspend (getService: ServiceGetter<ReporterServiceConnection>) -> Any>()
        mockServiceConnector = mockk {
            coEvery {
                connect(capture(connectBlockSlot))
            } coAnswers {
                connectBlockSlot.captured({ mockServiceConnection })
            }
        }
        lastProcessedEntryProvider = FakeLastProcessedEntryProvider(0)
        task = DropBoxGetEntriesTask(
            reporterServiceConnector = mockServiceConnector,
            lastProcessedEntryProvider = lastProcessedEntryProvider,
            entryProcessors = mapOf(TEST_TAG to mockEntryProcessor),
            retryDelayMillis = 0
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun mockGetNextEntryReponses(vararg entries: DropBoxManager.Entry?) {
        coEvery {
            mockServiceConnection.sendAndReceive(ofType(GetNextEntryRequest::class))
        } returnsMany
                entries.map { GetNextEntryResponse(it) }
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
            mockServiceConnection.sendAndReceive(ofType(SetTagFilterRequest::class))
        } returns ErrorResponse("Can't do!")

        val result = runBlocking {
            task.doWork()
        }
        coVerify(exactly = 1) {
            mockServiceConnection.sendAndReceive(ofType(SetTagFilterRequest::class))
        }
        assertEquals(TaskResult.FAILURE, result)
    }

    @Test
    fun failsTaskIfGetNextEntryFails() {
        coEvery {
            mockServiceConnection.sendAndReceive(ofType(GetNextEntryRequest::class))
        } returns ErrorResponse("Can't do!")

        val result = runBlocking {
            task.doWork()
        }
        coVerify(exactly = 1) {
            mockServiceConnection.sendAndReceive(ofType(GetNextEntryRequest::class))
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
            mockServiceConnection.sendAndReceive(ofType(GetNextEntryRequest::class))
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
            mockServiceConnection.sendAndReceive(ofType(GetNextEntryRequest::class))
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
            mockServiceConnection.sendAndReceive(ofType(GetNextEntryRequest::class))
        }
        coVerify(exactly = 0) {
            mockEntryProcessor.process(any())
        }
        assertEquals(TaskResult.SUCCESS, result)
        assertEquals(10, lastProcessedEntryProvider.timeMillis)
    }
}
