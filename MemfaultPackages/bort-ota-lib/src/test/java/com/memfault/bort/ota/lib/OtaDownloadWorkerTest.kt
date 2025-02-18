package com.memfault.bort.ota.lib

import android.content.Context
import androidx.work.ListenableWorker.Result
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test

internal class OtaDownloadWorkerTest {
    private var state: State = State.Idle
    private var rule: (Ota) -> Boolean = { _ -> true }

    private val otaRulesProvider = object : OtaRulesProvider {
        override fun downloadRules(ota: Ota) = DownloadOtaRules(
            canDownloadNowAfterConstraintsSatisfied = rule,
            overrideNetworkConstraint = null,
            requiresStorageNotLowConstraint = false,
            requiresBatteryNotLowConstraint = false,
            requiresChargingConstraint = false,
            useForegroundServiceForAbDownloads = false,
        )
        override fun installRules(ota: Ota) = TODO("not used")
    }
    private val updater: Updater = mockk(relaxed = true) {
        every { badCurrentUpdateState() } answers { state }
    }
    private val ota = Ota(url = "https://", version = "1.2.3", releaseNotes = "notes")
    private val context: Context = mockk(relaxed = true)

    @Before
    fun setup() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
    }

    @Test
    fun failsWhenNotInExpectedState() = runTest {
        state = State.Idle
        val result = OtaDownloadWorker.downloadWorkerRun(updater, otaRulesProvider, { false }, context, {})
        assertThat(result).isEqualTo(Result.failure())
        coVerify(exactly = 0) { updater.perform(any()) }
    }

    @Test
    fun downloadsUpdate() = runTest {
        state = State.UpdateAvailable(ota)
        val result = OtaDownloadWorker.downloadWorkerRun(updater, otaRulesProvider, { false }, context, {})
        assertThat(result).isEqualTo(Result.success())
        coVerify(exactly = 1) { updater.badCurrentUpdateState() }
        coVerify(exactly = 1) { updater.perform(Action.DownloadUpdate) }
        confirmVerified(updater)
    }

    @Test
    fun doesNotDownloadUpdate() = runTest {
        state = State.UpdateAvailable(ota)
        rule = { _ -> false }
        val result = OtaDownloadWorker.downloadWorkerRun(updater, otaRulesProvider, { false }, context, {})
        assertThat(result).isEqualTo(Result.retry())
        coVerify(exactly = 0) { updater.perform(any()) }
    }
}
