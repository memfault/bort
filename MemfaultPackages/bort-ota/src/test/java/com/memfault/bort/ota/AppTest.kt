package com.memfault.bort.ota

import android.content.Context
import com.memfault.bort.ota.App.Companion.shouldAutoInstallOtaUpdate
import com.memfault.bort.ota.lib.Ota
import io.mockk.mockk
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
        val canInstallNow = { _: Context -> true }
        val context = mockk<Context>()
        assertEquals(default, shouldAutoInstallOtaUpdate(ota, default, canInstallNow, context))
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
        val canInstallNow = { _: Context -> true }
        val context = mockk<Context>()
        assertEquals(default, shouldAutoInstallOtaUpdate(ota, default, canInstallNow, context))
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
        val canInstallNow = { _: Context -> true }
        val context = mockk<Context>()
        assertTrue(shouldAutoInstallOtaUpdate(ota, default, canInstallNow, context))
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
        val canInstallNow = { _: Context -> false }
        val context = mockk<Context>()
        assertFalse(shouldAutoInstallOtaUpdate(ota, default, canInstallNow, context))
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
        val canInstallNow = { _: Context -> true }
        val context = mockk<Context>()
        assertFalse(shouldAutoInstallOtaUpdate(ota, default, canInstallNow, context))
    }
}
