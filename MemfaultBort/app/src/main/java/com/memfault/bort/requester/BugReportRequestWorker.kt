package com.memfault.bort.requester

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.memfault.bort.Logger
import com.memfault.bort.SERVICE_MEMFAULT_DUMPSTATE_RUNNER
import java.lang.reflect.Method

internal class BugReportRequestWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : Worker(appContext, workerParameters) {

    override fun doWork(): Result {
        Logger.v("BugReportRequestWorker start")
        try {
            SystemPropertiesProxy.set("ctl.start", SERVICE_MEMFAULT_DUMPSTATE_RUNNER)
        } catch (e: IllegalArgumentException) {
            Logger.e("Failed to invoke SystemProperties.set", e)
            return Result.failure()
        }
        return Result.success()
    }
}

private object SystemPropertiesProxy {
    @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
    @Throws(IllegalArgumentException::class)
    @JvmStatic
    fun set(key: String, value: String) {
        try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val setter: Method = systemProperties.getDeclaredMethod(
                "set", String::class.java, String::class.java
            )
            setter.isAccessible = true
            setter.invoke(systemProperties, key, value)
        } catch (e: ReflectiveOperationException) {
            throw IllegalArgumentException(e)
        }
    }
}
