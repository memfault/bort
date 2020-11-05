package com.memfault.bort.shared

import java.lang.Exception
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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

    @Test
    fun errorResponseFromExceptionIncludesStackStrace() {
        ErrorResponse.fromException(Exception("Boom")).also {
            assertNotNull(it.error)
            assertTrue(it.error!!.contains(this::errorResponseFromExceptionIncludesStackStrace.name))
        }
    }
}
