package com.memfault.bort.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SetReporterSettingsRequestTest {
    @Test
    fun reporterSettings_defaultValuesIn() {
        val emptyJson = "{}"
        val decodedMessage = SetReporterSettingsRequest.fromJson(emptyJson)
        assertEquals(SetReporterSettingsRequest(), decodedMessage)
    }

    @Test
    fun reporterSettings_unknownValuesHandled() {
        val emptyJson = "{\"unknownField\":52436, \"maxFileTransferStorageBytes\":12345}"
        val decodedMessage = SetReporterSettingsRequest.fromJson(emptyJson)
        assertEquals(SetReporterSettingsRequest(maxFileTransferStorageBytes = 12345), decodedMessage)
    }
}
