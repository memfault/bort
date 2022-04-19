package com.memfault.bort.settings

import com.memfault.bort.clientserver.CachedClientServerMode
import com.memfault.bort.clientserver.LinkedDeviceFileSender
import com.memfault.bort.shared.CLIENT_SERVER_SETTINGS_UPDATE_DROPBOX_TAG
import com.memfault.bort.shared.ClientServerMode
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.test.util.TestTemporaryFileFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals

class SettingsUpdateHandlerTest {
    private val settingsProvider: DynamicSettingsProvider = mockk {
        every { invalidate() } returns Unit
    }
    private val storedSettingsPreferenceProvider: StoredSettingsPreferenceProvider = mockk {
        every { set(any()) } returns Unit
        every { get() } returns SETTINGS_FIXTURE.toSettings()
    }
    private val fetchedSettingsUpdateSlot = slot<FetchedSettingsUpdate>()
    private val callback: SettingsUpdateCallback = mockk {
        coEvery { onSettingsUpdated(any(), capture(fetchedSettingsUpdateSlot)) } returns Unit
    }
    private var clientServerMode: ClientServerMode = ClientServerMode.DISABLED
    private val cachedClientServerMode = CachedClientServerMode { clientServerMode }
    private val linkedDeviceFileSender: LinkedDeviceFileSender = mockk(relaxed = true)
    private val temporaryFileFactory = TestTemporaryFileFactory
    private val handler = SettingsUpdateHandler(
        settingsProvider = settingsProvider,
        storedSettingsPreferenceProvider = storedSettingsPreferenceProvider,
        settingsUpdateCallback = callback,
        metrics = mockk(relaxed = true),
        cachedClientServerMode = cachedClientServerMode,
        linkedDeviceFileSender = linkedDeviceFileSender,
        temporaryFileFactory = temporaryFileFactory,
    )

    @Test
    fun validResponse() = runBlocking {
        val response1 = SETTINGS_FIXTURE.toSettings()
        handler.handleSettingsUpdate(response1)

        // The first call returns the same stored fixture and thus set() won't be called
        verify {
            storedSettingsPreferenceProvider.get()
        }
        confirmVerified(storedSettingsPreferenceProvider)

        // The second one will trigger the update
        val response2 = response1.copy(bortMinLogcatLevel = LogLevel.NONE.level)
        handler.handleSettingsUpdate(response2)

        // Check that settings was invalidated after a remote update
        coVerify {
            storedSettingsPreferenceProvider.get()
            storedSettingsPreferenceProvider.set(response2)
            settingsProvider.invalidate()
            callback.onSettingsUpdated(any(), any())
        }
        confirmVerified(settingsProvider)
        assertEquals(fetchedSettingsUpdateSlot.captured.old, SETTINGS_FIXTURE.toSettings())
        assertEquals(fetchedSettingsUpdateSlot.captured.new, response2)
    }

    @Test
    fun clientServer_Disabled_doesNotForward() = runBlocking {
        clientServerMode = ClientServerMode.DISABLED
        val response1 = SETTINGS_FIXTURE.toSettings()
        handler.handleSettingsUpdate(response1)
        confirmVerified(linkedDeviceFileSender)
    }

    @Test
    fun clientServer_Client_doesNotForward() = runBlocking {
        clientServerMode = ClientServerMode.CLIENT
        val response1 = SETTINGS_FIXTURE.toSettings()
        handler.handleSettingsUpdate(response1)
        confirmVerified(linkedDeviceFileSender)
    }

    @Test
    fun clientServer_Server_forwards() = runBlocking {
        clientServerMode = ClientServerMode.SERVER
        val response1 = SETTINGS_FIXTURE.toSettings()
        handler.handleSettingsUpdate(response1)
        coVerify { linkedDeviceFileSender.sendFileToLinkedDevice(any(), CLIENT_SERVER_SETTINGS_UPDATE_DROPBOX_TAG) }
        confirmVerified(linkedDeviceFileSender)
    }
}
