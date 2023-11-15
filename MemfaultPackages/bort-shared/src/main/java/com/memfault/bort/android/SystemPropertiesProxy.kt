package com.memfault.bort.android

import android.annotation.SuppressLint
import com.memfault.bort.shared.Logger
import java.lang.reflect.Method

object SystemPropertiesProxy {
    @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
    @Throws(IllegalArgumentException::class)
    @JvmStatic
    fun set(
        key: String,
        value: String,
    ) {
        try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val setter: Method = systemProperties.getDeclaredMethod(
                "set",
                String::class.java,
                String::class.java,
            )
            setter.isAccessible = true
            setter.invoke(systemProperties, key, value)
        } catch (e: ReflectiveOperationException) {
            throw IllegalArgumentException(e)
        }
    }

    @SuppressLint("PrivateApi")
    fun get(propName: String): String? = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val getPropMethod = clazz.getMethod("get", String::class.java)
        getPropMethod.invoke(null, propName) as String?
    } catch (e: ReflectiveOperationException) {
        Logger.e("Error getting system property", e)
        null
    }

    fun setSafe(
        key: String,
        value: String,
    ) = try {
        set(key, value)
    } catch (e: IllegalArgumentException) {
        Logger.e("Failed to invoke SystemProperties.set($key, $value)", e)
    }
}
