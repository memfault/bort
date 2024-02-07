package com.memfault.bort.parsers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PackageManagerReportTest {
    @Test
    fun appIdGuessesFromProcessName() {
        // Valid app IDs must have at least one dot:
        assertEquals(
            listOf("com.memfault.smartsink.bort", "com.memfault.smartsink", "com.memfault"),
            PackageManagerReport.Util.appIdGuessesFromProcessName("com.memfault.smartsink.bort").toList(),
        )

        assertEquals(
            emptyList<String>(),
            PackageManagerReport.Util.appIdGuessesFromProcessName("/system/bin/storaged").toList(),
        )
    }
}
