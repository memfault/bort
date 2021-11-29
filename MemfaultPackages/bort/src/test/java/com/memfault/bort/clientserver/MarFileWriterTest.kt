package com.memfault.bort.clientserver

import com.memfault.bort.BortJson
import com.memfault.bort.clientserver.MarFileWriter.Companion.writeMarFile
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.boxed
import java.io.File
import java.io.FileInputStream
import java.time.Instant.now
import java.util.zip.ZipInputStream
import kotlin.time.Duration
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

internal class MarFileWriterTest {
    @Test fun createMarFile() {
        val marFile = File.createTempFile("marfile", "marfile.zip")
        val device = MarDevice(
            projectKey = "projectKey",
            hardwareVersion = "hardwareVersion",
            softwareVersion = "softwareVersion",
            softwareType = "softwareType",
            deviceSerial = "deviceSerial",
        )
        val time = CombinedTime(
            uptime = Duration.ZERO.boxed(),
            elapsedRealtime = Duration.ZERO.boxed(),
            linuxBootId = "bootid",
            bootCount = 1,
            timestamp = now()
        )
        val inputFile = File.createTempFile("input", "input")
        val manifest = MarManifest(
            collectionTime = time,
            type = "android-heartbeat",
            device = device,
            metadata = MarMetadata.HeartbeatMarMetadata(
                batteryStatsFileName = inputFile.name,
                heartbeatIntervalMs = 2,
                customMetrics = emptyMap(),
                builtinMetrics = emptyMap(),
            )
        )
        inputFile.writeText(FILE_CONTENT)
        writeMarFile(marFile, manifest, inputFile)

        ZipInputStream(FileInputStream(marFile)).use { zip ->
            val dirEntry = zip.nextEntry
            assertTrue(dirEntry.isDirectory)

            val manifestEntry = zip.nextEntry
            assertTrue(manifestEntry.name.endsWith("manifest.json"))
            val manifestOutputString = zip.readBytes().decodeToString()
            val manifestOutput = BortJson.decodeFromString(MarManifest.serializer(), manifestOutputString)
            assertEquals(manifest, manifestOutput)

            val fileEntry = zip.nextEntry
            assertTrue(fileEntry.name.endsWith(inputFile.name))
            val fileContentOutput = zip.readBytes().decodeToString()
            assertEquals(FILE_CONTENT, fileContentOutput)
        }
    }

    companion object {
        private const val FILE_CONTENT = "hi this is the input file"
    }
}
