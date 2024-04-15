package com.memfault.bort.dagger

/**
 * Injecting Set<T> for multibinding doesn't work in Kotlin, because the kotlin set is typed Set<out T>, so we get
 * a missing binding error. Always use this alias e.g. InjectSet<T> to inject multibinding values, instead.
 */
typealias InjectSet<T> = Set<@JvmSuppressWildcards T>

typealias InjectMap<K, V> = Map<@JvmSuppressWildcards K, @JvmSuppressWildcards V>
