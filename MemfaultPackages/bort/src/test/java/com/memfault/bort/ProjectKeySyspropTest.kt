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
    private val dumpsterClient: DumpsterClient = mockk {
        coEvery { getprop() } answers { sysprops }
    }
    private val projectKeyProvider: ProjectKeyProvider = mockk(relaxed = true)
    private val projectKeySysprop = ProjectKeySysprop(projectKeyProvider, dumpsterClient)

    @Test
    fun noSyspropConfigured() = runTest {
        projectKeySysprop.loadFromSysprop(syspropName = "")
        verify(exactly = 0) { projectKeyProvider.setProjectKey(any(), any()) }
        verify(exactly = 0) { projectKeyProvider.reset(any()) }
        confirmVerified(projectKeyProvider)
    }

    @Test
    fun noSyspropValue() = runTest {
        projectKeySysprop.loadFromSysprop(syspropName = "mysysprop")
        verify(exactly = 0) { projectKeyProvider.setProjectKey(any(), any()) }
        verify(exactly = 1) { projectKeyProvider.reset(any()) }
        confirmVerified(projectKeyProvider)
    }

    @Test
    fun useValueFromSysprop() = runTest {
        sysprops["mysysprop"] = "mykey"
        projectKeySysprop.loadFromSysprop(syspropName = "mysysprop")
        verify(exactly = 1) { projectKeyProvider.setProjectKey("mykey", any()) }
        verify(exactly = 0) { projectKeyProvider.reset(any()) }
        confirmVerified(projectKeyProvider)
    }
}
