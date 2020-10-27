package com.memfault.bort

import android.annotation.SuppressLint
import android.os.IBinder

internal object ServiceManagerProxy {
    @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
    @Throws(IllegalArgumentException::class)
    @JvmStatic
    fun getService(name: String): IBinder? {
        try {
            return with(Class.forName("android.os.ServiceManager")) {
                this.getMethod(
                    "getService",
                    String::class.java
                ).invoke(
                    this,
                    name
                )
            } as IBinder?
        } catch (e: ReflectiveOperationException) {
            throw IllegalArgumentException(e)
        }
    }
}
