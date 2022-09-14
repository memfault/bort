package com.memfault.bort.dropbox

import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.LogcatCollectionId
import com.memfault.bort.StructuredLogFileUploadPayload
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.metrics.makeFakeMetricRegistry
import com.memfault.bort.test.util.TestTemporaryFileFactory
import com.memfault.bort.tokenbucket.MockTokenBucketFactory
import com.memfault.bort.tokenbucket.MockTokenBucketStorage
import com.memfault.bort.tokenbucket.StoredTokenBucketMap
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueUpload
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val STRUCTURED_LOG_TEST_BUCKET_CAPACITY = 3

/**
 * Using robolectric because of a parser dependency in android.util.JsonReader/Writer
 * @see StructuredLogEntryProcessorTest
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StructuredLogEntryProcessorTest {
    lateinit var processor: StructuredLogEntryProcessor
    lateinit var mockEnqueueUpload: EnqueueUpload
    lateinit var fileUploadPayloadSlot: CapturingSlot<FileUploadPayload>
    lateinit var builtInMetricsStore: BuiltinMetricsStore
    var dataSourceEnabled: Boolean = true

    @Before
    fun setUp() {
        dataSourceEnabled = true
        mockEnqueueUpload = mockk(relaxed = true)

        fileUploadPayloadSlot = slot()
        every {
            mockEnqueueUpload.enqueue(any(), capture(fileUploadPayloadSlot), any(), any())
        } returns Unit
        builtInMetricsStore = BuiltinMetricsStore(makeFakeMetricRegistry())

        processor = StructuredLogEntryProcessor(
            temporaryFileFactory = TestTemporaryFileFactory,
            enqueueUpload = mockEnqueueUpload,
            deviceInfoProvider = FakeDeviceInfoProvider(),
            tokenBucketStore = TokenBucketStore(
                storage = MockTokenBucketStorage(StoredTokenBucketMap()),
                getMaxBuckets = { 1 },
                getTokenBucketFactory = {
                    MockTokenBucketFactory(
                        defaultCapacity = STRUCTURED_LOG_TEST_BUCKET_CAPACITY,
                        defaultPeriod = 1.milliseconds,
                        metrics = builtInMetricsStore,
                    )
                }
            ),
            combinedTimeProvider = FakeCombinedTimeProvider,
            structuredLogDataSourceEnabledConfig = { dataSourceEnabled }
        )
    }

    @Test
    fun enqueues() {
        runBlocking {
            val info = FakeDeviceInfoProvider().getDeviceInfo()

            processor.process(mockEntry(text = VALID_STRUCTURED_LOG_FIXTURE))
            val payload = fileUploadPayloadSlot.captured as StructuredLogFileUploadPayload
            assertEquals(LogcatCollectionId(UUID.fromString("00000000-0000-0000-0000-000000000002")), payload.cid)
            assertEquals(LogcatCollectionId(UUID.fromString("00000000-0000-0000-0000-000000000003")), payload.nextCid)
            assertEquals(FakeCombinedTimeProvider.now(), payload.collectionTime)
            assertEquals(info.deviceSerial, payload.deviceSerial)
            assertEquals(info.hardwareVersion, payload.hardwareVersion)
            assertEquals(info.softwareVersion, payload.softwareVersion)
        }
    }

    @Test
    fun rateLimiting() {
        runBlocking {
            val runs = 15
            (0..runs).forEach {
                processor.process(mockEntry(text = VALID_STRUCTURED_LOG_FIXTURE, tag_ = "memfault_structured"))
            }
            verify(exactly = STRUCTURED_LOG_TEST_BUCKET_CAPACITY) {
                mockEnqueueUpload.enqueue(any(), any(), any(), any())
            }
            assert(
                (runs - STRUCTURED_LOG_TEST_BUCKET_CAPACITY + 1).toFloat()
                    == builtInMetricsStore.collect("rate_limit_applied_structured")
            )
        }
    }

    @Test
    fun noProcessingWhenDataSourceDisabled() {
        dataSourceEnabled = false
        runBlocking {
            processor.process(mockEntry(text = VALID_STRUCTURED_LOG_FIXTURE, tag_ = "memfault_structured"))
            verify(exactly = 0) { mockEnqueueUpload.enqueue(any(), any(), any(), any()) }
        }
    }
}
