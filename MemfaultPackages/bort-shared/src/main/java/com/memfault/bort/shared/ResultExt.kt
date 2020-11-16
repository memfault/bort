package com.memfault.bort.shared.result

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.runCatching

/**
 * Extensions to make michaelbull's Result mostly source code compatible with Kotlin Stdlib's Result API.
 * The reason why Kotlin's Result is not used is because inline classes (on top of which Result is built)
 * are still alpha and a bit buggy. See https://youtrack.jetbrains.com/issue/KT-23338
 */

fun <V> Result.Companion.success(value: V) = Ok(value)
fun <E> Result.Companion.failure(error: E) = Err(error)

inline fun <R, T> Result<T, Throwable>.mapCatching(transform: (value: T) -> R): Result<R, Throwable> {
    return when (this) {
        is Ok<T> -> runCatching { transform(value) }
        is Err<Throwable> -> this
    }
}

inline val <V, E> Result<V, E>.isSuccess: Boolean
    get() = this is Ok

inline val <V, E> Result<V, E>.isFailure: Boolean
    get() = this is Err

typealias StdResult<V> = Result<V, Throwable>
