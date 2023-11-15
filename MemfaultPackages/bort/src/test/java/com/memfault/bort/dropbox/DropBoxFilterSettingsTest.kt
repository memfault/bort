package com.memfault.bort.dropbox

import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.settings.DropBoxSettings
import com.memfault.bort.settings.RateLimitingSettings
import com.memfault.bort.time.boxed
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class DropBoxFilterSettingsTest {

    @RelaxedMockK
    lateinit var mockEntryProcessor: EntryProcessor
    private var mockGetExcludedTags: Set<String> = setOf()
    lateinit var entryProcessors: DropBoxEntryProcessors
    private var bortEnabled = true

    private val mockRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 10,
        defaultPeriod = 15.minutes.boxed(),
        maxBuckets = 1,
    )
    private val bortEnabledProvider = object : BortEnabledProvider {
        override fun setEnabled(isOptedIn: Boolean) = Unit

        override fun isEnabled(): Boolean = bortEnabled

        override fun requiresRuntimeEnable(): Boolean = true
    }

    private val mockDropboxSettings = object : DropBoxSettings {
        override val dataSourceEnabled = true
        override val anrRateLimitingSettings = mockRateLimitingSettings
        override val javaExceptionsRateLimitingSettings = mockRateLimitingSettings
        override val wtfsRateLimitingSettings = mockRateLimitingSettings
        override val wtfsTotalRateLimitingSettings = mockRateLimitingSettings
        override val kmsgsRateLimitingSettings = mockRateLimitingSettings
        override val structuredLogRateLimitingSettings = mockRateLimitingSettings
        override val tombstonesRateLimitingSettings = mockRateLimitingSettings
        override val metricReportRateLimitingSettings = mockRateLimitingSettings
        override val marFileRateLimitingSettings = mockRateLimitingSettings
        override val continuousLogFileRateLimitingSettings = mockRateLimitingSettings
        override val excludedTags get() = mockGetExcludedTags
        override val scrubTombstones: Boolean = false
        override val processImmediately: Boolean = true
        override val pollingInterval: Duration = 15.minutes
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        entryProcessors = mapOfProcessors(
            TEST_TAG to mockEntryProcessor,
            TEST_TAG_TO_IGNORE to mockEntryProcessor,
        )
    }

    @Test
    fun ignoredTagsArePassed() {
        mockGetExcludedTags = setOf(TEST_TAG_TO_IGNORE)
        val configureFilterSettings = RealDropBoxFilters(
            entryProcessors = entryProcessors,
            settings = mockDropboxSettings,
            bortEnabledProvider = bortEnabledProvider,
        )

        assertEquals(
            configureFilterSettings.tagFilter(),
            listOf(TEST_TAG),
        )
    }

    @Test
    fun noTagsPassedWhenBortDisabled() {
        mockGetExcludedTags = setOf(TEST_TAG_TO_IGNORE)
        bortEnabled = false
        val configureFilterSettings = RealDropBoxFilters(
            entryProcessors = entryProcessors,
            settings = mockDropboxSettings,
            bortEnabledProvider = bortEnabledProvider,
        )

        assertEquals(
            configureFilterSettings.tagFilter(),
            emptyList<String>(),
        )
    }

    private fun mapOfProcessors(vararg processors: Pair<String, EntryProcessor>): DropBoxEntryProcessors =
        mockk(relaxed = true) {
            every { map } returns mapOf(*processors)
        }
}
