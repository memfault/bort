package com.memfault.bort.ota

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.lang.IllegalStateException

class UpdateViewModelFactory(
    private val appComponents: AppComponents
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        UpdateViewModel::class.java -> UpdateViewModel(appComponents.updater()) as T
        else -> throw IllegalStateException("Unknown ViewModel class $modelClass")
    }
}
