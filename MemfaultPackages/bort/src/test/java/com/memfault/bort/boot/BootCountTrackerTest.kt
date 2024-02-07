package com.memfault.bort.boot

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.settings.SettingsProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class BootCountTrackerTest {

    private val lastTrackedBootCountProvider = object : LastTrackedBootCountProvider {
        override var bootCount: Int = 0
    }
    private val rebootEventUploader = mockk<RebootEventUploader> {
        coEvery { handleUntrackedBootCount(any()) } answers { }
    }
    private val settingsProvider = mockk<SettingsProvider> {
        every { rebootEventsSettings.dataSourceEnabled } returns true
    }

    private val bootCountTracker = BootCountTracker(
        lastTrackedBootCountProvider,
        rebootEventUploader,
        settingsProvider,
    )

    @Test
    fun testIfBootNotTrackedYet() = runTest {
        assertThat(lastTrackedBootCountProvider.bootCount).isEqualTo(0)

        bootCountTracker.trackIfNeeded(1)
        // Expect the LastTrackedBootCountProvider to have been updated and the block to have been called
        assertThat(lastTrackedBootCountProvider.bootCount).isEqualTo(1)
        coVerify(exactly = 1) {
            rebootEventUploader.handleUntrackedBootCount(any())
        }

        bootCountTracker.trackIfNeeded(1)
        // Block should not be called again, because the boot count has not been bumped
        assertThat(lastTrackedBootCountProvider.bootCount).isEqualTo(1)
        coVerify(exactly = 1) {
            rebootEventUploader.handleUntrackedBootCount(any())
        }
    }
}
