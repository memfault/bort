package com.memfault.bort

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class RebootTrackingTest {
    @Test
    fun testIfBootNotTrackedYet() {
        val currentBootCount = 1
        val untrackedBootCountHandler = mock<(Int) -> Unit>()
        val provider = object : LastTrackedBootCountProvider {
            override var bootCount: Int = 0
        }

        assertNotEquals(currentBootCount, provider.bootCount)
        BootCountTracker(provider, untrackedBootCountHandler).trackIfNeeded(currentBootCount)

        // Expect the LastTrackedBootCountProvider to have been updated and the block to have been called
        assertEquals(currentBootCount, provider.bootCount)
        verify(untrackedBootCountHandler, times(1))(currentBootCount)

        // Block should not be called again, because the boot count has not been bumped
        BootCountTracker(provider, untrackedBootCountHandler).trackIfNeeded(currentBootCount)
        verify(untrackedBootCountHandler, times(1))(currentBootCount)
    }
}

class AndroidBootReasonParsing {
    @Test
    fun parsing() {
        val testCases = listOf(
            null to AndroidBootReason("reboot", "bort_unknown"),
            "" to AndroidBootReason(""),
            "reboot" to AndroidBootReason("reboot"),
            "reboot,userrequested" to AndroidBootReason("reboot", "userrequested"),
            "shutdown,battery,thermal" to AndroidBootReason("shutdown", "battery", listOf("thermal")),
            "shutdown,battery,thermal,50C" to AndroidBootReason("shutdown", "battery", listOf("thermal", "50C"))
        )
        for ((input, expectedOutput) in testCases) {
            assertEquals(expectedOutput, AndroidBootReason.parse(input))
        }
    }
}
