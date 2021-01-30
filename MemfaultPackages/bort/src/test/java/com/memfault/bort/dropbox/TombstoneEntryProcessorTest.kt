package com.memfault.bort.dropbox

import com.memfault.bort.DropBoxEntryFileUploadPayload
import com.memfault.bort.FakeBootRelativeTimeProvider
import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.TombstoneFileUploadMetadata
import com.memfault.bort.parsers.EXAMPLE_NATIVE_BACKTRACE
import com.memfault.bort.parsers.EXAMPLE_TOMBSTONE
import com.memfault.bort.parsers.Package
import com.memfault.bort.test.util.TestTemporaryFileFactory
import com.memfault.bort.tokenbucket.MockTokenBucketFactory
import com.memfault.bort.tokenbucket.MockTokenBucketStorage
import com.memfault.bort.tokenbucket.StoredTokenBucketMap
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueFileUpload
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.time.milliseconds
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

val TEST_BUCKET_CAPACITY = 3

class TombstoneEntryProcessorTest {
    lateinit var processor: TombstoneEntryProcessor
    lateinit var mockEnqueueFileUpload: EnqueueFileUpload
    lateinit var fileUploadPayloadSlot: CapturingSlot<FileUploadPayload>
    lateinit var mockPackageManagerClient: PackageManagerClient

    @BeforeEach
    fun setUp() {
        mockEnqueueFileUpload = mockk(relaxed = true)

        fileUploadPayloadSlot = slot<FileUploadPayload>()
        every {
            mockEnqueueFileUpload(any(), capture(fileUploadPayloadSlot), any())
        } returns Unit

        mockPackageManagerClient = mockk()
        processor = TombstoneEntryProcessor(
            tempFileFactory = TestTemporaryFileFactory,
            enqueueFileUpload = mockEnqueueFileUpload,
            bootRelativeTimeProvider = FakeBootRelativeTimeProvider,
            packageManagerClient = mockPackageManagerClient,
            deviceInfoProvider = FakeDeviceInfoProvider,
            tokenBucketStore = TokenBucketStore(
                storage = MockTokenBucketStorage(StoredTokenBucketMap()),
                maxBuckets = 1,
                tokenBucketFactory = MockTokenBucketFactory(
                    defaultCapacity = TEST_BUCKET_CAPACITY,
                    defaultPeriod = 1.milliseconds,
                ),
            ),
        )
    }

    @TestFactory
    fun enqueuesFileWithPackageInfo() = listOf(
        Triple("native backtrace", EXAMPLE_NATIVE_BACKTRACE, "foo"),
        Triple("tombstone", EXAMPLE_TOMBSTONE, "com.android.chrome"),
    ).map { (name, text, expectedProcessName) ->
        DynamicTest.dynamicTest("enqueuesFileWithPackageInfo for $name") {
            coEvery {
                mockPackageManagerClient.findPackagesByProcessName(expectedProcessName)
            } returns PACKAGE_FIXTURE
            runBlocking {
                processor.process(mockEntry(text = text))
                val payload = fileUploadPayloadSlot.captured as DropBoxEntryFileUploadPayload
                val metadata = payload.metadata as TombstoneFileUploadMetadata
                assertEquals(listOf(PACKAGE_FIXTURE.toUploaderPackage()), metadata.packages)
            }
        }
    }

    @Test
    fun enqueuesFileThatFailedToParseWithoutPackageInfo() {
        // Even though Bort's parsing failed to parse out the processName, ensure it's uploaded it anyway:
        runBlocking {
            processor.process(mockEntry(text = ""))
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
            (0..15).forEach {
                processor.process(mockEntry(text = EXAMPLE_TOMBSTONE, tag_ = "SYSTEM_TOMBSTONE"))
            }
            verify(exactly = TEST_BUCKET_CAPACITY) { mockEnqueueFileUpload(any(), any(), any()) }
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
