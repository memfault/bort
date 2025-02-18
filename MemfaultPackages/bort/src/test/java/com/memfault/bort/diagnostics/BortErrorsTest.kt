package com.memfault.bort.diagnostics

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.TimezoneWithId
import com.memfault.bort.chronicler.ClientChroniclerEntry
import com.memfault.bort.clientserver.MarMetadata.ClientChroniclerMarMetadata
import com.memfault.bort.diagnostics.BortErrorType.BatteryStatsHistoryParseError
import com.memfault.bort.diagnostics.BortErrorType.BortRateLimit
import com.memfault.bort.settings.BatchMarUploads
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.uploader.EnqueueUpload
import io.mockk.Called
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.time.Duration.Companion.days

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class BortErrorsTest {
    private var batchMars = true
    private val batchMarUploads = BatchMarUploads { batchMars }
    private val enqueueUpload: EnqueueUpload = mockk(relaxed = true)
    private val combinedTimeProvider: CombinedTimeProvider = FakeCombinedTimeProvider
    private val absoluteTimeMs: Long = 1714150878000
    private val absoluteTimeProvider = { AbsoluteTime(Instant.ofEpochMilli(absoluteTimeMs)) }
    private lateinit var bortErrors: BortErrors

    private lateinit var db: BortErrorsDb

    private val historyParserClientChroniclerEntry = ClientChroniclerEntry(
        eventType = "AndroidBatteryStatsHistoryParseError",
        source = "android-batterystats",
        eventData = mapOf("a" to "b", "c" to "d"),
        entryTime = absoluteTimeProvider(),
        timezone = TimezoneWithId.deviceDefault,
    )
    private val historyParseBortError = BortError(
        timestamp = historyParserClientChroniclerEntry.entryTime,
        type = BatteryStatsHistoryParseError,
        eventData = historyParserClientChroniclerEntry.eventData,
    )

    private val oldRateLimitClientChroniclerEntry = ClientChroniclerEntry(
        eventType = "AndroidDeviceCollectionRateLimitExceeded",
        source = "android-collection-rate-limits",
        eventData = mapOf("1" to "2", "3" to "4"),
        entryTime = absoluteTimeProvider().minus(2.days),
        timezone = TimezoneWithId.deviceDefault,
    )
    private val oldRateLimitBortError = BortError(
        timestamp = oldRateLimitClientChroniclerEntry.entryTime,
        type = BortRateLimit,
        eventData = oldRateLimitClientChroniclerEntry.eventData,
    )

    private fun verifyUploaded(entries: List<ClientChroniclerEntry>) {
        coVerify {
            enqueueUpload.enqueue(
                file = any(),
                metadata = ClientChroniclerMarMetadata(entries),
                collectionTime = combinedTimeProvider.now(),
            )
        }
    }

    @Before
    fun createDB() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, BortErrorsDb::class.java)
            .fallbackToDestructiveMigration().allowMainThreadQueries().build()
        bortErrors = BortErrors(db, batchMarUploads, enqueueUpload, combinedTimeProvider, absoluteTimeProvider)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun uploadEventImmediately() = runTest {
        batchMars = false
        bortErrors.add(BatteryStatsHistoryParseError, historyParserClientChroniclerEntry.eventData)
        verifyUploaded(listOf(historyParserClientChroniclerEntry))

        bortErrors.add(
            oldRateLimitClientChroniclerEntry.entryTime,
            BortRateLimit,
            oldRateLimitClientChroniclerEntry.eventData,
        )
        verifyUploaded(listOf(oldRateLimitClientChroniclerEntry))

        clearMocks(enqueueUpload)
        bortErrors.enqueueBortErrorsForUpload()
        coVerify { enqueueUpload wasNot Called }

        assertThat(bortErrors.getAllErrors()).isEqualTo(
            listOf(oldRateLimitBortError, historyParseBortError),
        )

        // Adding another error triggers cleanup of the old one
        bortErrors.add(BatteryStatsHistoryParseError, historyParserClientChroniclerEntry.eventData)
        verifyUploaded(listOf(historyParserClientChroniclerEntry))
        assertThat(bortErrors.getAllErrors()).isEqualTo(
            listOf(historyParseBortError, historyParseBortError),
        )
    }

    @Test
    fun doesNotUploadEventImmediately() = runTest {
        batchMars = true
        bortErrors.add(BatteryStatsHistoryParseError, historyParserClientChroniclerEntry.eventData)
        bortErrors.add(
            oldRateLimitClientChroniclerEntry.entryTime,
            BortRateLimit,
            oldRateLimitClientChroniclerEntry.eventData,
        )
        coVerify { enqueueUpload wasNot Called }

        // All errors returned because cleanup hasn't run yet
        assertThat(bortErrors.getAllErrors()).isEqualTo(
            listOf(oldRateLimitBortError, historyParseBortError),
        )

        bortErrors.enqueueBortErrorsForUpload()
        verifyUploaded(listOf(oldRateLimitClientChroniclerEntry, historyParserClientChroniclerEntry))

        // Old error was cleaned up
        assertThat(bortErrors.getAllErrors()).containsExactly(historyParseBortError)
    }
}
