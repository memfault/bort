package com.memfault.bort.parsers

class PeekableIterator<T : Any>(private var source: Iterator<T>) : Iterator<T> {
    private var peekedValues: MutableList<T> = mutableListOf()

    override fun hasNext(): Boolean =
        peekedValues.isNotEmpty() || source.hasNext()

    override fun next(): T =
        if (peekedValues.isEmpty()) source.next()
        else peekedValues.removeFirst()

    fun peek(): T? =
        if (peekedValues.isEmpty()) {
            if (!source.hasNext()) null
            else source.next().also {
                peekedValues.add(it)
            }
        } else peekedValues[0]

    fun prepend(vararg values: T) {
        peekedValues.addAll(0, values.asList())
    }

    fun toList(): List<T> = this.asSequence().toList()
}

fun <T : Any> List<T>.peekableIterator() = PeekableIterator(this.iterator())
