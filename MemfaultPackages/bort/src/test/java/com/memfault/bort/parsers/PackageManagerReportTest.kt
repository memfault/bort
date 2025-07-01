package com.memfault.bort.parsers

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.memfault.bort.parsers.PackageManagerReport.Companion.PROCESS_UID_COMPONENT_MAP
import com.memfault.bort.parsers.PackageManagerReport.Companion.ProcessUid
import org.junit.Test

class PackageManagerReportTest {
    @Test
    fun appIdGuessesFromProcessName() {
        // Valid app IDs must have at least one dot:
        assertThat(
            PackageManagerReport.appIdGuessesFromProcessName("com.memfault.smartsink.bort").toList(),
        ).containsExactly(
            "com.memfault.smartsink.bort",
            "com.memfault.smartsink",
            "com.memfault",
        )

        assertThat(
            PackageManagerReport.appIdGuessesFromProcessName("/system/bin/storaged").toList(),
        ).isEmpty()
    }

    @Test
    fun processUidComponentMap() {
        ProcessUid.entries.forEach { p ->
            assertThat(PROCESS_UID_COMPONENT_MAP[p.uid]).isEqualTo(p.processName)
        }
        assertThat(PROCESS_UID_COMPONENT_MAP[10000]).isNull()
        assertThat(PROCESS_UID_COMPONENT_MAP[15000]).isNull()
        assertThat(PROCESS_UID_COMPONENT_MAP[20000]).isNull()

        assertThat(ProcessUid.entries.map { it.uid }.toSet()).hasSize(ProcessUid.entries.size)
        assertThat(ProcessUid.entries.map { it.processName }.toSet()).hasSize(ProcessUid.entries.size)
    }
}
