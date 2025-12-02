package com.memfault.bort.parsers

import com.memfault.bort.shared.consume

typealias LinePredicate = (line: String) -> Boolean

class Lines(iterable: Iterable<String>) : Iterator<String>, Iterable<String> {
    private val peekable = PeekableIterator(iterable.iterator())

    override fun hasNext() = peekable.hasNext()
    override fun next() = peekable.next()
    override fun iterator(): Iterator<String> = this

    fun peek() = peekable.peek()

    fun until(predicate: LinePredicate): Lines =
        sequence {
            for (line in this@Lines) {
                if (predicate(line)) {
                    peekable.prepend(line)
                    break
                }
                yield(line)
            }
        }.asLines()

    // inlined so that return and yield can be used in the block
    inline fun <R> use(block: (lines: Lines) -> R): R =
        block(this).also {
            this.consume()
        }
}

private fun Sequence<String>.asLines() = Lines(this.asIterable())
