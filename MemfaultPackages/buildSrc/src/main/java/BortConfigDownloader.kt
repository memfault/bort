package com.memfault.bort.buildsrc

import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Paths
import java.util.*

const val BORT_GENERATED_PROPERTIES_FILE_NAME = "bort_generated.properties"

class BortConfigDownloadException(msg: String, cause: Throwable?) : Exception(msg, cause) {
    constructor(msg: String) : this(msg, null)
}

class BortSystemPropertyConfigDownloader(
    val apiBaseUrl: String,
    val projectKey: String,
    val softwareType: String = "android-build"
) {
    fun downloadJavaProperties(): String {
        val url = URL("${apiBaseUrl}/api/v0/bort/sys-prop-config/${softwareType}")
        val connection = try {
            url.openConnection().apply {
                setRequestProperty("Accept", "text/x-java-properties");
                setRequestProperty("Memfault-Project-Key", projectKey)
            } as HttpURLConnection
        } catch (e: Exception) {
            throw BortConfigDownloadException("Failed to connect to Memfault Server: ${url}", e)
        }
        val responseCode = try {
            connection.getResponseCode()
        } catch (e: Exception) {
            throw BortConfigDownloadException("Failed to connect to Memfault Server: ${url}", e)
        }
        when (responseCode) {
            401 -> throw BortConfigDownloadException(
                """Memfault Server Error: ${connection.getResponseMessage()}
                   Please ensure MEMFAULT_PROJECT_API_KEY in bort.properties matches your project's settings.""".trimIndent()
            )
            in 400..599 -> throw BortConfigDownloadException(
                "Memfault Server Error: ${connection.getResponseMessage()}"
            )
        }
        return connection.getInputStream().use {
            it.bufferedReader().readText()
        }
    }
}

fun getBortSystemPropertyConfigFile(rootDir: String) =
    Paths.get(rootDir, BORT_GENERATED_PROPERTIES_FILE_NAME).toFile()


class BortSystemPropertyConfigRefresher(
    val rootDir: String,
    val warn: (String, Exception?) -> Unit,
    val getConfig: () -> String
) {
    fun tryRefresh() {
        val generatedFile = getBortSystemPropertyConfigFile(rootDir)
        val text = try {
            getConfig()
        } catch (e: Exception) {
            if (!generatedFile.isFile) {
                throw BortConfigDownloadException("Failed to fetch initial configuration from Memfault Server", e)
            }
            warn("Failed to refresh configuration from Memfault Server, reusing existing one...", e)
            return
        }
        generatedFile.writeText(text)
    }
}

fun bortSystemPropertyConfig(rootDir: String): Properties {
    val generatedFile = getBortSystemPropertyConfigFile(rootDir)
    val properties = Properties()
    properties.load(FileInputStream(generatedFile))
    return properties
}

fun tryRefreshBortSystemPropertyConfig(
    rootDir: String, warn: (String, Exception?) -> Unit, apiBaseUrl: String, projectKey: String
) {
    BortSystemPropertyConfigRefresher(
        rootDir = rootDir,
        warn = warn,
        getConfig = {
            BortSystemPropertyConfigDownloader(
                apiBaseUrl = apiBaseUrl,
                projectKey = projectKey
            ).downloadJavaProperties()
        }
    ).tryRefresh()
}
