package com.memfault.bort

import com.memfault.bort.settings.ProjectKeyProvider
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProjectKeySyspropTest {
    private val sysprops = mutableMapOf<String, String>()
    private var syspropName = ""
    private val dumpsterClient: DumpsterClient = mockk {
        coEvery { getprop() } answers { sysprops }
    }
    private val projectKeyProvider: ProjectKeyProvider = mockk(relaxed = true)
    private val projectKeySysprop = ProjectKeySysprop(projectKeyProvider, dumpsterClient, { syspropName })

    @Test
    fun noSyspropConfigured() = runTest {
        syspropName = ""
        projectKeySysprop.loadFromSysprop()
        verify(exactly = 0) { projectKeyProvider.setProjectKey(any(), any()) }
        verify(exactly = 0) { projectKeyProvider.reset(any()) }
        confirmVerified(projectKeyProvider)
    }

    @Test
    fun noSyspropValue() = runTest {
        syspropName = "mysysprop"
        projectKeySysprop.loadFromSysprop()
        verify(exactly = 0) { projectKeyProvider.setProjectKey(any(), any()) }
        verify(exactly = 1) { projectKeyProvider.reset(any()) }
        confirmVerified(projectKeyProvider)
    }

    @Test
    fun useValueFromSysprop() = runTest {
        syspropName = "mysysprop"
        sysprops["mysysprop"] = "mykey"
        projectKeySysprop.loadFromSysprop()
        verify(exactly = 1) { projectKeyProvider.setProjectKey("mykey", any()) }
        verify(exactly = 0) { projectKeyProvider.reset(any()) }
        confirmVerified(projectKeyProvider)
    }
}
