package com.memfault.bort

import androidx.work.NetworkType

enum class NetworkConstraint(
    val networkType: NetworkType
) {
    CONNECTED(NetworkType.CONNECTED),
    UNMETERED(NetworkType.UNMETERED)
}

interface SettingsProvider {
    fun bugReportRequestIntervalHours(): Long
    fun firstBugReportDelayAfterBootMinutes(): Long
    fun minLogLevel(): LogLevel
    fun bugReportNetworkConstraint(): NetworkConstraint
    fun maxUploadAttempts(): Int
    fun isRuntimeEnableRequired(): Boolean
    fun projectKey(): String
    fun baseUrl(): String
}

interface BortEnabledProvider {
    fun setEnabled(isOptedIn: Boolean)
    fun isEnabled(): Boolean
}
