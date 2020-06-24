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
    fun appVersionName(): String
    fun appVersionCode(): Int
    fun upstreamVersionName(): String
    fun upstreamVersionCode(): Int
    fun upstreamGitSha(): String
    fun currentGitSha(): String
}

interface BortEnabledProvider {
    fun setEnabled(isOptedIn: Boolean)
    fun isEnabled(): Boolean
}
