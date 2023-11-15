package com.memfault.bort.shared

import android.content.Context
import android.os.Process
import android.util.EventLog
import android.util.Log
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.shared.LogLevel.Companion.tag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val eventLogEnabled: Boolean,
    val logToDisk: Boolean,
    val minLogcatLevel: LogLevel,
    val minStructuredLevel: LogLevel,
    val hrtEnabled: Boolean,
)

object Logger {
    private var TAG = "TAG"
    private var TAG_TEST = "TAG_TEST"
    private var settings: LoggerSettings = LoggerSettings(
        eventLogEnabled = true,
        logToDisk = false,
        minLogcatLevel = LogLevel.NONE,
        minStructuredLevel = LogLevel.NONE,
        hrtEnabled = false,
    )

    /**
     * All uses of logFile should be holding [logFileLock].
     */
    private var logFile: File? = null
    private var logFileLock = Mutex()

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

    fun initLogFile(context: Context) = CoroutineScope(Dispatchers.IO).launch {
        logFileLock.withLock {
            logFile = File(context.cacheDir, LOG_FILE_NAME)
        }
    }

    /**
     * @return true if a log file is available was was written into the temp file.
     *
     * This method renames the main log file ([LOG_FILE_NAME]) to the temp supplied file. The next write will recreate
     * the main log file.
     */
    suspend fun uploadAndDeleteLogFile(tempFile: File): Boolean = withContext(Dispatchers.IO) {
        logFileLock.withLock {
            logFile?.renameTo(tempFile) ?: false
        }
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
        if (settings.logToDisk) {
            CoroutineScope(Dispatchers.IO).launch {
                logFileLock.withLock {
                    logFile?.let { file ->
                        file.truncateLogFileIfOverSizeLimit()
                        file.logToFile(level, message, t)
                    }
                }
            }
        }
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

    private fun File.logToFile(level: LogLevel, message: String, t: Throwable? = null) {
        val timestamp = FORMATTER.format(Date())
        val process = Process.myPid()
        val thread = Process.myTid()
        val throwable = t?.apply { " / ${stackTraceToString()}" } ?: ""
        appendText("$timestamp $process-$thread ${level.tag()}/$TAG: $message$throwable\n")
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

    @JvmStatic
    fun logEvent(vararg strings: String) {
        if (settings.eventLogEnabled) {
            EventLog.writeEvent(40000000, *strings)
        }
    }

    @JvmStatic
    fun logEventBortSdkEnabled(isEnabled: Boolean) {
        if (settings.eventLogEnabled) {
            EventLog.writeEvent(40000001, if (isEnabled) 1 else 0)
        }
    }

    /**
     * If log file is over [MAX_LOG_FILE_SIZE] bytes, then remove data from the start of the file (i.e. earlier logs)
     * until the size is under [TRUNCATED_LOG_FILE_SIZE].
     *
     * The two limits are different, so that we are not constantly truncating the file on every log line once over the
     * limit.
     */
    private fun File.truncateLogFileIfOverSizeLimit() {
        val size = length()
        if (size <= MAX_LOG_FILE_SIZE) return
        // Use raw Android logging call here - i.e. don't recurse into our own logging methods.
        Log.d(TAG, "Truncating log file size=$size")
        logToFile(LogLevel.INFO, "Truncating log file size=$size")

        val copy = File(path + ".big")
        renameTo(copy)

        copy.inputStream().buffered().use { reader ->
            outputStream().buffered().use { writer ->
                reader.skip(size - TRUNCATED_LOG_FILE_SIZE)
                reader.copyTo(writer)
            }
        }
        copy.delete()
    }

    private const val LOG_FILE_NAME = "bort_logs"
    private val FORMATTER = SimpleDateFormat("yyyy/MM/dd hh:mm:ss.SSS", Locale.US)
    private const val MAX_LOG_FILE_SIZE = 7_000_000
    private const val TRUNCATED_LOG_FILE_SIZE = 5_000_000
}
