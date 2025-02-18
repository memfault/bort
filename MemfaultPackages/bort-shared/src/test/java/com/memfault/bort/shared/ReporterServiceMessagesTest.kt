package com.memfault.bort.shared

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import org.junit.Test

class ReporterServiceMessagesTest {
    @Test
    fun simpleMessage() {
        assertThat(
            SimpleReporterServiceMessage(1),
        ).isEqualTo(
            SimpleReporterServiceMessage(1),
        )
        assertThat(
            SimpleReporterServiceMessage(SEND_FILE_TO_SERVER_REQ),
        ).isNotEqualTo(
            SetLogLevelResponse,
        )
    }

    @Test
    fun errorResponseFromExceptionIncludesStackStrace() {
        ErrorResponse.fromException(Exception("Boom")).also {
            assertThat(it.error).isNotNull()
            assertThat(it.error!!.contains(this::errorResponseFromExceptionIncludesStackStrace.name)).isTrue()
        }
    }
}
