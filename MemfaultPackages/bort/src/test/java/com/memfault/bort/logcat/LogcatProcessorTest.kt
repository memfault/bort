package com.memfault.bort.logcat

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import com.memfault.bort.BortJson
import com.memfault.bort.CredentialScrubbingRule
import com.memfault.bort.DataScrubber
import com.memfault.bort.EmailScrubbingRule
import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.PackageNameAllowList
import com.memfault.bort.parsers.Package
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.settings.LogcatCollectionMode
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.settings.RateLimitingSettings
import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.LogcatFormat
import com.memfault.bort.shared.LogcatFormatModifier
import com.memfault.bort.shared.LogcatPriority
import com.memfault.bort.shared.LogcatPrioritySerializer
import com.memfault.bort.test.util.TestTemporaryFileFactory
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.boxed
import com.memfault.bort.uploader.FileUploadHoldingArea
import com.memfault.bort.uploader.PendingFileUploadEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes

private val FAKE_NOW = AbsoluteTime(Instant.ofEpochSecond(9999, 123456789))

class LogcatProcessorTest {
    private lateinit var processor: LogcatProcessor
    private lateinit var cidProvider: NextLogcatCidProvider
    private lateinit var logcatOutput: String
    private lateinit var mockPackageNameAllowList: PackageNameAllowList
    private lateinit var mockPackageManagerClient: PackageManagerClient
    private lateinit var kernelOopsDetector: LogcatLineProcessor
    private lateinit var selinuxViolationLogcatDetector: SelinuxViolationLogcatDetector
    private lateinit var logcatSettings: LogcatSettings
    private lateinit var storagedDiskWearLogcatDetector: StoragedDiskWearLogcatDetector
    private val fileUploadHoldingArea: FileUploadHoldingArea = mockk {
        every { add(any()) } answers { fileUploadEntry = arg(0) }
    }
    private var collectionMode = LogcatCollectionMode.PERIODIC
    private var fileUploadEntry: PendingFileUploadEntry? = null
    private val command = LogcatCommand(
        format = LogcatFormat.THREADTIME,
        formatModifiers = listOf(
            LogcatFormatModifier.NSEC,
            LogcatFormatModifier.UTC,
            LogcatFormatModifier.YEAR,
            LogcatFormatModifier.UID,
        ),
    )

    @Before
    fun setUp() {
        cidProvider = FakeNextLogcatCidProvider.incrementing()

        mockPackageManagerClient = mockk {
            coEvery { getPackageManagerReport() } returns PackageManagerReport(
                listOf(
                    Package(id = "android", userId = 1000),
                    Package(id = "com.memfault.bort", userId = 9008),
                    Package(id = "org.smartcompany.smartcupholder", userId = 9020),
                ),
            )
        }

        mockPackageNameAllowList = mockk {
            every { contains(any()) } answers {
                firstArg() in setOf("android", "com.memfault.bort")
            }
        }

        kernelOopsDetector = mockk(relaxed = true)
        selinuxViolationLogcatDetector = mockk(relaxed = true)
        logcatSettings = object : LogcatSettings {
            override val dataSourceEnabled = true
            override val collectionInterval = ZERO
            override val commandTimeout = ZERO
            override val filterSpecs = emptyList<LogcatFilterSpec>()
            override val kernelOopsDataSourceEnabled = true
            override val kernelOopsRateLimitingSettings = RateLimitingSettings(0, ZERO.boxed(), 0)
            override val storeUnsampled: Boolean = false
            override val collectionMode = LogcatCollectionMode.PERIODIC
            override val continuousLogDumpThresholdBytes: Int = 128 * 1024
            override val continuousLogDumpThresholdTime: Duration = 30.minutes
            override val continuousLogDumpWrappingTimeout: Duration = 30.minutes
            override val logs2metricsConfig: JsonObject get() = TODO("not used")
        }

        storagedDiskWearLogcatDetector = StoragedDiskWearLogcatDetector()
        val factories = setOf(
            object : LogcatLineProcessor.Factory {
                override fun create(): LogcatLineProcessor = kernelOopsDetector
            },
            object : LogcatLineProcessor.Factory {
                override fun create(): LogcatLineProcessor = selinuxViolationLogcatDetector
            },
            object : LogcatLineProcessor.Factory {
                override fun create(): LogcatLineProcessor = storagedDiskWearLogcatDetector
            },
        )

        processor = LogcatProcessor(
            temporaryFileFactory = TestTemporaryFileFactory,
            lineProcessorFactories = factories,
            packageManagerClient = mockPackageManagerClient,
            packageNameAllowList = mockPackageNameAllowList,
            dataScrubber = DataScrubber(cleaners = { listOf(EmailScrubbingRule, CredentialScrubbingRule) }),
            combinedTimeProvider = FakeCombinedTimeProvider,
            nextLogcatCidProvider = cidProvider,
            fileUploadingArea = fileUploadHoldingArea,
        )
    }

    private suspend fun collect() = processor.process(logcatOutput.byteInputStream(), command, collectionMode)

    @Test
    fun happyPath() = runTest {
        val initialCid = cidProvider.cid
        logcatOutput = """2021-01-18 12:34:02.000000000 +0000  9008  9008 I ServiceManager: Waiting...
            |2021-01-18 12:34:02.000000000 +0000  9008  9008 I PII: u: root p: hunter2
            |--------- beginning of kernel
            |2021-01-18 12:34:02.000000000 +0000  9008  9008 I AlsoPII: Mailing someone@mflt.io
            |2021-01-18 12:34:02.000000000 +0000 10020  9020 W SmartFruitbasket: seriously confidential stuff
            |2021-01-18 12:34:02.000000000 +0000  9013  9020 W SmartCupHolder: won't be scrubbed (< first app aid)
            |--------- switch to main
            |2021-01-18 12:34:02.000000000 +0000  9008  9008 W PackageManager: Installing app
            |
        """.trimMargin()
        val result = collect()
        val uploadEntry = fileUploadEntry
        checkNotNull(result)
        checkNotNull(uploadEntry)
        assertThat(uploadEntry.file.readText()).isEqualTo(
            """2021-01-18 12:34:02.000000000 +0000  9008  9008 I ServiceManager: Waiting...
            |2021-01-18 12:34:02.000000000 +0000  9008  9008 I PII: u: {{USERNAME}} p: {{PASSWORD}}
            |--------- beginning of kernel
            |2021-01-18 12:34:02.000000000 +0000  9008  9008 I AlsoPII: Mailing {{EMAIL}}
            |2021-01-18 12:34:02.000000000 +0000 10020  9020 W SmartFruitbasket: ***SCRUBBED-78818331***
            |2021-01-18 12:34:02.000000000 +0000  9013  9020 W SmartCupHolder: won't be scrubbed (< first app aid)
            |--------- switch to main
            |2021-01-18 12:34:02.000000000 +0000  9008  9008 W PackageManager: Installing app
            |
            """.trimMargin(),

        )
        assertThat(uploadEntry.payload.cid).isEqualTo(initialCid)
        val nextCid = cidProvider.cid
        assertThat(nextCid).isNotEqualTo(initialCid)
        assertThat(uploadEntry.payload.nextCid).isEqualTo(nextCid)
        assertThat(cidProvider.cid).isEqualTo(nextCid)
        coVerify { kernelOopsDetector.process(any(), any()) }
        coVerify(exactly = 1) { kernelOopsDetector.finish(any()) }
        assertThat(result.timeStart).isEqualTo(Instant.ofEpochSecond(1610973242))
        assertThat(result.timeEnd).isEqualTo(Instant.ofEpochSecond(1610973242, 1))
    }

    @Test
    fun useLastDateIflastLogLineIsSeparator() = runTest {
        logcatOutput = """2021-01-18 12:34:02.000000000 +0000  9008  9008 I ServiceManager: Waiting...
            |--------- switch to main
            |
        """.trimMargin()
        val result = collect()
        checkNotNull(result)
        coVerify { kernelOopsDetector.process(any(), any()) }
        coVerify(exactly = 1) { kernelOopsDetector.finish(any()) }
        assertThat(result.timeStart).isEqualTo(Instant.ofEpochSecond(1610973242))
        assertThat(result.timeEnd).isEqualTo(Instant.ofEpochSecond(1610973242, 1))
    }

    @Test
    fun logPrioritySerializerDeserializer() = runTest {
        LogcatPriority.values()
            .map {
                Pair(it.cliValue, """"${it.cliValue}"""")
            }.forEach { (literal, json) ->
                assertThat(
                    BortJson.decodeFromString(
                        LogcatPrioritySerializer,
                        json,
                    ),
                ).isEqualTo(LogcatPriority.getByCliValue(literal))
                assertThat(
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
            ),
        )

        val allowList = PackageNameAllowList { it?.contains("smart") ?: false }
        assertThat(report.toAllowedUids(allowList)).containsExactlyInAnyOrder(1001, 1002, 1004)
    }
}
