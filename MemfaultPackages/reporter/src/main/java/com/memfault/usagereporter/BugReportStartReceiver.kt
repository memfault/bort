package com.memfault.usagereporter

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memfault.bort.shared.BugReportOptions
import com.memfault.bort.shared.INTENT_ACTION_BUG_REPORT_START
import com.memfault.bort.shared.Logger
import java.lang.reflect.Method

private const val SERVICE_MEMFAULT_DUMPSTATE_RUNNER = "memfault_dumpstate_runner"
private const val DUMPSTATE_MEMFAULT_MINIMAL_PROPERTY = "dumpstate.memfault.minimal"

class BugReportStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return
        when {
            intent.action != INTENT_ACTION_BUG_REPORT_START -> return
        }
        val options = BugReportOptions.fromIntent(intent)
        Logger.v("Starting $SERVICE_MEMFAULT_DUMPSTATE_RUNNER (options=$options)")
        SystemPropertiesProxy.setSafe(DUMPSTATE_MEMFAULT_MINIMAL_PROPERTY, if (options.minimal) "1" else "0")
        SystemPropertiesProxy.setSafe("ctl.start", SERVICE_MEMFAULT_DUMPSTATE_RUNNER)
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

    fun setSafe(key: String, value: String) = try {
        set(key, value)
    } catch (e: IllegalArgumentException) {
        Logger.e("Failed to invoke SystemProperties.set($key, $value)", e)
    }
}
