package com.memfault.bort

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CachedAsyncProperty<out T>(val factory: suspend () -> T) {
    private var value: CachedValue<T> = CachedValue.Absent
    private val mutex = Mutex()

    suspend fun get(): T = mutex.withLock {
        return when (val cached = value) {
            is CachedValue.Value -> cached.value
            CachedValue.Absent -> factory().also { value = CachedValue.Value(it) }
        }
    }

    fun invalidate() {
        value = CachedValue.Absent
    }

    private sealed class CachedValue<out T> {
        object Absent : CachedValue<Nothing>()
        class Value<T>(val value: T) : CachedValue<T>()
    }
}
