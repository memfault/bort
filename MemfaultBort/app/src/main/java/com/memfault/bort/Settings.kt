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
    fun minLogLevel(): LogLevel
    fun bugReportNetworkConstraint(): NetworkConstraint
    fun maxUploadAttempts(): Int
    fun isBuildTypeBlacklisted(): Boolean
}
