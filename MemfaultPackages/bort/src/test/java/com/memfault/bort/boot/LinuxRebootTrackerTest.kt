package com.memfault.bort.boot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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

    @BeforeEach
    fun setUp() {
        currentBootId = ""
        fakeLastTrackedLinuxBootIdProvider = FakeLastTrackedLinuxBootIdProvider()
    }

    @Test
    fun rebooted() {
        fakeLastTrackedLinuxBootIdProvider.id = "A"
        currentBootId = "B"
        val tracker = LinuxRebootTracker(::currentBootId, fakeLastTrackedLinuxBootIdProvider)
        assertTrue(tracker.checkAndUnset())
        assertFalse(tracker.checkAndUnset())
        assertEquals(fakeLastTrackedLinuxBootIdProvider.id, currentBootId)
    }

    @Test
    fun notRebooted() {
        fakeLastTrackedLinuxBootIdProvider.id = "A"
        currentBootId = "A"
        val tracker = LinuxRebootTracker(::currentBootId, fakeLastTrackedLinuxBootIdProvider)
        assertFalse(tracker.checkAndUnset())
        assertFalse(tracker.checkAndUnset())
    }
}
