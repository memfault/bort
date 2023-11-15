package com.memfault.bort.settings

import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.ProcessingOptions
import com.memfault.bort.TimezoneWithId
import com.memfault.bort.clientserver.MarDevice
import com.memfault.bort.clientserver.MarManifest
import com.memfault.bort.clientserver.MarMetadata
import com.memfault.bort.logcat.FakeNextLogcatCidProvider
import com.memfault.bort.time.AbsoluteTime
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResolutionTest {
    @Test
    fun ordering() {
        assertTrue(Resolution.LOW > Resolution.OFF)
        assertTrue(Resolution.LOW >= Resolution.LOW)
        assertTrue(Resolution.NORMAL > Resolution.LOW)
        assertTrue(Resolution.NORMAL >= Resolution.NORMAL)
        assertTrue(Resolution.HIGH > Resolution.NORMAL)
        assertTrue(Resolution.HIGH > Resolution.LOW)
        assertTrue(Resolution.HIGH > Resolution.OFF)
        assertTrue(Resolution.HIGH >= Resolution.HIGH)
        assertTrue(Resolution.NOT_APPLICABLE > Resolution.HIGH)
        assertTrue(Resolution.NOT_APPLICABLE > Resolution.NORMAL)
        assertTrue(Resolution.NOT_APPLICABLE > Resolution.LOW)
        assertTrue(Resolution.NOT_APPLICABLE > Resolution.OFF)
        assertTrue(Resolution.NOT_APPLICABLE >= Resolution.NOT_APPLICABLE)
    }

    private val marDevice = MarDevice(
        projectKey = "projectKey",
        hardwareVersion = "hardwareVersion",
        softwareVersion = "softwareVersion",
        softwareType = "softwareType",
        deviceSerial = "deviceSerial",
    )
    private val bugReportManifest = MarManifest(
        collectionTime = FakeCombinedTimeProvider.now(),
        type = "test",
        device = marDevice,
        metadata = MarMetadata.BugReportMarMetadata(
            bugReportFileName = "filename",
            processingOptions = ProcessingOptions(),
        ),
        debuggingResolution = Resolution.NORMAL,
        loggingResolution = Resolution.NOT_APPLICABLE,
        monitoringResolution = Resolution.NOT_APPLICABLE,
    )

    @Test
    fun bugReportSampled() {
        assertTrue(
            SamplingConfig(
                monitoringResolution = Resolution.OFF,
                debuggingResolution = Resolution.NORMAL,
                loggingResolution = Resolution.OFF,
            ).shouldUpload(bugReportManifest),
        )
    }

    @Test
    fun bugReportUnsampled() {
        assertFalse(
            SamplingConfig(
                monitoringResolution = Resolution.OFF,
                debuggingResolution = Resolution.OFF,
                loggingResolution = Resolution.OFF,
            ).shouldUpload(bugReportManifest),
        )
    }

    private val dropboxManifest = MarManifest(
        collectionTime = FakeCombinedTimeProvider.now(),
        type = "test",
        device = marDevice,
        metadata = MarMetadata.DropBoxMarMetadata(
            entryFileName = "filename",
            tag = "tag",
            entryTime = AbsoluteTime.now(),
            timezone = TimezoneWithId.deviceDefault,
            cidReference = FakeNextLogcatCidProvider.incrementing().cid,
            packages = emptyList(),
            fileTime = null,
        ),
        debuggingResolution = Resolution.NORMAL,
        loggingResolution = Resolution.NOT_APPLICABLE,
        monitoringResolution = Resolution.NOT_APPLICABLE,
    )

    @Test
    fun dropboxSampled() {
        assertTrue(
            SamplingConfig(
                monitoringResolution = Resolution.OFF,
                debuggingResolution = Resolution.NORMAL,
                loggingResolution = Resolution.OFF,
            ).shouldUpload(dropboxManifest),
        )
    }

    @Test
    fun dropboxUnsampled() {
        assertFalse(
            SamplingConfig(
                monitoringResolution = Resolution.OFF,
                debuggingResolution = Resolution.OFF,
                loggingResolution = Resolution.OFF,
            ).shouldUpload(dropboxManifest),
        )
    }

    private val heartbeatManifest = MarManifest(
        collectionTime = FakeCombinedTimeProvider.now(),
        type = "test",
        device = marDevice,
        metadata = MarMetadata.HeartbeatMarMetadata(
            batteryStatsFileName = null,
            heartbeatIntervalMs = 0,
            customMetrics = emptyMap(),
            builtinMetrics = emptyMap(),
        ),
        debuggingResolution = Resolution.NOT_APPLICABLE,
        loggingResolution = Resolution.NOT_APPLICABLE,
        monitoringResolution = Resolution.NORMAL,
    )

    @Test
    fun heartbeatSampled() {
        assertTrue(
            SamplingConfig(
                debuggingResolution = Resolution.OFF,
                loggingResolution = Resolution.OFF,
                monitoringResolution = Resolution.NORMAL,
            ).shouldUpload(heartbeatManifest),
        )
    }

    @Test
    fun heartbeatUnsampled() {
        assertFalse(
            SamplingConfig(
                monitoringResolution = Resolution.OFF,
                debuggingResolution = Resolution.OFF,
                loggingResolution = Resolution.OFF,
            ).shouldUpload(heartbeatManifest),
        )
    }

    private val logcatManifestNormal = MarManifest(
        collectionTime = FakeCombinedTimeProvider.now(),
        type = "test",
        device = marDevice,
        metadata = MarMetadata.LogcatMarMetadata(
            logFileName = "",
            command = emptyList(),
            cid = FakeNextLogcatCidProvider.incrementing().cid,
            nextCid = FakeNextLogcatCidProvider.incrementing().cid,
        ),
        debuggingResolution = Resolution.NORMAL,
        loggingResolution = Resolution.NORMAL,
        monitoringResolution = Resolution.NOT_APPLICABLE,

    )
    private val logcatManifestNoDebugging = logcatManifestNormal.copy(debuggingResolution = Resolution.NOT_APPLICABLE)

    @Test
    fun logcatNormalSampled() {
        assertTrue(
            SamplingConfig(
                monitoringResolution = Resolution.OFF,
                debuggingResolution = Resolution.NORMAL,
                loggingResolution = Resolution.OFF,
            ).shouldUpload(logcatManifestNormal),
        )
    }

    @Test
    fun logcatNormalUnsampled() {
        assertFalse(
            SamplingConfig(
                debuggingResolution = Resolution.OFF,
                loggingResolution = Resolution.OFF,
                monitoringResolution = Resolution.OFF,
            ).shouldUpload(logcatManifestNormal),
        )
    }

    @Test
    fun logcatHighSampled() {
        assertTrue(
            SamplingConfig(
                monitoringResolution = Resolution.OFF,
                debuggingResolution = Resolution.OFF,
                loggingResolution = Resolution.NORMAL,
            ).shouldUpload(logcatManifestNoDebugging),
        )
    }

    @Test
    fun logcatHighUnsampled() {
        assertFalse(
            SamplingConfig(
                monitoringResolution = Resolution.OFF,
                debuggingResolution = Resolution.NORMAL,
                loggingResolution = Resolution.OFF,
            ).shouldUpload(logcatManifestNoDebugging),
        )
    }
}
