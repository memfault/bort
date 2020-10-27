package com.memfault.bort

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetpropParsingTest {

    @Test
    fun basicInput() {
        val result = parseGetpropOutput(
            """[persist.sys.timezone]: [Europe/Paris]
            |[foo]: [bar baz]
            |[empty]: []
            |
        """.trimMargin()
        )

        assertEquals(
            result,
            mapOf(
                "persist.sys.timezone" to "Europe/Paris",
                "foo" to "bar baz",
                "empty" to ""
            )
        )
    }
}
