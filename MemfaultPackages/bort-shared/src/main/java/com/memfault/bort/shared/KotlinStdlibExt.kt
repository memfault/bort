package com.memfault.bort.shared

fun <T> Array<T>?.listify(): List<T> =
    this?.toList() ?: emptyList()

fun <T> List<T>?.listify(): List<T> =
    this ?: emptyList()

fun <T : Any> T?.listify(): List<T> =
    this?.let(::listOf) ?: emptyList()
