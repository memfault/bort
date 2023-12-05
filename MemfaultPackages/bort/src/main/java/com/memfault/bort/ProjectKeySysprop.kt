package com.memfault.bort

import com.memfault.bort.settings.ProjectKeyProvider
import com.memfault.bort.shared.BuildConfig
import javax.inject.Inject

class ProjectKeySysprop @Inject constructor(
    private val projectKeyProvider: ProjectKeyProvider,
    private val dumpsterClient: DumpsterClient,
) {
    suspend fun loadFromSysprop(syspropName: String = BuildConfig.PROJECT_KEY_SYSPROP) {
        if (syspropName.isNullOrEmpty()) {
            return
        }
        val projectKeyFromSysprop = dumpsterClient.getprop()?.get(syspropName)
        if (projectKeyFromSysprop.isNullOrEmpty()) {
            projectKeyProvider.reset(source = "sysprop")
            return
        }
        projectKeyProvider.setProjectKey(newKey = projectKeyFromSysprop, source = "sysprop")
    }
}
