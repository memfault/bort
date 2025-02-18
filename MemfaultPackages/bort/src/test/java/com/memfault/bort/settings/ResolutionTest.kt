package com.memfault.bort.settings

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.ProcessingOptions
import com.memfault.bort.TimezoneWithId
import com.memfault.bort.clientserver.MarDevice
import com.memfault.bort.clientserver.MarManifest
import com.memfault.bort.clientserver.MarMetadata
import com.memfault.bort.logcat.FakeNextLogcatCidProvider
import com.memfault.bort.time.AbsoluteTime
import org.junit.Test

class ResolutionTest {
    @Test
    fun ordering() {
        assertThat(Resolution.LOW > Resolution.OFF).isTrue()
        assertThat(Resolution.LOW >= Resolution.LOW).isTrue()
        assertThat(Resolution.NORMAL > Resolution.LOW).isTrue()
        assertThat(Resolution.NORMAL >= Resolution.NORMAL).isTrue()
        assertThat(Resolution.HIGH > Resolution.NORMAL).isTrue()
        assertThat(Resolution.HIGH > Resolution.LOW).isTrue()
        assertThat(Resolution.HIGH > Resolution.OFF).isTrue()
        assertThat(Resolution.HIGH >= Resolution.HIGH).isTrue()
        assertThat(Resolution.NOT_APPLICABLE > Resolution.HIGH).isTrue()
        assertThat(Resolution.NOT_APPLICABLE > Resolution.NORMAL).isTrue()
        assertThat(Resolution.NOT_APPLICABLE > Resolution.LOW).isTrue()
        assertThat(Resolution.NOT_APPLICABLE > Resolution.OFF).isTrue()
        assertThat(Resolution.NOT_APPLICABLE >= Resolution.NOT_APPLICABLE).isTrue()
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
        assertThat(
            SamplingConfig(
                monitoringResolution = Resolution.OFF,
                debuggingResolution = Resolution.NORMAL,
                loggingResolution = Resolution.OFF,
            ).shouldUpload(bugReportManifest),
        ).isTrue()
    }

    @Test
    fun bugReportUnsampled() {
        assertThat(
            SamplingConfig(
                monitoringResolution = Resolution.OFF,
                debuggingResolution = Resolution.OFF,
                loggingResolution = Resolution.OFF,
            ).shouldUpload(bugReportManifest),
        ).isFalse()
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
        assertThat(
            SamplingConfig(
                monitoringResolution = Resolution.OFF,
                debuggingResolution = Resolution.NORMAL,
                loggingResolution = Resolution.OFF,
            ).shouldUpload(dropboxManifest),
        ).isTrue()
    }

    @Test
    fun dropboxUnsampled() {
        assertThat(
            SamplingConfig(
                monitoringResolution = Resolution.OFF,
                debuggingResolution = Resolution.OFF,
                loggingResolution = Resolution.OFF,
            ).shouldUpload(dropboxManifest),
        ).isFalse()
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
            reportType = "heartbeat",
            reportName = null,
        ),
        debuggingResolution = Resolution.NOT_APPLICABLE,
        loggingResolution = Resolution.NOT_APPLICABLE,
        monitoringResolution = Resolution.NORMAL,
    )

    @Test
    fun heartbeatSampled() {
        assertThat(
            SamplingConfig(
                debuggingResolution = Resolution.OFF,
                loggingResolution = Resolution.OFF,
                monitoringResolution = Resolution.NORMAL,
            ).shouldUpload(heartbeatManifest),
        ).isTrue()
    }

    @Test
    fun heartbeatUnsampled() {
        assertThat(
            SamplingConfig(
                monitoringResolution = Resolution.OFF,
                debuggingResolution = Resolution.OFF,
                loggingResolution = Resolution.OFF,
            ).shouldUpload(heartbeatManifest),
        ).isFalse()
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
        assertThat(
            SamplingConfig(
                monitoringResolution = Resolution.OFF,
                debuggingResolution = Resolution.NORMAL,
                loggingResolution = Resolution.OFF,
            ).shouldUpload(logcatManifestNormal),
        ).isTrue()
    }

    @Test
    fun logcatNormalUnsampled() {
        assertThat(
            SamplingConfig(
                debuggingResolution = Resolution.OFF,
                loggingResolution = Resolution.OFF,
                monitoringResolution = Resolution.OFF,
            ).shouldUpload(logcatManifestNormal),
        ).isFalse()
    }

    @Test
    fun logcatHighSampled() {
        assertThat(
            SamplingConfig(
                monitoringResolution = Resolution.OFF,
                debuggingResolution = Resolution.OFF,
                loggingResolution = Resolution.NORMAL,
            ).shouldUpload(logcatManifestNoDebugging),
        ).isTrue()
    }

    @Test
    fun logcatHighUnsampled() {
        assertThat(
            SamplingConfig(
                monitoringResolution = Resolution.OFF,
                debuggingResolution = Resolution.NORMAL,
                loggingResolution = Resolution.OFF,
            ).shouldUpload(logcatManifestNoDebugging),
        ).isFalse()
    }
}
