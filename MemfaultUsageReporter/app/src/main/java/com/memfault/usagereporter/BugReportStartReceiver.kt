package com.memfault.usagereporter

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.lang.reflect.Method

private const val TAG = "MfltUsageReporter"
private const val SERVICE_MEMFAULT_DUMPSTATE_RUNNER = "memfault_dumpstate_runner"

class BugReportStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return
        when {
            intent.action != "com.memfault.intent.action.BUG_REPORT_START" -> return
        }
        Log.v(TAG, "Starting $SERVICE_MEMFAULT_DUMPSTATE_RUNNER")
        try {
            SystemPropertiesProxy.set("ctl.start", SERVICE_MEMFAULT_DUMPSTATE_RUNNER)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG,"Failed to invoke SystemProperties.set", e)
        }
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
