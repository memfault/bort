package com.memfault.bort.shared

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class SetReporterSettingsRequestTest {
    @Test
    fun reporterSettings_defaultValuesIn() {
        val emptyJson = "{}"
        val decodedMessage = SetReporterSettingsRequest.fromJson(emptyJson)
        assertThat(decodedMessage).isEqualTo(SetReporterSettingsRequest())
    }

    @Test
    fun reporterSettings_unknownValuesHandled() {
        val emptyJson = "{\"unknownField\":52436, \"maxFileTransferStorageBytes\":12345}"
        val decodedMessage = SetReporterSettingsRequest.fromJson(emptyJson)
        assertThat(decodedMessage).isEqualTo(SetReporterSettingsRequest(maxFileTransferStorageBytes = 12345))
    }
}
