package com.memfault.bort.boot

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.settings.SettingsProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BootCountTrackerTest {
    private var bootCount = 1
    private val lastTrackedBootCountProvider = object : LastTrackedBootCountProvider {
        override var bootCount: Int = 0
    }
    private val rebootEventUploader = mockk<RebootEventUploader> {
        coEvery { handleUntrackedBootCount(any()) } answers { }
    }
    private val settingsProvider = mockk<SettingsProvider> {
        every { rebootEventsSettings.dataSourceEnabled } returns true
    }
    private val androidBootCount = AndroidBootCount { bootCount }
    private val bootCountTracker = BootCountTracker(
        lastTrackedBootCountProvider,
        rebootEventUploader,
        settingsProvider,
        androidBootCount,
    )

    @Test
    fun testIfBootNotTrackedYet() = runTest {
        assertThat(lastTrackedBootCountProvider.bootCount).isEqualTo(0)

        bootCount = 1
        bootCountTracker.trackIfNeeded()
        // Expect the LastTrackedBootCountProvider to have been updated and the block to have been called
        assertThat(lastTrackedBootCountProvider.bootCount).isEqualTo(1)
        coVerify(exactly = 1) {
            rebootEventUploader.handleUntrackedBootCount(any())
        }

        bootCount = 1
        bootCountTracker.trackIfNeeded()
        // Block should not be called again, because the boot count has not been bumped
        assertThat(lastTrackedBootCountProvider.bootCount).isEqualTo(1)
        coVerify(exactly = 1) {
            rebootEventUploader.handleUntrackedBootCount(any())
        }
    }
}
