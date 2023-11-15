package com.memfault.bort.shared

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
            SimpleReporterServiceMessage(1),
        )
        assertNotEquals(
            SimpleReporterServiceMessage(SEND_FILE_TO_SERVER_REQ),
            SetLogLevelResponse,
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
