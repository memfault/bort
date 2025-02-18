package com.memfault.bort

import assertk.assertThat
import assertk.assertions.containsOnly
import org.junit.Test

class GetpropParsingTest {

    @Test
    fun basicInput() {
        val result = parseGetpropOutput(
            """[persist.sys.timezone]: [Europe/Paris]
            |[foo]: [bar baz]
            |[empty]: []
            |
            """.trimMargin(),
        )

        assertThat(result).containsOnly(
            "persist.sys.timezone" to "Europe/Paris",
            "foo" to "bar baz",
            "empty" to "",
        )
    }
}
