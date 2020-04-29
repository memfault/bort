package com.memfault.bort

import androidx.work.NetworkType

enum class NetworkConstraint(
    val networkType: NetworkType
) {
    CONNECTED(NetworkType.CONNECTED),
    UNMETERED(NetworkType.UNMETERED)
}

// Default settings

private const val BUG_REPORT_REQUEST_INTERVAL_HOURS = 12L
private val MIN_LOG_LEVEL = LogLevel.VERBOSE
private val BUG_REPORT_NETWORK_CONSTRAINT = NetworkConstraint.CONNECTED
private const val MAX_UPLOAD_ATTEMPTS = 3

interface SettingsProvider {
    /** The time interval, in hours, when bug reports will be created. */
    fun bugReportRequestIntervalHours() = BUG_REPORT_REQUEST_INTERVAL_HOURS

    /** The minimum log level that will be logged to logcat. */
    fun minLogLevel(): LogLevel = MIN_LOG_LEVEL

    /** The minimum network state required for the upload task to run. */
    fun bugReportNetworkConstraint(): NetworkConstraint = BUG_REPORT_NETWORK_CONSTRAINT

    /** The maximum number of upload attempts before the request will fail. */
    fun maxUploadAttempts(): Int = MAX_UPLOAD_ATTEMPTS
}
