package com.memfault.bort

import android.content.Context
import com.memfault.bort.shared.BugReportRequest
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BugReportRequestTimeoutTaskTest {
    lateinit var context: Context
    lateinit var mockStorage: PendingBugReportRequestStorage
    lateinit var pendingBugReportRequestAccessor: PendingBugReportRequestAccessor

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        mockStorage = MockPendingBugReportRequestStorage(null)
        pendingBugReportRequestAccessor = PendingBugReportRequestAccessor(
            storage = mockStorage
        )
    }

    @Test
    fun timeout() {
        // When BugReportTimeoutTask runs and iff the requestId matches, it should clear the
        // pendingBugReportRequestAccessor and broadcast the status intent.
        val requestId = "foo"
        pendingBugReportRequestAccessor.set(
            BugReportRequest(
                requestId = requestId,
                replyReceiver = BugReportRequest.Component(
                    pkg = "com.myapp",
                    cls = "receivers.replyreceiver",
                ),
            )
        )
        BugReportRequestTimeoutTask(context, pendingBugReportRequestAccessor).doWork(requestId)
        assertNull(pendingBugReportRequestAccessor.get())
        verify(exactly = 1) {
            context.sendBroadcast(any())
        }
    }

    @Test
    fun idMismatch() {
        // When BugReportTimeoutTask runs and the requestId does NOT match, it should do nothing.
        // This scenario could happen if the pendingBugReportRequestAccessor state and running timeout job got out
        // of sync somehow.
        val requestId = "foo"
        pendingBugReportRequestAccessor.set(
            BugReportRequest(
                requestId = requestId,
            )
        )
        BugReportRequestTimeoutTask(context, pendingBugReportRequestAccessor).doWork("bar")
        assertEquals(requestId, pendingBugReportRequestAccessor.get()?.requestId)
        verify(exactly = 0) {
            context.sendBroadcast(any())
        }
    }
}
