package com.memfault.bort

import com.memfault.bort.shared.BugReportRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

data class MockPendingBugReportRequestStorage(var request: BugReportRequest? = null) : PendingBugReportRequestStorage {
    override fun write(request: BugReportRequest?) {
        this.request = request
    }
    override fun read(): BugReportRequest? = request
}

class PendingBugReportRequestAccessorTest {
    val currentValue = BugReportRequest(requestId = "foo")
    val newValue = BugReportRequest(requestId = "bar")

    lateinit var storage: MockPendingBugReportRequestStorage
    lateinit var accessor: PendingBugReportRequestAccessor

    @BeforeEach
    fun setUp() {
        storage = MockPendingBugReportRequestStorage(currentValue)
        accessor = PendingBugReportRequestAccessor(storage)
    }

    @Test
    fun compareAndSwapOk() {
        assertEquals(
            Pair(true, currentValue),
            accessor.compareAndSwap(newValue) {
                it == currentValue
            }
        )
        assertEquals(newValue, accessor.get())
    }

    @Test
    fun compareAndSwapFail() {
        assertEquals(
            Pair(false, null),
            accessor.compareAndSwap(newValue) {
                it == null
            }
        )
        assertEquals(currentValue, accessor.get())
    }
}
