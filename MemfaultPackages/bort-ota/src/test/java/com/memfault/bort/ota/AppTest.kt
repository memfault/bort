package com.memfault.bort.ota

import com.memfault.bort.ota.App.Companion.shouldAutoInstallOtaUpdate
import com.memfault.bort.ota.lib.Ota
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTest {
    @Test fun usesDefaultIfNoIsForcedValue_false() {
        val ota = Ota(
            url = "url",
            version = "version",
            releaseNotes = "notes",
            metadata = emptyMap(),
            isForced = null,
        )
        val default = false
        val canInstallNow = { true }
        assertEquals(default, shouldAutoInstallOtaUpdate(ota, default, canInstallNow))
    }

    @Test fun usesDefaultIfNoIsForcedValue_true() {
        val ota = Ota(
            url = "url",
            version = "version",
            releaseNotes = "notes",
            metadata = emptyMap(),
            isForced = null,
        )
        val default = true
        val canInstallNow = { true }
        assertEquals(default, shouldAutoInstallOtaUpdate(ota, default, canInstallNow))
    }

    @Test fun isForced() {
        val ota = Ota(
            url = "url",
            version = "version",
            releaseNotes = "notes",
            metadata = emptyMap(),
            isForced = true,
        )
        val default = false
        val canInstallNow = { true }
        assertTrue(shouldAutoInstallOtaUpdate(ota, default, canInstallNow))
    }

    @Test fun cantInstallNow() {
        val ota = Ota(
            url = "url",
            version = "version",
            releaseNotes = "notes",
            metadata = emptyMap(),
            isForced = true,
        )
        val default = true
        val canInstallNow = { false }
        assertFalse(shouldAutoInstallOtaUpdate(ota, default, canInstallNow))
    }

    @Test fun isNotForced() {
        val ota = Ota(
            url = "url",
            version = "version",
            releaseNotes = "notes",
            metadata = emptyMap(),
            isForced = false,
        )
        val default = false
        val canInstallNow = { true }
        assertFalse(shouldAutoInstallOtaUpdate(ota, default, canInstallNow))
    }
}
