package com.memfault.bort.settings

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.memfault.bort.ProjectKeySyspropName
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.clientserver.MarFileHoldingArea
import com.memfault.bort.settings.ProjectKeyChangeSource.BROADCAST
import com.memfault.bort.settings.ProjectKeyChangeSource.SYSPROP
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class ProjectKeyProviderTest {
    private var syspropName = ""
    private var allowKeyChange = false
    private val preferenceProvider = object : ProjectKeyOverridePreferenceProvider {
        var storedKey: String? = null

        override fun getValue(): String = storedKey ?: BUILT_IN_KEY

        override fun setValue(newValue: String) {
            storedKey = newValue
        }

        override fun remove() {
            storedKey = null
        }
    }
    private val marFileHoldingArea: MarFileHoldingArea = mockk(relaxed = true)
    private val temporaryFileFactory: TemporaryFileFactory = mockk(relaxed = true)
    private val projectKeySyspropName = ProjectKeySyspropName { syspropName }
    private val builtInProjectKey = BuiltInProjectKey { BUILT_IN_KEY }
    private val allowProjectKeyChange = AllowProjectKeyChange { allowKeyChange }
    private val projectKeyProvider = ProjectKeyProvider(
        preferenceProvider = preferenceProvider,
        marFileHoldingArea = { marFileHoldingArea },
        temporaryFileFactory = temporaryFileFactory,
        projectKeySyspropName = projectKeySyspropName,
        builtInProjectKey = builtInProjectKey,
        allowProjectKeyChange = allowProjectKeyChange,
    )

    companion object {
        private const val BUILT_IN_KEY = "xxyyzz"
        private const val NEW_KEY = "newKey"
        private const val SYSPROP_NAME = "a.b.c"
    }

    @Test
    fun projectKeyUpdatedIfAllowed_sysprop() {
        allowKeyChange = true
        syspropName = SYSPROP_NAME
        projectKeyProvider.setProjectKey(NEW_KEY, SYSPROP)
        assertThat(preferenceProvider.storedKey).isEqualTo(NEW_KEY)
        verify { marFileHoldingArea.deleteAllFiles() }
    }

    @Test
    fun projectKeyUpdatedIfAllowed_broadcast() {
        allowKeyChange = true
        syspropName = ""
        projectKeyProvider.setProjectKey(NEW_KEY, BROADCAST)
        assertThat(preferenceProvider.storedKey).isEqualTo(NEW_KEY)
        verify { marFileHoldingArea.deleteAllFiles() }
    }

    @Test
    fun keyChangeNotAllowed_sysprop() {
        allowKeyChange = false
        syspropName = SYSPROP_NAME
        projectKeyProvider.setProjectKey(NEW_KEY, SYSPROP)
        assertThat(preferenceProvider.storedKey).isNull()
    }

    @Test
    fun keyChangeNotAllowed_broadcast() {
        allowKeyChange = false
        syspropName = ""
        projectKeyProvider.setProjectKey(NEW_KEY, BROADCAST)
        assertThat(preferenceProvider.storedKey).isNull()
    }

    @Test
    fun keyChangeNotAllowed_broadcastWhenSyspropNameConfigured() {
        allowKeyChange = true
        syspropName = SYSPROP_NAME
        projectKeyProvider.setProjectKey(NEW_KEY, BROADCAST)
        assertThat(preferenceProvider.storedKey).isNull()
    }

    @Test
    fun reset_sysprop() {
        syspropName = SYSPROP_NAME
        preferenceProvider.storedKey = NEW_KEY
        projectKeyProvider.reset(SYSPROP)
        assertThat(preferenceProvider.storedKey).isNull()
    }

    @Test
    fun reset_broadcast() {
        syspropName = ""
        preferenceProvider.storedKey = NEW_KEY
        projectKeyProvider.reset(BROADCAST)
        assertThat(preferenceProvider.storedKey).isNull()
    }

    @Test
    fun resetNotAllowed_broadcastWithSyspropSet() {
        syspropName = SYSPROP_NAME
        preferenceProvider.storedKey = NEW_KEY
        projectKeyProvider.reset(BROADCAST)
        assertThat(preferenceProvider.storedKey).isEqualTo(NEW_KEY)
    }

    @Test
    fun resetNotAllowed_alreadyReset() {
        syspropName = SYSPROP_NAME
        preferenceProvider.storedKey = null
        projectKeyProvider.reset(SYSPROP)
        assertThat(preferenceProvider.storedKey).isNull()
        confirmVerified(marFileHoldingArea)
    }
}
