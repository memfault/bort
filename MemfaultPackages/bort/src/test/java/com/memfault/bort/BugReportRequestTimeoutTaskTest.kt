package com.memfault.bort

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.memfault.bort.bugreport.PendingBugReportRequestAccessor
import com.memfault.bort.bugreport.PendingBugReportRequestStorage
import com.memfault.bort.shared.BugReportRequest
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class BugReportRequestTimeoutTaskTest {
    lateinit var application: Application
    lateinit var mockStorage: PendingBugReportRequestStorage
    lateinit var pendingBugReportRequestAccessor: PendingBugReportRequestAccessor

    @Before
    fun setUp() {
        application = mockk(relaxed = true)
        mockStorage = MockPendingBugReportRequestStorage(null)
        pendingBugReportRequestAccessor = PendingBugReportRequestAccessor(
            storage = mockStorage,
        )
    }

    @Test
    fun timeout() = runTest {
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
            ),
        )
        BugReportRequestTimeoutTask(application, pendingBugReportRequestAccessor)
            .doWork(requestId)
        assertThat(pendingBugReportRequestAccessor.get()).isNull()
        verify(exactly = 1) {
            application.sendBroadcast(any())
        }
    }

    @Test
    fun idMismatch() = runTest {
        // When BugReportTimeoutTask runs and the requestId does NOT match, it should do nothing.
        // This scenario could happen if the pendingBugReportRequestAccessor state and running timeout job got out
        // of sync somehow.
        val requestId = "foo"
        pendingBugReportRequestAccessor.set(
            BugReportRequest(
                requestId = requestId,
            ),
        )
        BugReportRequestTimeoutTask(application, pendingBugReportRequestAccessor)
            .doWork("bar")
        assertThat(pendingBugReportRequestAccessor.get()?.requestId).isEqualTo(requestId)
        verify(exactly = 0) {
            application.sendBroadcast(any())
        }
    }
}
