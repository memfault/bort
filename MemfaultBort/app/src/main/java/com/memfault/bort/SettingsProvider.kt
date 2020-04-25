package com.memfault.bort

import androidx.work.NetworkType

enum class NetworkConstraint(
    val networkType: NetworkType
) {
    CONNECTED(NetworkType.CONNECTED),
    UNMETERED(NetworkType.UNMETERED)
}

class SettingsProvider {
    /** The project API key MUST be set. */
    fun apiKey(): String = "" // Add your API key here!

    /** Set the base URL for the Memfault API. Not recommended to change. */
    fun baseUrl(): String = "https://files.memfault.com"

    /** The time interval, in hours, when bug reports will be created. */
    fun bugReportRequestIntervalHours(): Long = 12L

    /** The minimum log level that will be logged to logcat. */
    fun minLogLevel(): LogLevel = LogLevel.VERBOSE

    /** Optionally configure the network constraint. */
    fun bugReportNetworkConstraint() = NetworkConstraint.CONNECTED

    /** The maximum number of upload attempts before the request will fail. */
    fun maxUploadAttempts(): Int = 3
}
