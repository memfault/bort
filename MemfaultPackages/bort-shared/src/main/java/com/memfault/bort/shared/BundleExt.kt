package com.memfault.bort.shared

import android.os.Bundle

fun Bundle.getBooleanOrNull(key: String): Boolean? =
    if (!containsKey(key)) null
    else getBoolean(key)

fun Bundle.getByteOrNull(key: String): Byte? =
    if (!containsKey(key)) null
    else getByte(key)

fun Bundle.getIntOrNull(key: String): Int? =
    if (!containsKey(key)) null
    else getInt(key)

fun Bundle.getLongOrNull(key: String): Long? =
    if (!containsKey(key)) null
    else getLong(key)
