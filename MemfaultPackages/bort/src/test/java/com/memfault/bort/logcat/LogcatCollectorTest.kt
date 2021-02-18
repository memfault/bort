package com.memfault.bort.logcat

import com.memfault.bort.BortJson
import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatPriority
import com.memfault.bort.shared.LogcatPrioritySerializer
import com.memfault.bort.test.util.TestTemporaryFileFactory
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BaseAbsoluteTime
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
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
    lateinit var mockRunLogcat: suspend (outputStream: OutputStream, command: LogcatCommand) -> Unit
    lateinit var logcatOutput: String
    var tempFile: File? = null

    @BeforeEach
    fun setUp() {
        val outputStreamSlot = slot<OutputStream>()
        val commandSlot = slot<LogcatCommand>()
        mockRunLogcat = mockk()
        coEvery { mockRunLogcat(capture(outputStreamSlot), capture(commandSlot)) } coAnswers {
            logcatOutput.let { output ->
                checkNotNull(output)
                output.byteInputStream().use {
                    it.copyTo(outputStreamSlot.captured)
                }
            }
        }
        startTimeProvider = FakeNextLogcatStartTimeProvider(nextStart = AbsoluteTime(Instant.ofEpochSecond(0)))
        cidProvider = FakeNextLogcatCidProvider.incrementing()
        collector = LogcatCollector(
            temporaryFileFactory = TestTemporaryFileFactory,
            nextLogcatStartTimeProvider = startTimeProvider,
            nextLogcatCidProvider = cidProvider,
            runLogcat = mockRunLogcat,
            now = { FAKE_NOW },
            filterSpecsConfig = ::emptyList
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
        logcatOutput = "2021-01-18 12:34:02.000000000 +0000  9008  9008 I ServiceManager: Waiting..."
        val result = collect()!!
        assertEquals(logcatOutput, result.file.readText())
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
}
