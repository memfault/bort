package com.memfault.bort

import com.memfault.bort.settings.ProjectKeyChangeSource.SYSPROP
import com.memfault.bort.settings.ProjectKeyProvider
import javax.inject.Inject

fun interface ProjectKeySyspropName : () -> String?

class ProjectKeySysprop @Inject constructor(
    private val projectKeyProvider: ProjectKeyProvider,
    private val dumpsterClient: DumpsterClient,
    private val projectKeySyspropName: ProjectKeySyspropName,
) {
    suspend fun loadFromSysprop() {
        val syspropName = projectKeySyspropName()
        if (syspropName.isNullOrEmpty()) {
            return
        }
        val projectKeyFromSysprop = dumpsterClient.getprop()?.get(syspropName)
        if (projectKeyFromSysprop.isNullOrEmpty()) {
            projectKeyProvider.reset(source = SYSPROP)
            return
        }
        projectKeyProvider.setProjectKey(newKey = projectKeyFromSysprop, source = SYSPROP)
    }
}
