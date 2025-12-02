package com.memfault.bort.parsers

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.junit.Test

fun makeLinesRange(stop: Int) = Lines((0 until stop).map { it.toString() })

class LinesTest {
    @Test
    fun until() {
        val lines = makeLinesRange(5)
        val some = lines.until { it == "3" }
        assertThat(some.toList()).containsExactly("0", "1", "2")
        assertThat(lines.toList()).containsExactly("3", "4")
    }

    @Test
    fun untilWarningExplainer() {
        // More of an explanation than a test on why one has to use `use` when using `until`
        val lines = makeLinesRange(5)
        val some = lines.until { it == "3" }

        for (line in some) break

        // Since `use` was not used, the iterator didn't advance to "3".
        assertThat(lines.peek()).isEqualTo("1")

        some.use {}

        assertThat(lines.peek()).isEqualTo("3")
    }

    @Test
    fun use() {
        val lines = makeLinesRange(5)
        lines.use {
            assertThat(it).isEqualTo(lines)
        }
    }
}
