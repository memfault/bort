package com.memfault.bort.shared

import android.os.Bundle

fun Bundle.getBooleanOrNull(key: String): Boolean? =
    if (!containsKey(key)) null
    else getBoolean(key)

fun Bundle.getLongOrNull(key: String): Long? =
    if (!containsKey(key)) null
    else getLong(key)
