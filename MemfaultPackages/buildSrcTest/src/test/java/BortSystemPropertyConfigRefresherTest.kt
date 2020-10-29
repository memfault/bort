package com.memfault.bort.buildsrc

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BortSystemPropertyConfigRefresherTest {
    lateinit var mockRootDir: String
    lateinit var mockWarn: (String, Exception?) -> Unit
    lateinit var mockGetConfig: () -> String

    @BeforeEach
    fun setUp() {
        mockRootDir = Files.createTempDirectory("bortBuildSrcTest").toString()
        mockWarn = mockk(relaxed = true)
        mockGetConfig = mockk()
        every {
            mockGetConfig()
        } returns """FOO=BAR"""
    }

    @Test
    fun throwsIfInitialDownloadFails() {
        every {
            mockGetConfig()
        } throws Exception()
        assertThrows<BortConfigDownloadException> {
            BortSystemPropertyConfigRefresher(mockRootDir, mockWarn, mockGetConfig).tryRefresh()
        }
    }

    @Test
    fun warnsButUsesExistingConfigIfDownloadFails() {
        BortSystemPropertyConfigRefresher(mockRootDir, mockWarn, mockGetConfig).tryRefresh()

        every {
            mockGetConfig()
        } throws Exception()
        BortSystemPropertyConfigRefresher(mockRootDir, mockWarn, mockGetConfig).tryRefresh()
        verify(exactly = 1) {
            mockWarn(
                match { it.contains("reusing existing") },
                any()
            )
        }
    }

    @Test
    fun writesGeneratedFileIfDownloadSucceeds() {
        assertEquals(false, getBortSystemPropertyConfigFile(mockRootDir).isFile)
        BortSystemPropertyConfigRefresher(mockRootDir, mockWarn, mockGetConfig).tryRefresh()
        assertEquals(true, getBortSystemPropertyConfigFile(mockRootDir).isFile)
        val properties = bortSystemPropertyConfig(mockRootDir)
        assertEquals("BAR", properties["FOO"])
    }
}
