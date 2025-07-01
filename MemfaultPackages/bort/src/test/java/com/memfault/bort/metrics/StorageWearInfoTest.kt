package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class StorageWearInfoTest {
    @Test fun `Parses the output of disk wear correctly`() {
        val outputs = listOf(
            "1 2 3 mmc0 version 1",
            "4 5 6 624000.ufshc ufs 310",
            "7 8 9 ",
            "7 8 9",
        )

        val expected = listOf(
            StorageWearInfo(
                source = "mmc0",
                eol = 1,
                lifetimeA = 2,
                lifetimeB = 3,
                version = "version 1",
            ),
            StorageWearInfo(
                source = "624000.ufshc",
                eol = 4,
                lifetimeA = 5,
                lifetimeB = 6,
                version = "ufs 310",
            ),
            StorageWearInfo(
                source = "",
                eol = 7,
                lifetimeA = 8,
                lifetimeB = 9,
                version = "",
            ),
            StorageWearInfo(
                source = "",
                eol = 7,
                lifetimeA = 8,
                lifetimeB = 9,
                version = "",
            ),
        )

        assertThat(outputs.map { StorageWearInfo.fromServiceOutput(it) })
            .isEqualTo(expected)
    }
}
