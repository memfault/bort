package com.memfault.bort.dropbox

import com.memfault.bort.DropBoxEntryFileUploadPayload
import com.memfault.bort.FakeBootRelativeTimeProvider
import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.PackageNameAllowList
import com.memfault.bort.TombstoneFileUploadMetadata
import com.memfault.bort.logcat.FakeNextLogcatCidProvider
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.metrics.makeFakeMetricRegistry
import com.memfault.bort.metrics.metricForTraceTag
import com.memfault.bort.parsers.EXAMPLE_NATIVE_BACKTRACE
import com.memfault.bort.parsers.EXAMPLE_TOMBSTONE
import com.memfault.bort.parsers.Package
import com.memfault.bort.test.util.TestTemporaryFileFactory
import com.memfault.bort.time.BaseBootRelativeTime
import com.memfault.bort.tokenbucket.MockTokenBucketFactory
import com.memfault.bort.tokenbucket.MockTokenBucketStorage
import com.memfault.bort.tokenbucket.StoredTokenBucketMap
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueUpload
import com.memfault.bort.uploader.HandleEventOfInterest
import io.mockk.CapturingSlot
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.time.milliseconds
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

val TEST_BUCKET_CAPACITY = 3

class TombstoneEntryProcessorTest {
    lateinit var processor: UploadingEntryProcessor<TombstoneUploadingEntryProcessorDelegate>
    lateinit var mockEnqueueUpload: EnqueueUpload
    lateinit var fileUploadPayloadSlot: CapturingSlot<FileUploadPayload>
    lateinit var mockPackageManagerClient: PackageManagerClient
    lateinit var builtInMetricsStore: BuiltinMetricsStore
    lateinit var mockHandleEventOfInterest: HandleEventOfInterest
    lateinit var mockPackageNameAllowList: PackageNameAllowList

    @BeforeEach
    fun setUp() {
        mockEnqueueUpload = mockk(relaxed = true)
        mockHandleEventOfInterest = mockk(relaxed = true)

        fileUploadPayloadSlot = slot<FileUploadPayload>()
        every {
            mockEnqueueUpload.enqueue(any(), capture(fileUploadPayloadSlot), any(), any())
        } returns Unit

        mockPackageManagerClient = mockk()
        builtInMetricsStore = BuiltinMetricsStore(makeFakeMetricRegistry())
        mockPackageNameAllowList = mockk {
            every { contains(any()) } returns true
        }
        processor = UploadingEntryProcessor(
            delegate = TombstoneUploadingEntryProcessorDelegate(
                packageManagerClient = mockPackageManagerClient,
                tokenBucketStore = TokenBucketStore(
                    storage = MockTokenBucketStorage(StoredTokenBucketMap()),
                    getMaxBuckets = { 1 },
                    getTokenBucketFactory = {
                        MockTokenBucketFactory(
                            defaultCapacity = TEST_BUCKET_CAPACITY,
                            defaultPeriod = 1.milliseconds,
                            metrics = builtInMetricsStore,
                        )
                    }
                ),
            ),
            tempFileFactory = TestTemporaryFileFactory,
            enqueueUpload = mockEnqueueUpload,
            nextLogcatCidProvider = FakeNextLogcatCidProvider.incrementing(),
            bootRelativeTimeProvider = FakeBootRelativeTimeProvider,
            deviceInfoProvider = FakeDeviceInfoProvider(),
            builtinMetricsStore = builtInMetricsStore,
            handleEventOfInterest = mockHandleEventOfInterest,
            packageNameAllowList = mockPackageNameAllowList,
            combinedTimeProvider = FakeCombinedTimeProvider,
        )
    }

    @TestFactory
    fun enqueuesFileWithPackageInfo() = listOf(
        Triple("native backtrace", EXAMPLE_NATIVE_BACKTRACE, "foo"),
        Triple("tombstone", EXAMPLE_TOMBSTONE, "com.android.chrome"),
    ).map { (name, text, expectedProcessName) ->
        DynamicTest.dynamicTest("enqueuesFileWithPackageInfo for $name") {
            // @BeforeEach doesn't work with DynamicTest... :/
            clearMocks(mockHandleEventOfInterest)

            coEvery {
                mockPackageManagerClient.findPackagesByProcessName(expectedProcessName)
            } returns PACKAGE_FIXTURE
            runBlocking {
                processor.process(mockEntry(text = text))
                val payload = fileUploadPayloadSlot.captured as DropBoxEntryFileUploadPayload
                val metadata = payload.metadata as TombstoneFileUploadMetadata
                assertEquals(listOf(PACKAGE_FIXTURE.toUploaderPackage()), metadata.packages)
                verify(exactly = 1) { mockHandleEventOfInterest.handleEventOfInterest(any<BaseBootRelativeTime>()) }
            }
        }
    }

    @Test
    fun enqueuesFileThatFailedToParseWithoutPackageInfo() {
        // Even though Bort's parsing failed to parse out the processName, ensure it's uploaded it anyway:
        runBlocking {
            processor.process(mockEntry(text = "not_empty_but_invalid"))
            val payload = fileUploadPayloadSlot.captured as DropBoxEntryFileUploadPayload
            val metadata = payload.metadata as TombstoneFileUploadMetadata
            assertTrue(metadata.packages.isEmpty())
        }
    }

    @Test
    fun rateLimiting() {
        coEvery {
            mockPackageManagerClient.findPackagesByProcessName(any())
        } returns PACKAGE_FIXTURE

        runBlocking {
            val runs = 15
            (0..runs).forEach {
                processor.process(mockEntry(text = EXAMPLE_TOMBSTONE, tag_ = "SYSTEM_TOMBSTONE"))
            }
            verify(exactly = TEST_BUCKET_CAPACITY) { mockEnqueueUpload.enqueue(any(), any(), any(), any()) }

            // Check that dropped items are counted correctly
            assert(
                (runs - TEST_BUCKET_CAPACITY + 1).toFloat()
                    == builtInMetricsStore.collect("rate_limit_applied_dropbox_SYSTEM_TOMBSTONE")
            )
            verify(exactly = TEST_BUCKET_CAPACITY) {
                mockHandleEventOfInterest.handleEventOfInterest(any<BaseBootRelativeTime>())
            }
        }
    }

    @Test
    fun testMetricEntryCounting() {
        coEvery {
            mockPackageManagerClient.findPackagesByProcessName(any())
        } returns PACKAGE_FIXTURE

        runBlocking {
            val processTags = mapOf(
                "TEST_TAG_1" to 10,
                "TEST_TAG_2" to 5,
                "TEST_TAG_3" to 3,
            )

            processTags.forEach { (tag, count) ->
                repeat(count) {
                    processor.process(mockEntry(text = EXAMPLE_NATIVE_BACKTRACE, tag_ = tag))
                }
            }

            val expectedMap = processTags
                .map { (tag, count) ->
                    metricForTraceTag(tag) to JsonPrimitive(count.toFloat())
                }
                .toMap()

            // Check that it contains all expected keys, it might (and does) contain at least another
            // key regarding rate limiting.
            assert(
                builtInMetricsStore
                    .collectMetrics()
                    .entries
                    .containsAll(expectedMap.entries)
            )
        }
    }

    @Test
    fun doesNotEnqueueWhenNotAllowed() {
        every { mockPackageNameAllowList.contains(any()) } returns false

        runBlocking {
            processor.process(mockEntry(text = "not_empty_but_invalid"))
            assertFalse(fileUploadPayloadSlot.isCaptured)
        }
    }

    @Test
    fun doesNotEnqueueWhenEmpty() {
        runBlocking {
            processor.process(mockEntry(text = ""))
            assertFalse(fileUploadPayloadSlot.isCaptured)
        }
    }

    @Test
    fun nullEntryInputStream() {
        val entry = mockEntry(text = "not_empty_but_invalid")
        every { entry.getInputStream() } returns null
        runBlocking {
            // Used to raise an exception, but should not!
            processor.process(entry)
        }
    }
}

private val PACKAGE_FIXTURE = Package(
    id = "com.android.chrome",
    userId = 1000,
    codePath = "/data/app.apk",
    versionCode = 1,
    versionName = "1.0.0"
)
