package com.memfault.bort.dropbox

import com.memfault.bort.FakeBootRelativeTimeProvider
import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.PackageNameAllowList
import com.memfault.bort.clientserver.MarMetadata
import com.memfault.bort.logcat.FakeNextLogcatCidProvider
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.metrics.CrashHandler
import com.memfault.bort.parsers.EXAMPLE_TOMBSTONE
import com.memfault.bort.parsers.Package
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.test.util.TestTemporaryFileFactory
import com.memfault.bort.time.BaseBootRelativeTime
import com.memfault.bort.uploader.EnqueueUpload
import com.memfault.bort.uploader.HandleEventOfInterest
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TombstoneEntryProcessorTest {
    lateinit var processor: UploadingEntryProcessor<TombstoneUploadingEntryProcessorDelegate>
    lateinit var mockEnqueueUpload: EnqueueUpload
    lateinit var marMetadataSlot: CapturingSlot<MarMetadata>
    lateinit var mockPackageManagerClient: PackageManagerClient
    lateinit var builtInMetricsStore: BuiltinMetricsStore
    lateinit var mockHandleEventOfInterest: HandleEventOfInterest
    lateinit var mockPackageNameAllowList: PackageNameAllowList
    lateinit var crashHandler: CrashHandler
    private var allowedByRateLimit = true

    @BeforeEach
    fun setUp() {
        mockEnqueueUpload = mockk(relaxed = true)
        mockHandleEventOfInterest = mockk(relaxed = true)

        marMetadataSlot = slot()
        every {
            mockEnqueueUpload.enqueue(any(), capture(marMetadataSlot), any())
        } returns Unit

        mockPackageManagerClient = mockk()
        builtInMetricsStore = BuiltinMetricsStore()
        mockPackageNameAllowList = mockk {
            every { contains(any()) } returns true
        }
        crashHandler = mockk(relaxed = true)
        processor = UploadingEntryProcessor(
            delegate = TombstoneUploadingEntryProcessorDelegate(
                packageManagerClient = mockPackageManagerClient,
                tokenBucketStore = mockk {
                    every { takeSimple(any(), any(), any()) } answers { allowedByRateLimit }
                },
                tempFileFactory = TestTemporaryFileFactory,
                scrubTombstones = { false },
            ),
            tempFileFactory = TestTemporaryFileFactory,
            enqueueUpload = mockEnqueueUpload,
            nextLogcatCidProvider = FakeNextLogcatCidProvider.incrementing(),
            bootRelativeTimeProvider = FakeBootRelativeTimeProvider,
            builtinMetricsStore = builtInMetricsStore,
            handleEventOfInterest = mockHandleEventOfInterest,
            packageNameAllowList = mockPackageNameAllowList,
            combinedTimeProvider = FakeCombinedTimeProvider,
            crashHandler = crashHandler,
        )
    }

    @Test
    fun enqueuesFileWithPackageInfo() = runTest {
        coEvery {
            mockPackageManagerClient.getPackageManagerReport()
        } returns PackageManagerReport(listOf(PACKAGE_FIXTURE))
        processor.process(mockEntry(text = EXAMPLE_TOMBSTONE))
        val metadata = marMetadataSlot.captured as MarMetadata.DropBoxMarMetadata
        assertEquals(listOf(PACKAGE_FIXTURE.toUploaderPackage()), metadata.packages)
        verify(exactly = 1) { mockHandleEventOfInterest.handleEventOfInterest(any<BaseBootRelativeTime>()) }
    }

    @Test
    fun enqueuesFileThatFailedToParseWithoutPackageInfo() = runTest {
        // Even though Bort's parsing failed to parse out the processName, ensure it's uploaded it anyway:
        processor.process(mockEntry(text = "not_empty_but_invalid"))
        val metadata = marMetadataSlot.captured as MarMetadata.DropBoxMarMetadata
        assertTrue(metadata.packages.isEmpty())
    }

    @Test
    fun rateLimiting() = runTest {
        coEvery {
            mockPackageManagerClient.getPackageManagerReport()
        } returns PackageManagerReport(listOf(PACKAGE_FIXTURE))

        allowedByRateLimit = true
        processor.process(mockEntry(text = EXAMPLE_TOMBSTONE, tag_ = "SYSTEM_TOMBSTONE"))
        allowedByRateLimit = false
        processor.process(mockEntry(text = EXAMPLE_TOMBSTONE, tag_ = "SYSTEM_TOMBSTONE"))
        verify(exactly = 1) { mockEnqueueUpload.enqueue(any(), any(), any()) }
        verify(exactly = 1) {
            mockHandleEventOfInterest.handleEventOfInterest(any<BaseBootRelativeTime>())
        }
    }

    @Test
    fun doesNotEnqueueWhenNotAllowed() = runTest {
        every { mockPackageNameAllowList.contains(any()) } returns false

        processor.process(mockEntry(text = "not_empty_but_invalid"))
        assertFalse(marMetadataSlot.isCaptured)
    }

    @Test
    fun doesNotEnqueueWhenEmpty() = runTest {
        processor.process(mockEntry(text = ""))
        assertFalse(marMetadataSlot.isCaptured)
    }

    @Test
    fun nullEntryInputStream() = runTest {
        val entry = mockEntry(text = "not_empty_but_invalid")
        every { entry.getInputStream() } returns null
        // Used to raise an exception, but should not!
        processor.process(entry)
    }
}

private val PACKAGE_FIXTURE = Package(
    id = "com.android",
    userId = 1000,
    versionCode = 1,
    versionName = "1.0.0",
)
