package com.memfault.bort.receivers

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.DropBoxManager
import com.memfault.bort.DevMode
import com.memfault.bort.dropbox.DropBoxFilters
import com.memfault.bort.dropbox.ProcessedEntryCursorProvider
import com.memfault.bort.dropbox.enqueueOneTimeDropBoxQueryTask
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.metrics.metricForTraceTag
import com.memfault.bort.settings.DropBoxSettings
import com.memfault.bort.settings.SettingsProvider
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val TEST_TAG = "data_app_crash"

class DropBoxEntryAddedReceiverTest {
    private val settingsProvider = mockk<SettingsProvider>()
    private val dropBoxSettings = mockk<DropBoxSettings>()
    private val processedEntryCursorProvider = mockk<ProcessedEntryCursorProvider>(relaxed = true)
    private val dropBoxFilters = mockk<DropBoxFilters>()
    private val application = mockk<Application>()
    private val devMode = mockk<DevMode>()
    private val builtinMetricsStore = mockk<BuiltinMetricsStore>(relaxed = true)

    private val receiver = DropBoxEntryAddedReceiver(
        settingsProvider = settingsProvider,
        dropBoxProcessedEntryCursorProvider = processedEntryCursorProvider,
        dropBoxFilters = dropBoxFilters,
        application = application,
        devMode = devMode,
        builtinMetricsStore = builtinMetricsStore,
    )

    @Before
    fun setUp() {
        mockkStatic("com.memfault.bort.dropbox.DropBoxGetEntriesTaskKt")

        every { settingsProvider.dropBoxSettings } returns dropBoxSettings
        every { dropBoxSettings.dataSourceEnabled } returns true
        every { dropBoxFilters.tagFilter() } returns listOf(TEST_TAG)
        every { enqueueOneTimeDropBoxQueryTask(any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkStatic("com.memfault.bort.dropbox.DropBoxGetEntriesTaskKt")
    }

    @Test
    fun incrementsDropBoxTagCounterWhenIntentIncludesDroppedCount() {
        val context = mockk<Context>()
        val intent = mockk<Intent> {
            every { getStringExtra(DropBoxManager.EXTRA_TAG) } returns TEST_TAG
            every { getIntExtra(DropBoxManager.EXTRA_DROPPED_COUNT, 0) } returns 3
        }

        receiver.onReceivedAndEnabled(context, intent, DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED)

        verify(exactly = 1) { builtinMetricsStore.increment(metricForTraceTag(TEST_TAG), 3) }
        verify(exactly = 1) { processedEntryCursorProvider.handleTimeFromEntryAddedIntent(intent) }
        verify(exactly = 1) { enqueueOneTimeDropBoxQueryTask(context) }
    }

    @Test
    fun doesNotIncrementDropBoxTagCounterWhenDroppedCountIsZero() {
        val context = mockk<Context>()
        val intent = mockk<Intent> {
            every { getStringExtra(DropBoxManager.EXTRA_TAG) } returns TEST_TAG
            every { getIntExtra(DropBoxManager.EXTRA_DROPPED_COUNT, 0) } returns 0
        }

        receiver.onReceivedAndEnabled(context, intent, DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED)

        verify(exactly = 0) { builtinMetricsStore.increment(any(), any()) }
        verify(exactly = 1) { processedEntryCursorProvider.handleTimeFromEntryAddedIntent(intent) }
        verify(exactly = 1) { enqueueOneTimeDropBoxQueryTask(context) }
    }
}
