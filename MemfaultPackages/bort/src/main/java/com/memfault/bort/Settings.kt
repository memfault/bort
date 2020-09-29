package com.memfault.bort

import androidx.work.Constraints
import androidx.work.NetworkType
import com.memfault.bort.shared.LogLevel


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
    fun filesBaseUrl(): String
    fun ingressBaseUrl(): String
    fun appVersionName(): String
    fun appVersionCode(): Int
    fun upstreamVersionName(): String
    fun upstreamVersionCode(): Int
    fun upstreamGitSha(): String
    fun currentGitSha(): String
    fun androidBuildVersionKey(): String
    fun androidHardwareVersionKey(): String
}

fun SettingsProvider.uploadConstraints(): Constraints =
    Constraints.Builder()
        .setRequiredNetworkType(bugReportNetworkConstraint().networkType)
        .build()


interface BortEnabledProvider {
    fun setEnabled(isOptedIn: Boolean)
    fun isEnabled(): Boolean
}
