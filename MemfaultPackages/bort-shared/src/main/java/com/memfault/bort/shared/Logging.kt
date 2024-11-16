package com.memfault.bort.shared

import android.util.Log
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.shared.LogLevel.Companion.tag
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
    TEST(6),
    ;

    companion object {
        fun fromInt(level: Int) = values().firstOrNull { it.level == level }
        fun LogLevel.tag() = when (this) {
            NONE -> "?"
            ERROR -> "E"
            WARN -> "W"
            INFO -> "I"
            DEBUG -> "D"
            VERBOSE -> "V"
            TEST -> "T"
        }
    }
}

/**
 * SDK settings used by Logger (note that we cannot reference SettingsProvider from here).
 */
data class LoggerSettings(
    val minLogcatLevel: LogLevel,
    val minStructuredLevel: LogLevel,
    val hrtEnabled: Boolean,
)

object Logger {
    private var TAG = "TAG"
    private var TAG_TEST = "TAG_TEST"
    private var settings: LoggerSettings = LoggerSettings(
        minLogcatLevel = LogLevel.NONE,
        minStructuredLevel = LogLevel.NONE,
        hrtEnabled = false,
    )

    fun initSettings(settings: LoggerSettings) {
        this.settings = settings
    }

    fun initTags(tag: String, testTag: String = "TAG_TEST") {
        TAG = tag
        TAG_TEST = testTag
    }

    fun getTag(): String = TAG

    fun updateMinLogcatLevel(level: LogLevel) {
        settings = settings.copy(minLogcatLevel = level)
    }

    @JvmStatic
    fun e(tag: String, payload: Map<String, Any>, t: Throwable? = null) = writeEvent(
        LogLevel.ERROR,
        tag,
        payload,
        t,
    )

    @JvmStatic
    fun e(message: String, t: Throwable? = null) = logcat(
        LogLevel.ERROR,
        message,
        t,
    )

    @JvmStatic
    fun w(tag: String, payload: Map<String, Any>, t: Throwable? = null) = writeEvent(
        LogLevel.WARN,
        tag,
        payload,
        t,
    )

    @JvmStatic
    fun w(message: String, t: Throwable? = null) = logcat(
        LogLevel.WARN,
        message,
        t,
    )

    @JvmStatic
    fun i(tag: String, payload: Map<String, Any>, t: Throwable? = null) = writeEvent(
        LogLevel.INFO,
        tag,
        payload,
        t,
    )

    @JvmStatic
    fun i(message: String, t: Throwable? = null) = logcat(
        LogLevel.INFO,
        message,
        t,
    )

    @JvmStatic
    fun d(tag: String, payload: Map<String, Any>, t: Throwable? = null) = writeEvent(
        LogLevel.DEBUG,
        tag,
        payload,
        t,
    )

    @JvmStatic
    fun d(message: String, t: Throwable? = null) = logcat(
        LogLevel.DEBUG,
        message,
        t,
    )

    @JvmStatic
    fun v(tag: String, payload: Map<String, Any>, t: Throwable? = null) = writeEvent(
        LogLevel.VERBOSE,
        tag,
        payload,
        t,
    )

    @JvmStatic
    fun v(message: String, t: Throwable? = null) = logcat(
        LogLevel.VERBOSE,
        message,
        t,
    )

    @JvmStatic
    fun test(tag: String, payload: Map<String, Any>, t: Throwable? = null) = writeEvent(
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
            t,
        )

    private fun logcat(level: LogLevel, message: String, t: Throwable? = null) {
        if (level > settings.minLogcatLevel) return
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

    private fun writeEvent(
        level: LogLevel,
        tag: String,
        payload: Map<String, Any>,
        t: Throwable? = null,
    ) {
        logcat(level, "$tag: $payload", t)
        if (level > settings.minStructuredLevel) return
        val jsonObject = JSONObject(payload)
        // Add any throwable stacktrace as an array, so that we can see the first few lines in the log popup.
        t?.let { jsonObject.put("throwable", JSONArray(it.stackTraceToString().lines().take(5))) }
        // Don't crash if we were passed invalid json
        try {
            writeInternalMetricEvent(tag, jsonObject.toString())
        } catch (e: JSONException) {
            writeInternalMetricEvent("error.logging.$tag", payload.toString())
        }
    }

    /**
     * Write an internal (only visible on timeline with debug mode enabled) metric event.
     */
    private fun writeInternalMetricEvent(tag: String, message: String) {
        Reporting.report().event(name = tag, countInReport = false, internal = true).add(value = message)
    }
}
