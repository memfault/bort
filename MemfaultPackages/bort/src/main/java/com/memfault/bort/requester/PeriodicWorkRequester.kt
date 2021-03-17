package com.memfault.bort.requester

import com.memfault.bort.settings.SettingsProvider

abstract class PeriodicWorkRequester {
    abstract fun startPeriodic(justBooted: Boolean = false, settingsChanged: Boolean = false)
    abstract fun cancelPeriodic()
    abstract fun restartRequired(old: SettingsProvider, new: SettingsProvider): Boolean
    fun evaluateSettingsChange(old: SettingsProvider, new: SettingsProvider) {
        if (!restartRequired(old, new)) return
        cancelPeriodic()
        startPeriodic(settingsChanged = true)
    }
}
