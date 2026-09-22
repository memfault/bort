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

    @Test
    fun uidToName() {
        val report = PackageManagerReport(
            listOf(
                Package(id = "com.memfault.smartfridge", userId = 10045),
                Package(id = "com.memfault.smartfridge.secondary", userId = 1010046),
            ),
        )

        assertThat(report.uidToName(10045)).isEqualTo("com.memfault.smartfridge")
        assertThat(report.uidToName(1010046)).isEqualTo("com.memfault.smartfridge.secondary")
        assertThat(report.uidToName(10099)).isEqualTo("unknown")
        assertThat(report.uidToName(1010099)).isEqualTo("unknown")
        assertThat(report.uidToName(ProcessUid.PROCESS_SYSTEM.uid)).isEqualTo("system")
        assertThat(report.uidToName(1234)).isEqualTo("android")
    }
}
