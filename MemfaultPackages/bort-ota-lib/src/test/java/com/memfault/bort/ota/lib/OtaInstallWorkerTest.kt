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

internal class OtaInstallWorkerTest {
    private var state: State = State.Idle
    private var rule: (Ota) -> Boolean = { _ -> true }

    private val otaRulesProvider = object : OtaRulesProvider {
        override fun downloadRules(ota: Ota) = TODO("not used")
        override fun installRules(ota: Ota) = InstallOtaRules(
            canInstallNowAfterConstraintsSatisfied = rule,
            requiresStorageNotLowConstraint = false,
            requiresBatteryNotLowConstraint = false,
            requiresChargingConstraint = false,
        )
    }
    private val context: Context = mockk()
    private val updater: Updater = mockk(relaxed = true) {
        every { badCurrentUpdateState() } answers { state }
    }
    private val ota = Ota(url = "https://", version = "1.2.3", releaseNotes = "notes")

    @Before
    fun setup() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
    }

    @Test
    fun failsWhenNotInExpectedState() = runTest {
        state = State.Idle
        val result = OtaInstallWorker.installWorkerRun(updater, otaRulesProvider)
        assertThat(result).isEqualTo(Result.failure())
        coVerify(exactly = 0) { updater.perform(any()) }
    }

    @Test
    fun recoveryUpdateApplied() = runTest {
        state = State.ReadyToInstall(ota)
        val result = OtaInstallWorker.installWorkerRun(updater, otaRulesProvider)
        assertThat(result).isEqualTo(Result.success())
        coVerify(exactly = 1) { updater.badCurrentUpdateState() }
        coVerify(exactly = 1) { updater.perform(Action.InstallUpdate) }
        confirmVerified(updater)
    }

    @Test
    fun recoveryUpdateNotApplied() = runTest {
        state = State.ReadyToInstall(ota)
        rule = { _ -> false }
        val result = OtaInstallWorker.installWorkerRun(updater, otaRulesProvider)
        assertThat(result).isEqualTo(Result.retry())
        coVerify(exactly = 0) { updater.perform(any()) }
    }

    @Test
    fun abUpdateApplied() = runTest {
        state = State.RebootNeeded(ota)
        val result = OtaInstallWorker.installWorkerRun(updater, otaRulesProvider)
        assertThat(result).isEqualTo(Result.success())
        coVerify(exactly = 1) { updater.badCurrentUpdateState() }
        coVerify(exactly = 1) { updater.perform(Action.Reboot) }
        confirmVerified(updater)
    }

    @Test
    fun abUpdateNotApplied() = runTest {
        state = State.RebootNeeded(ota)
        rule = { _ -> false }
        val result = OtaInstallWorker.installWorkerRun(updater, otaRulesProvider)
        assertThat(result).isEqualTo(Result.retry())
        coVerify(exactly = 0) { updater.perform(any()) }
    }
}
