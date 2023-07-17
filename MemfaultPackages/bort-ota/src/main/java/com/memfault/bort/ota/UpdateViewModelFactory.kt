package com.memfault.bort.ota

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.memfault.bort.ota.lib.Updater
import java.lang.IllegalStateException
import javax.inject.Inject

class UpdateViewModelFactory @Inject constructor(
    private val updater: Updater
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        UpdateViewModel::class.java -> UpdateViewModel(updater) as T
        else -> throw IllegalStateException("Unknown ViewModel class $modelClass")
    }
}
