package com.memfault.bort.ota.lib

import android.annotation.SuppressLint
import android.content.Context
import java.lang.IllegalStateException

/**
 * Reflection-based proxy for android.os.SystemProperties
 */
object SystemPropertyProxy {
    @SuppressLint("PrivateApi")
    fun get(context: Context, property: String): String? =
        try {
            context.classLoader
                .loadClass("android.os.SystemProperties").let { klass ->
                    klass.getMethod("get", java.lang.String::class.java)
                        .invoke(klass, property) as String
                }
        } catch (ex: Exception) {
            throw IllegalStateException(
                "Cannot find the SystemProperties system api, " +
                    "this should not happen and must be fixed."
            )
        }
}
