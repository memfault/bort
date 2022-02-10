package com.memfault.usagereporter

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memfault.bort.shared.BugReportRequest
import com.memfault.bort.shared.INTENT_ACTION_BUG_REPORT_START
import com.memfault.bort.shared.Logger
import java.lang.Exception
import java.lang.reflect.Method

private const val SERVICE_MEMFAULT_DUMPSTATE_RUNNER = "memfault_dumpstate_runner"
private const val DUMPSTATE_MEMFAULT_MINIMAL_PROPERTY = "dumpstate.memfault.minimal"
private const val DUMPSTATE_MEMFAULT_REQUEST_ID = "dumpstate.memfault.requestid"

class BugReportStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return
        when {
            intent.action != INTENT_ACTION_BUG_REPORT_START -> return
        }
        val request = try {
            BugReportRequest.fromIntent(intent)
        } catch (e: Exception) {
            Logger.e("Invalid bug report request", e)
            return
        }
        Logger.v("Starting $SERVICE_MEMFAULT_DUMPSTATE_RUNNER (options=$request)")
        SystemPropertiesProxy.setSafe(DUMPSTATE_MEMFAULT_MINIMAL_PROPERTY, if (request.options.minimal) "1" else "0")
        SystemPropertiesProxy.setSafe(DUMPSTATE_MEMFAULT_REQUEST_ID, request.requestId ?: "")
        SystemPropertiesProxy.setSafe("ctl.start", SERVICE_MEMFAULT_DUMPSTATE_RUNNER)
    }
}

object SystemPropertiesProxy {
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

    @SuppressLint("PrivateApi")
    fun get(propName: String): String? {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getPropMethod = clazz.getMethod("get", String::class.java)
            return getPropMethod.invoke(null, propName) as String?
        } catch (e: ReflectiveOperationException) {
            Logger.w("Error getting system property", e)
            return null
        }
    }

    fun setSafe(key: String, value: String) = try {
        set(key, value)
    } catch (e: IllegalArgumentException) {
        Logger.e("Failed to invoke SystemProperties.set($key, $value)", e)
    }
}
