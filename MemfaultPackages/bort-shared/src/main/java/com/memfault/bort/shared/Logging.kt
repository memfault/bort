package com.memfault.bort.shared

import android.util.EventLog
import android.util.Log
import com.memfault.bort.structuredlog.StructuredLog

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
    var minLevel: LogLevel =
        LogLevel.NONE

    @JvmStatic
    fun e(message: String) = log(
        LogLevel.ERROR,
        message
    )

    @JvmStatic
    fun w(message: String) = log(
        LogLevel.WARN,
        message
    )

    @JvmStatic
    fun i(message: String) = log(
        LogLevel.INFO,
        message
    )

    @JvmStatic
    fun d(message: String) = log(
        LogLevel.DEBUG,
        message
    )

    @JvmStatic
    fun v(message: String) = log(
        LogLevel.VERBOSE,
        message
    )

    @JvmStatic
    fun test(message: String) = log(
        LogLevel.TEST,
        message
    )

    @JvmStatic
    fun e(message: String, t: Throwable) = log(
        LogLevel.ERROR,
        message,
        t
    )

    @JvmStatic
    fun w(message: String, t: Throwable) = log(
        LogLevel.WARN,
        message,
        t
    )

    @JvmStatic
    fun i(message: String, t: Throwable) = log(
        LogLevel.INFO,
        message,
        t
    )

    @JvmStatic
    fun d(message: String, t: Throwable) = log(
        LogLevel.DEBUG,
        message,
        t
    )

    @JvmStatic
    fun v(message: String, t: Throwable) = log(
        LogLevel.VERBOSE,
        message,
        t
    )

    @JvmStatic
    fun test(message: String, t: Throwable) =
        log(
            LogLevel.TEST,
            message,
            t
        )

    @JvmStatic
    fun log(level: LogLevel, message: String) {
        if (level > minLevel) return
        when (level) {
            LogLevel.ERROR -> Log.e(TAG, message)
            LogLevel.WARN -> Log.w(TAG, message)
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.DEBUG -> Log.d(TAG, message)
            LogLevel.VERBOSE -> Log.v(TAG, message)
            LogLevel.TEST -> Log.v(TAG_TEST, message)
            else -> return
        }
    }

    @JvmStatic
    fun log(level: LogLevel, message: String, t: Throwable) {
        if (level > minLevel) return
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

    @JvmStatic
    fun structured(data: String, tag: String = TAG) {
        StructuredLog.log(tag, data)
    }
}
