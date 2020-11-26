package com.memfault.bort.parsers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

fun makeLinesRange(stop: Int) = Lines((0 until stop).map { it.toString() })

class LinesTest {
    @Test
    fun until() {
        val lines = makeLinesRange(5)
        val some = lines.until { it == "3" }
        assertEquals(listOf("0", "1", "2"), some.toList())
        assertEquals(listOf("3", "4"), lines.toList())
    }

    @Test
    fun untilConsuming() {
        val lines = makeLinesRange(5)
        val some = lines.until(consumeMatch = true) { it == "3" }
        assertEquals(listOf("0", "1", "2"), some.toList())
        assertEquals(listOf("4"), lines.toList())
    }

    @Test
    fun untilWarningExplainer() {
        // More of an explanation than a test on why one has to use `use` when using `until`
        val lines = makeLinesRange(5)
        val some = lines.until { it == "3" }

        for (line in some) break

        // Since `use` was not used, the iterator didn't advance to "3".
        assertEquals("1", lines.peek())

        some.use {}

        assertEquals("3", lines.peek())
    }

    @Test
    fun use() {
        val lines = makeLinesRange(5)
        lines.use {
            assertEquals(lines, it)
        }
    }
}
