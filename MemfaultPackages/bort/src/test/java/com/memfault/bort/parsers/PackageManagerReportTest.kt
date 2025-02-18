package com.memfault.bort.parsers

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class PackageManagerReportTest {
    @Test
    fun appIdGuessesFromProcessName() {
        // Valid app IDs must have at least one dot:
        assertThat(
            PackageManagerReport.Util.appIdGuessesFromProcessName("com.memfault.smartsink.bort").toList(),
        ).isEqualTo(
            listOf("com.memfault.smartsink.bort", "com.memfault.smartsink", "com.memfault"),
        )

        assertThat(
            PackageManagerReport.Util.appIdGuessesFromProcessName("/system/bin/storaged").toList(),
        ).isEqualTo(
            emptyList<String>(),
        )
    }
}
