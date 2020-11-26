package com.memfault.bort.parsers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PeekableIteratorTest {
    @Test
    fun hasNext() {
        val iterator = listOf(1).peekableIterator()
        assertTrue(iterator.hasNext())
        iterator.peek()
        assertTrue(iterator.hasNext())
        iterator.next()
        assertFalse(iterator.hasNext())
    }

    @Test
    fun next() {
        val iterator = listOf(1, 2).peekableIterator()
        // next() without having peeked first:
        assertEquals(1, iterator.next())
        iterator.peek()
        assertEquals(2, iterator.next())
        assertThrows<java.util.NoSuchElementException> {
            iterator.next()
        }
    }

    @Test
    fun peek() {
        val iterator = listOf(1, 2).peekableIterator()
        assertEquals(1, iterator.peek())
        // peek() again should give the same value
        assertEquals(1, iterator.peek())
        assertEquals(1, iterator.next())
        assertEquals(2, iterator.peek())
        assertEquals(2, iterator.next())
        assertEquals(null, iterator.peek())
    }

    @Test
    fun prepend() {
        val iterator = listOf(1, 2).peekableIterator()
        iterator.prepend(-1, 0)
        iterator.prepend(-2)
        assertEquals(listOf(-2, -1, 0, 1, 2), iterator.toList())
    }
}
