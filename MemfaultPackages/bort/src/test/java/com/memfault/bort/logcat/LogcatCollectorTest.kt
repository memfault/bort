package com.memfault.bort.logcat

import com.memfault.bort.BortJson
import com.memfault.bort.CredentialScrubbingRule
import com.memfault.bort.DataScrubber
import com.memfault.bort.EmailScrubbingRule
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.PackageNameAllowList
import com.memfault.bort.parsers.Package
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatPriority
import com.memfault.bort.shared.LogcatPrioritySerializer
import com.memfault.bort.test.util.TestTemporaryFileFactory
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BaseAbsoluteTime
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.minutes
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

data class FakeNextLogcatStartTimeProvider(
    override var nextStart: BaseAbsoluteTime
) : NextLogcatStartTimeProvider

private val FAKE_NOW = AbsoluteTime(Instant.ofEpochSecond(9999, 123456789))

class LogcatCollectorTest {
    lateinit var collector: LogcatCollector
    lateinit var startTimeProvider: NextLogcatStartTimeProvider
    lateinit var cidProvider: NextLogcatCidProvider
    lateinit var mockRunLogcat: suspend (outputStream: OutputStream, command: LogcatCommand, timeout: Duration) -> Unit
    lateinit var logcatOutput: String
    lateinit var mockPackageNameAllowList: PackageNameAllowList
    lateinit var mockPackageManagerClient: PackageManagerClient
    var tempFile: File? = null

    @BeforeEach
    fun setUp() {
        val outputStreamSlot = slot<OutputStream>()
        val commandSlot = slot<LogcatCommand>()
        mockRunLogcat = mockk()
        coEvery { mockRunLogcat(capture(outputStreamSlot), capture(commandSlot), any()) } coAnswers {
            logcatOutput.let { output ->
                checkNotNull(output)
                output.byteInputStream().use {
                    it.copyTo(outputStreamSlot.captured)
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

        collector = LogcatCollector(
            temporaryFileFactory = TestTemporaryFileFactory,
            nextLogcatStartTimeProvider = startTimeProvider,
            nextLogcatCidProvider = cidProvider,
            runLogcat = mockRunLogcat,
            now = { FAKE_NOW },
            filterSpecsConfig = ::emptyList,
            dataScrubber = { DataScrubber(listOf(EmailScrubbingRule, CredentialScrubbingRule)) },
            timeoutConfig = 1::minutes,
            packageNameAllowList = mockPackageNameAllowList,
            packageManagerClient = mockPackageManagerClient,
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
    }

    @Test
    fun emptyLogcatOutput() {
        logcatOutput = ""
        val initialCid = cidProvider.cid
        val result = collect()
        assertNull(result)
        // CID should not have been rotated:
        assertEquals(initialCid, cidProvider.cid)
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
