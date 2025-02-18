package com.memfault.bort.boot

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Before
import org.junit.Test

data class FakeLastTrackedLinuxBootIdProvider(
    var id: String = "",
) : LastTrackedLinuxBootIdProvider {
    override fun read(): String = id
    override fun write(linuxBootId: String) {
        id = linuxBootId
    }
}

class LinuxRebootTrackerTest {
    lateinit var currentBootId: String
    lateinit var fakeLastTrackedLinuxBootIdProvider: FakeLastTrackedLinuxBootIdProvider

    @Before
    fun setUp() {
        currentBootId = ""
        fakeLastTrackedLinuxBootIdProvider = FakeLastTrackedLinuxBootIdProvider()
    }

    @Test
    fun rebooted() {
        fakeLastTrackedLinuxBootIdProvider.id = "A"
        currentBootId = "B"
        val tracker = LinuxRebootTracker(::currentBootId, fakeLastTrackedLinuxBootIdProvider)
        assertThat(tracker.checkAndUnset()).isTrue()
        assertThat(tracker.checkAndUnset()).isFalse()
        assertThat(currentBootId).isEqualTo(fakeLastTrackedLinuxBootIdProvider.id)
    }

    @Test
    fun notRebooted() {
        fakeLastTrackedLinuxBootIdProvider.id = "A"
        currentBootId = "A"
        val tracker = LinuxRebootTracker(::currentBootId, fakeLastTrackedLinuxBootIdProvider)
        assertThat(tracker.checkAndUnset()).isFalse()
        assertThat(tracker.checkAndUnset()).isFalse()
    }
}
