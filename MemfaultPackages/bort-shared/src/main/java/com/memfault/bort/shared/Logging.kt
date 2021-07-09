package com.memfault.bort.shared

import android.util.EventLog
import android.util.Log
import com.memfault.bort.customevent.CustomEvent
import com.memfault.bort.customevent.CustomEvent.timestamp
import com.memfault.bort.internal.ILogger
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class LogLevel(val level: Int) {
    NONE(0),
    ERROR(1),
    WARN(2),
    INFO(3),
    DEBUG(4),
    VERBOSE(5),
    TEST(6);

    companion object {
        fun fromInt(level: Int) = values().firstOrNull { it.level == level }
    }
}

object Logger {
    @JvmStatic
    var TAG = "TAG"

    @JvmStatic
    var TAG_TEST = "TAG_TEST"

    @JvmStatic
    var eventLogEnabled: () -> Boolean = { true }

    @JvmStatic
    var minLogcatLevel: LogLevel = LogLevel.NONE

    @JvmStatic
    var minStructuredLevel: LogLevel = LogLevel.NONE

    @JvmStatic
    fun e(tag: String, payload: Map<String, Any>, t: Throwable? = null) = structuredLog(
        LogLevel.ERROR,
        tag,
        payload,
        t,
    )

    @JvmStatic
    fun e(message: String, t: Throwable? = null) = logcat(
        LogLevel.ERROR,
        message,
        t
    )

    @JvmStatic
    fun w(tag: String, payload: Map<String, Any>, t: Throwable? = null) = structuredLog(
        LogLevel.WARN,
        tag,
        payload,
        t,
    )

    @JvmStatic
    fun w(message: String, t: Throwable? = null) = logcat(
        LogLevel.WARN,
        message,
        t
    )

    @JvmStatic
    fun i(tag: String, payload: Map<String, Any>, t: Throwable? = null) = structuredLog(
        LogLevel.INFO,
        tag,
        payload,
        t,
    )

    @JvmStatic
    fun i(message: String, t: Throwable? = null) = logcat(
        LogLevel.INFO,
        message,
        t
    )

    @JvmStatic
    fun d(tag: String, payload: Map<String, Any>, t: Throwable? = null) = structuredLog(
        LogLevel.DEBUG,
        tag,
        payload,
        t,
    )

    @JvmStatic
    fun d(message: String, t: Throwable? = null) = logcat(
        LogLevel.DEBUG,
        message,
        t
    )

    @JvmStatic
    fun v(tag: String, payload: Map<String, Any>, t: Throwable? = null) = structuredLog(
        LogLevel.VERBOSE,
        tag,
        payload,
        t,
    )

    @JvmStatic
    fun v(message: String, t: Throwable? = null) = logcat(
        LogLevel.VERBOSE,
        message,
        t
    )

    @JvmStatic
    fun test(tag: String, payload: Map<String, Any>, t: Throwable? = null) = structuredLog(
        LogLevel.TEST,
        tag,
        payload,
        t,
    )

    @JvmStatic
    fun test(message: String, t: Throwable? = null) =
        logcat(
            LogLevel.TEST,
            message,
            t
        )

    private fun logcat(level: LogLevel, message: String, t: Throwable? = null) {
        if (level > minLogcatLevel) return
        when (level) {
            LogLevel.ERROR -> Log.e(TAG, message, t)
            LogLevel.WARN -> Log.w(TAG, message, t)
            LogLevel.INFO -> Log.i(TAG, message, t)
            LogLevel.DEBUG -> Log.d(TAG, message, t)
            LogLevel.VERBOSE -> Log.v(TAG, message, t)
            LogLevel.TEST -> Log.v(TAG_TEST, message, t)
            else -> return
        }
    }

    private fun structuredLog(level: LogLevel, tag: String, payload: Map<String, Any>, t: Throwable? = null) {
        logcat(level, "$tag: $payload", t)
        if (level > minStructuredLevel) return
        val jsonObject = JSONObject(payload)
        // Add any throwable stacktrace as an array, so that we can see the first few lines in the log popup.
        t?.let { jsonObject.put("throwable", JSONArray(it.stackTraceToString().lines().take(5))) }
        // Don't crash if we were passed invalid json
        try {
            writeInternalStructureLog(tag, jsonObject.toString())
        } catch (e: JSONException) {
            writeInternalStructureLog("error.logging.$tag", payload.toString())
        }
    }

    /**
     * Write an internal (only visible on timeline with debug mode enabled) structured log, using reflection
     * (we don't expose the logInternal API publicly).
     */
    private fun writeInternalStructureLog(tag: String, message: String) {
        try {
            val obtainLogger = CustomEvent::class.java.getDeclaredMethod("obtainRemoteLogger")
            obtainLogger.isAccessible = true
            val logger = obtainLogger.invoke(null) as ILogger?
            logger?.logInternal(timestamp(), tag, message)
        } catch (ex: Exception) {
            logcat(LogLevel.ERROR, "Failed to write structured log", ex)
        }
    }

    @JvmStatic
    fun logEvent(vararg strings: String) {
        if (eventLogEnabled()) {
            EventLog.writeEvent(40000000, *strings)
        }
    }

    @JvmStatic
    fun logEventBortSdkEnabled(isEnabled: Boolean) {
        if (eventLogEnabled()) {
            EventLog.writeEvent(40000001, if (isEnabled) 1 else 0)
        }
    }
}
