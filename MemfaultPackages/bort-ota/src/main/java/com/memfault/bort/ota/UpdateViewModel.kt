package com.memfault.bort.ota

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memfault.bort.ota.lib.Action
import com.memfault.bort.ota.lib.State
import com.memfault.bort.ota.lib.Updater
import kotlinx.coroutines.launch

class UpdateViewModel(private val updater: Updater) : ViewModel() {
    val state = updater.updateState
    val events = updater.events

    fun badCurrentState(): State = updater.badCurrentUpdateState()

    fun checkForUpdates() {
        viewModelScope.launch { updater.perform(Action.CheckForUpdate()) }
    }

    fun downloadUpdate() {
        viewModelScope.launch { updater.perform(Action.DownloadUpdate) }
    }

    fun installUpdate() {
        viewModelScope.launch { updater.perform(Action.InstallUpdate) }
    }

    fun reboot() {
        viewModelScope.launch { updater.perform(Action.Reboot) }
    }
}
