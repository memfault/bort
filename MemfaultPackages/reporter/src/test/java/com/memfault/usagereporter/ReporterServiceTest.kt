package com.memfault.usagereporter

import android.os.Bundle
import android.os.DropBoxManager
import android.os.RemoteException
import com.memfault.bort.shared.*
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

data class FakeDropBoxFilterSettingsProvider(
    override var includedTags: Set<String>
) : DropBoxFilterSettingsProvider

data class UnknownMessage(override val messageId: Int = Int.MAX_VALUE) : ReporterServiceMessage() {
    override fun toBundle(): Bundle = Bundle()
}

interface Replier {
    fun sendReply(serviceMessage: ServiceMessage)
}

class ReporterServiceTest {
    lateinit var messageHandler: ReporterServiceMessageHandler
    lateinit var filterSettingsProvider: FakeDropBoxFilterSettingsProvider
    lateinit var replier: Replier
    var dropBoxManager: DropBoxManager? = null

    @Before
    fun setUp() {
        mockkConstructor()
        dropBoxManager = mockk()
        replier = mockk(relaxed = true)
        filterSettingsProvider = FakeDropBoxFilterSettingsProvider(emptySet())
        messageHandler = ReporterServiceMessageHandler(
            dropBoxMessageHandler = DropBoxMessageHandler(
                getDropBoxManager = { dropBoxManager },
                filterSettingsProvider = filterSettingsProvider
            ),
            serviceMessageFromMessage = ReporterServiceMessage.Companion::fromMessage,
            getSendReply = { replier::sendReply }
        )
    }

    @Test
    fun unknownMessageRespondsWithError() {
        messageHandler.handleServiceMessage(UnknownMessage(), mockk())
        verify(exactly = 1) { replier.sendReply(ofType(ErrorResponse::class)) }
    }

    @Test
    fun unexpectedMessageRespondsWithError() {
        // Using an ErrorResponse here -- clients should not be sending that to the service
        messageHandler.handleServiceMessage(ErrorResponse("Client sending error!?"), mockk())
        verify(exactly = 1) { replier.sendReply(ofType(ErrorResponse::class)) }
    }

    @Test
    fun handlesSendReplyRemoteException() {
        every { replier.sendReply(any()) } throws RemoteException()
        // Should not throw:
        messageHandler.handleServiceMessage(SetTagFilterRequest(emptyList()), mockk())
        verify(exactly = 1) { replier.sendReply(ofType(SetTagFilterResponse::class)) }
    }

    @Test
    fun dropBoxSetTagFilter() {
        val includedTags = listOf("foo", "bar")
        messageHandler.handleServiceMessage(SetTagFilterRequest(includedTags), mockk())
        verify(exactly = 1) { replier.sendReply(ofType(SetTagFilterResponse::class)) }
        assertEquals(includedTags.toSet(), filterSettingsProvider.includedTags)
    }

    @Test
    fun dropBoxManagerUnavailable() {
        dropBoxManager = null
        messageHandler.handleServiceMessage(GetNextEntryRequest(0), mockk())
        verify(exactly = 1) { replier.sendReply(ofType(ErrorResponse::class)) }
    }

    @Test
    fun dropBoxManagerException() {
        every {
            dropBoxManager?.getNextEntry(null, any())
        } throws RemoteException()
        messageHandler.handleServiceMessage(GetNextEntryRequest(0), mockk())
        verify(exactly = 1) { replier.sendReply(ofType(ErrorResponse::class)) }
    }

    @Test
    fun dropBoxGetNextEntry() {
        filterSettingsProvider.includedTags = setOf("TEST")
        val filteredEntry = mockk<DropBoxManager.Entry> {
            every { tag } returns "FILTER_ME"
            every { timeMillis } returns 10
        }
        val testEntry = mockk<DropBoxManager.Entry> {
            every { tag } returns "TEST"
            every { timeMillis } returns 20
        }
        every {
            dropBoxManager?.getNextEntry(null, any())
        } returnsMany listOf(filteredEntry, testEntry, null)
        messageHandler.handleServiceMessage(GetNextEntryRequest(0), mockk())
        verifySequence {
            dropBoxManager?.getNextEntry(null, 0)
            dropBoxManager?.getNextEntry(null, 10)
        }
        verify(exactly = 1) { replier.sendReply(GetNextEntryResponse(testEntry)) }
    }

    @Test
    fun dropBoxGetNextEntryNull() {
        every {
            dropBoxManager?.getNextEntry(null, any())
        } returns null
        messageHandler.handleServiceMessage(GetNextEntryRequest(10), mockk())
        verifySequence {
            dropBoxManager?.getNextEntry(null, 10)
        }
        verify(exactly = 1) { replier.sendReply(GetNextEntryResponse(null)) }
    }
}
