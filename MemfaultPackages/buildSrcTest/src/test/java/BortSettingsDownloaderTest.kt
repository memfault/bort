package com.memfault.bort.buildsrc

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

const val SUCCESS_FIXTURE = """{"valid": "true"}"""

class BortSettingsDownloaderTest {
    lateinit var mockRootDir: File
    lateinit var mockWarn: (String, Exception?) -> Unit
    lateinit var mockGetConfig: () -> String

    @BeforeEach
    fun setUp() {
        mockRootDir = Files.createTempDirectory("bortBuildSrcTest").toFile()
        mockWarn = mockk(relaxed = true)
        mockGetConfig = mockk()
        every {
            mockGetConfig()
        } returns SUCCESS_FIXTURE
    }

    @Test
    fun throwsIfInitialDownloadFails() {
        every {
            mockGetConfig()
        } throws Exception()
        assertThrows<BortSettingsDownloaderException> {
            refreshSettings(mockRootDir, mockWarn, mockGetConfig)
        }
    }

    @Test
    fun warnsButUsesExistingConfigIfDownloadFails() {
        refreshSettings(mockRootDir, mockWarn, mockGetConfig)

        every {
            mockGetConfig()
        } throws Exception()
        refreshSettings(mockRootDir, mockWarn, mockGetConfig)
        verify(exactly = 1) {
            mockWarn(
                match { it.contains("reusing existing") },
                any()
            )
        }
    }

    @Test
    fun writesGeneratedFileIfDownloadSucceeds() {
        assertEquals(false, getBortSettingsAssetsFile(mockRootDir).isFile)
        refreshSettings(mockRootDir, mockWarn, mockGetConfig)
        assertEquals(true, getBortSettingsAssetsFile(mockRootDir).isFile)

        getBortSettingsAssetsFile(mockRootDir)
            .inputStream()
            .use { it.bufferedReader().readText() }
    }
}
