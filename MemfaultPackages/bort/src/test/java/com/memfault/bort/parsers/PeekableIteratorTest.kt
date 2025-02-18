package com.memfault.bort.parsers

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test

class PeekableIteratorTest {
    @Test
    fun hasNext() {
        val iterator = listOf(1).peekableIterator()
        assertThat(iterator.hasNext()).isTrue()
        iterator.peek()
        assertThat(iterator.hasNext()).isTrue()
        iterator.next()
        assertThat(iterator.hasNext()).isFalse()
    }

    @Test
    fun next() {
        val iterator = listOf(1, 2).peekableIterator()
        // next() without having peeked first:
        assertThat(iterator.next()).isEqualTo(1)
        iterator.peek()
        assertThat(iterator.next()).isEqualTo(2)
        assertFailure { iterator.next() }.isInstanceOf<NoSuchElementException>()
    }

    @Test
    fun peek() {
        val iterator = listOf(1, 2).peekableIterator()
        assertThat(iterator.peek()).isEqualTo(1)
        // peek() again should give the same value
        assertThat(iterator.peek()).isEqualTo(1)
        assertThat(iterator.next()).isEqualTo(1)
        assertThat(iterator.peek()).isEqualTo(2)
        assertThat(iterator.next()).isEqualTo(2)
        assertThat(iterator.peek()).isNull()
    }

    @Test
    fun prepend() {
        val iterator = listOf(1, 2).peekableIterator()
        iterator.prepend(-1, 0)
        iterator.prepend(-2)
        assertThat(iterator.toList()).containsExactly(-2, -1, 0, 1, 2)
    }
}
