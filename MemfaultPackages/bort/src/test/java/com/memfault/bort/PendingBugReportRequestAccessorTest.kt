package com.memfault.bort

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.bugreport.PendingBugReportRequestAccessor
import com.memfault.bort.bugreport.PendingBugReportRequestStorage
import com.memfault.bort.shared.BugReportRequest
import org.junit.Before
import org.junit.Test

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

    @Before
    fun setUp() {
        storage = MockPendingBugReportRequestStorage(currentValue)
        accessor = PendingBugReportRequestAccessor(storage)
    }

    @Test
    fun compareAndSwapOk() {
        assertThat(
            accessor.compareAndSwap(newValue) {
                it == currentValue
            },
        ).isEqualTo(Pair(true, currentValue))
        assertThat(accessor.get()).isEqualTo(newValue)
    }

    @Test
    fun compareAndSwapFail() {
        assertThat(

            accessor.compareAndSwap(newValue) {
                it == null
            },
        ).isEqualTo(Pair(false, null))
        assertThat(accessor.get()).isEqualTo(currentValue)
    }
}
