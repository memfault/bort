package com.memfault.bort.shared

import org.junit.Test

import org.junit.Assert.*

class ReporterServiceMessagesTest {
    @Test
    fun simpleMessage() {
        assertEquals(
            SimpleReporterServiceMessage(1),
            SimpleReporterServiceMessage(1)
        )
        assertNotEquals(
            SimpleReporterServiceMessage(DROPBOX_SET_TAG_FILTER_RSP),
            DropBoxSetTagFilterResponse()
        )
        assertEquals(
            DropBoxSetTagFilterResponse().toString(),
            "DropBoxSetTagFilterResponse(messageId=101)"
        )
    }
}
