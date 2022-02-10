package com.memfault.bort.logcat

import com.memfault.bort.BortJson
import com.memfault.bort.CredentialScrubbingRule
import com.memfault.bort.DataScrubber
import com.memfault.bort.EmailScrubbingRule
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.PackageNameAllowList
import com.memfault.bort.parsers.Package
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.settings.RateLimitingSettings
import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.LogcatPriority
import com.memfault.bort.shared.LogcatPrioritySerializer
import com.memfault.bort.test.util.TestTemporaryFileFactory
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BaseAbsoluteTime
import com.memfault.bort.time.boxed
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.ZERO
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

data class FakeNextLogcatStartTimeProvider(
    override var nextStart: BaseAbsoluteTime,
) : NextLogcatStartTimeProvider

private val FAKE_NOW = AbsoluteTime(Instant.ofEpochSecond(9999, 123456789))

class LogcatCollectorTest {
    lateinit var collector: LogcatCollector
    lateinit var startTimeProvider: NextLogcatStartTimeProvider
    lateinit var cidProvider: NextLogcatCidProvider
    lateinit var logcatOutput: String
    lateinit var mockPackageNameAllowList: PackageNameAllowList
    lateinit var mockPackageManagerClient: PackageManagerClient
    lateinit var kernelOopsDetector: LogcatLineProcessor
    lateinit var logcatSettings: LogcatSettings
    lateinit var logcatRunner: LogcatRunner
    var tempFile: File? = null

    @BeforeEach
    fun setUp() {
        val outputStreamSlot = slot<OutputStream>()
        val commandSlot = slot<LogcatCommand>()
        logcatRunner = mockk(relaxed = true) {
            coEvery {
                runLogcat(
                    capture(outputStreamSlot),
                    capture(commandSlot),
                    any()
                )
            } answers {
                logcatOutput.let { output ->
                    checkNotNull(output)
                    output.byteInputStream().use {
                        it.copyTo(outputStreamSlot.captured)
                    }
                }
            }
        }
        startTimeProvider = FakeNextLogcatStartTimeProvider(nextStart = AbsoluteTime(Instant.ofEpochSecond(0)))
        cidProvider = FakeNextLogcatCidProvider.incrementing()

        mockPackageManagerClient = mockk {
            coEvery { getPackageManagerReport(null) } returns PackageManagerReport(
                listOf(
                    Package(id = "android", userId = 1000),
                    Package(id = "com.memfault.bort", userId = 9008),
                    Package(id = "org.smartcompany.smartcupholder", userId = 9020),
                )
            )
        }

        mockPackageNameAllowList = mockk {
            every { contains(any()) } answers {
                firstArg() in setOf("android", "com.memfault.bort")
            }
        }

        kernelOopsDetector = mockk(relaxed = true)
        logcatSettings = object : LogcatSettings {
            override val dataSourceEnabled = true
            override val collectionInterval = ZERO
            override val commandTimeout = ZERO
            override val filterSpecs = emptyList<LogcatFilterSpec>()
            override val kernelOopsDataSourceEnabled = true
            override val kernelOopsRateLimitingSettings = RateLimitingSettings(0, ZERO.boxed(), 0)
        }

        collector = LogcatCollector(
            temporaryFileFactory = TestTemporaryFileFactory,
            nextLogcatStartTimeProvider = startTimeProvider,
            nextLogcatCidProvider = cidProvider,
            now = { FAKE_NOW },
            dataScrubber = DataScrubber(listOf(EmailScrubbingRule, CredentialScrubbingRule)),
            packageNameAllowList = mockPackageNameAllowList,
            packageManagerClient = mockPackageManagerClient,
            kernelOopsDetector = { kernelOopsDetector },
            logcatSettings = logcatSettings,
            logcatRunner = logcatRunner,
        )
    }

    @AfterEach
    fun tearDown() {
        tempFile?.delete()
    }

    private fun collect() =
        runBlocking {
            collector.collect()
        }.also { result -> result?.let { tempFile = result.file } }

    @Test
    fun happyPath() {
        val initialCid = cidProvider.cid
        val nextStartInstant = Instant.ofEpochSecond(1234, 56789)
        startTimeProvider.nextStart = AbsoluteTime(nextStartInstant)
        logcatOutput = """2021-01-18 12:34:02.000000000 +0000  9008  9008 I ServiceManager: Waiting...
            |2021-01-18 12:34:02.000000000 +0000  9008  9008 I PII: u: root p: hunter2
            |--------- beginning of kernel
            |2021-01-18 12:34:02.000000000 +0000  9008  9008 I AlsoPII: Mailing someone@mflt.io
            |2021-01-18 12:34:02.000000000 +0000 10020  9020 W SmartFruitbasket: seriously confidential stuff
            |2021-01-18 12:34:02.000000000 +0000  9013  9020 W SmartCupHolder: won't be scrubbed (< first app aid)
            |--------- switch to main
            |2021-01-18 12:34:02.000000000 +0000  9008  9008 W PackageManager: Installing app
            |""".trimMargin()
        val result = collect()!!
        assertEquals(
            """2021-01-18 12:34:02.000000000 +0000  9008  9008 I ServiceManager: Waiting...
            |2021-01-18 12:34:02.000000000 +0000  9008  9008 I PII: u: {{USERNAME}} p: {{PASSWORD}}
            |--------- beginning of kernel
            |2021-01-18 12:34:02.000000000 +0000  9008  9008 I AlsoPII: Mailing {{EMAIL}}
            |2021-01-18 12:34:02.000000000 +0000 10020  9020 W SmartFruitbasket: ***SCRUBBED-78818331***
            |2021-01-18 12:34:02.000000000 +0000  9013  9020 W SmartCupHolder: won't be scrubbed (< first app aid)
            |--------- switch to main
            |2021-01-18 12:34:02.000000000 +0000  9008  9008 W PackageManager: Installing app
            |""".trimMargin(),
            result.file.readText()
        )
        assertEquals(nextStartInstant, result.command.recentSince?.toInstant(ZoneOffset.UTC))
        assertEquals(initialCid, result.cid)
        val nextCid = cidProvider.cid
        assertNotEquals(initialCid, nextCid)
        assertEquals(nextCid, result.nextCid)
        assertEquals(nextCid, cidProvider.cid)
        assertEquals(
            AbsoluteTime(Instant.ofEpochSecond(1610973242, 1)), // Note: +1 nsec
            startTimeProvider.nextStart,
        )
        verify { kernelOopsDetector.process(any()) }
        verify(exactly = 1) { kernelOopsDetector.finish(any()) }
    }

    @Test
    fun whenLastLogLineFailedToParsefallbackToNowAsNextStart() {
        logcatOutput = "foo bar"
        val result = collect()
        assertEquals(
            FAKE_NOW,
            startTimeProvider.nextStart,
        )
        assertNotNull(result)
        verify { kernelOopsDetector.process(any()) }
        verify(exactly = 1) { kernelOopsDetector.finish(any()) }
    }

    @Test
    fun useLastDateIflastLogLineIsSeparator() {
        val nextStartInstant = Instant.ofEpochSecond(1234, 56789)
        startTimeProvider.nextStart = AbsoluteTime(nextStartInstant)
        logcatOutput = """2021-01-18 12:34:02.000000000 +0000  9008  9008 I ServiceManager: Waiting...
            |--------- switch to main
            |""".trimMargin()
        val result = collect()
        assertNotEquals(FAKE_NOW, startTimeProvider.nextStart)
        assertEquals(
            AbsoluteTime(Instant.ofEpochSecond(1610973242, 1)), // Note: +1 nsec
            startTimeProvider.nextStart,
        )
        assertNotNull(result)
        verify { kernelOopsDetector.process(any()) }
        verify(exactly = 1) { kernelOopsDetector.finish(any()) }
    }

    @Test
    fun emptyLogcatOutput() {
        logcatOutput = ""
        val initialCid = cidProvider.cid
        val result = collect()
        // Upload even when empty. If it is not uploaded, it causes confusion when there is no log file
        // around an event of interest to be found ("what happened, is it a Bort bug?").
        assertNotNull(result)
        assertEquals("", result!!.file.readText())
        // CID should have been rotated:
        assertEquals(initialCid, result.cid)
        assertNotEquals(initialCid, cidProvider.cid)
    }

    @Test
    fun logPrioritySerializerDeserializer() {
        LogcatPriority.values()
            .map {
                Pair(it.cliValue, """"${it.cliValue}"""")
            }.forEach { (literal, json) ->
                assertEquals(
                    LogcatPriority.getByCliValue(literal),
                    BortJson.decodeFromString(
                        LogcatPrioritySerializer,
                        json
                    )
                )
                assertEquals(
                    BortJson.encodeToString(LogcatPrioritySerializer, LogcatPriority.getByCliValue(literal)!!),
                    json,
                )
            }
    }

    @Test
    fun testAllowedUidSet() {
        val report = PackageManagerReport(
            listOf(
                Package(id = "android", userId = 1000),
                Package(id = "net.smartthings.smartfireplace", userId = 1001),
                Package(id = "net.smartthings.smartcarpet", userId = 1002),
                Package(id = "net.smartthings.smartshoe", userId = 1004),
                Package(id = "android", userId = 2000),
            )
        )

        val allowList = PackageNameAllowList { it?.contains("smart") ?: false }
        assertEquals(
            setOf(1001, 1002, 1004),
            report.toAllowedUids(allowList),
        )
    }
}
