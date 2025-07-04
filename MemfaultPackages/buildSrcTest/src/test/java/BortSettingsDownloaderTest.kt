package com.memfault.bort.buildsrc

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

const val SUCCESS_FIXTURE = """{"valid": "true"}"""
private val OTHER_FIXTURE = """{
    |  "valid" : "false"
    |}
""".trimMargin()

class BortSettingsDownloaderTest {
    lateinit var mockRootDir: File
    lateinit var mockWarn: (String, Exception?) -> Unit
    lateinit var mockFetchConfig: () -> String

    @Before
    fun setUp() {
        mockRootDir = Files.createTempDirectory("bortBuildSrcTest").toFile()
        mockWarn = mockk(relaxed = true)
        mockFetchConfig = mockk()
        every {
            mockFetchConfig()
        } returns SUCCESS_FIXTURE
    }

    @Test
    fun throwsIfInitialDownloadFails() {
        every {
            mockFetchConfig()
        } throws BortSettingsDownloaderException("")
        assertFailure {
            fetchBortSettingsInternal(
                rootDir = mockRootDir,
                useDevConfig = false,
                skipDownload = false,
                getDefaultProperty = { _ -> null },
                warn = mockWarn,
                fetchBortConfigFun = mockFetchConfig,
            )
        }.isInstanceOf<BortSettingsDownloaderException>()
    }

    @Test
    fun deploysDevConfig() {
        val devConfigDefaults = mapOf(
            "MINIMUM_LOG_LEVEL" to "2",
            "ANDROID_BUILD_VERSION_KEY" to "ro.build.date.utc",
            "ANDROID_BUILD_VERSION_SOURCE" to "build_fingerprint_and_system_property",
            "ANDROID_DEVICE_SERIAL_KEY" to "ro.serialno",
            "ANDROID_HARDWARE_VERSION_KEY" to "ro.product.model",
            "MEMFAULT_API_BASE_URL" to "http://app.memfault.test:8000",
            "MEMFAULT_FILES_BASE_URL" to "http://app.memfault.test:8000",
        )

        val mockDefaultProperty: (String) -> String? = mockk {
            every { this@mockk(any()) } answers {
                devConfigDefaults[firstArg() as String]
            }
        }

        fetchBortSettingsInternal(
            rootDir = mockRootDir,
            useDevConfig = true,
            skipDownload = false,
            getDefaultProperty = mockDefaultProperty,
            warn = mockWarn,
            fetchBortConfigFun = mockFetchConfig,
        )

        verify {
            devConfigDefaults.keys.forEach {
                mockDefaultProperty(it)
            }
        }

        verify(exactly = 0) { mockFetchConfig() }
    }

    @Test
    fun skipDownloadFailsIfNoLocalFile() {
        assertFailure {
            fetchBortSettingsInternal(
                rootDir = mockRootDir,
                useDevConfig = false,
                skipDownload = true,
                getDefaultProperty = { _ -> null },
                warn = mockWarn,
                fetchBortConfigFun = mockFetchConfig,
            )
        }.isInstanceOf<BortSettingsInconsistencyException>()
    }

    @Test
    fun skipDownloadWorksIfLocalFile() {
        getBortSettingsAssetsFile(mockRootDir).also {
            it.parentFile!!.mkdirs()
        }.writeText(SUCCESS_FIXTURE)

        fetchBortSettingsInternal(
            rootDir = mockRootDir,
            useDevConfig = false,
            skipDownload = true,
            getDefaultProperty = { _ -> null },
            warn = mockWarn,
            fetchBortConfigFun = mockFetchConfig,
        )

        // Check that nothing was downloaded
        confirmVerified(mockFetchConfig)
    }

    @Test
    fun fetchSucceedsIfLocalAndRemoteMatch() {
        getBortSettingsAssetsFile(mockRootDir).also {
            it.parentFile!!.mkdirs()
        }.writeText(SUCCESS_FIXTURE)

        every { mockFetchConfig() } returns SUCCESS_FIXTURE

        fetchBortSettingsInternal(
            rootDir = mockRootDir,
            useDevConfig = false,
            skipDownload = false,
            getDefaultProperty = { _ -> null },
            warn = mockWarn,
            fetchBortConfigFun = mockFetchConfig,
        )

        verify { mockFetchConfig() }
    }

    @Test
    fun fetchFailsAndShowsDiffIfLocalAndRemoteDontMatch() {
        getBortSettingsAssetsFile(mockRootDir).also {
            it.parentFile!!.mkdirs()
        }.writeText(SUCCESS_FIXTURE)
        every { mockFetchConfig() } returns OTHER_FIXTURE

        assertFailure {
            fetchBortSettingsInternal(
                rootDir = mockRootDir,
                useDevConfig = false,
                skipDownload = false,
                getDefaultProperty = { _ -> null },
                warn = mockWarn,
                fetchBortConfigFun = mockFetchConfig,
            )
        }.isInstanceOf<BortSettingsInconsistencyException>()

        verify {
            mockWarn(
                "Diff between local configuration and project configuration in Memfault servers:",
                null,
            )
            mockWarn(
                """[ {
                |  "op" : "replace",
                |  "path" : "/valid",
                |  "value" : "false"
                |} ]
                """.trimMargin(),
                null,
            )
        }
    }

    @Test
    fun fetchWithNoLocalFile() {
        every { mockFetchConfig() } returns OTHER_FIXTURE

        fetchBortSettingsInternal(
            rootDir = mockRootDir,
            useDevConfig = false,
            skipDownload = false,
            getDefaultProperty = { _ -> null },
            warn = mockWarn,
            fetchBortConfigFun = mockFetchConfig,
        )

        assertThat(getBortSettingsAssetsFile(mockRootDir).readText()).isEqualTo(OTHER_FIXTURE)
    }

    @Test
    fun fetchFailsWithInvalidJson() {
        every { mockFetchConfig() } returns "{["

        assertFailure {
            fetchBortSettingsInternal(
                rootDir = mockRootDir,
                useDevConfig = false,
                skipDownload = false,
                getDefaultProperty = { _ -> null },
                warn = mockWarn,
                fetchBortConfigFun = mockFetchConfig,
            )
        }.isInstanceOf<BortSettingsInconsistencyException>()
    }
}
