package com.memfault.usagereporter

import android.os.UserHandle

val USER_CURRENT = -2

fun UserHandle(handle: Int): UserHandle {
    try {
        return with(Class.forName("android.os.UserHandle")) {
            getConstructor(
                Int::class.java
            ).newInstance(
                handle
            )
        } as UserHandle
    } catch (e: ReflectiveOperationException) {
        throw IllegalArgumentException(e)
    }
}
