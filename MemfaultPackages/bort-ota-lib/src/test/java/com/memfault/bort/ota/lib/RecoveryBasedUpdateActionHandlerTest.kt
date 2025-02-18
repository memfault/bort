package com.memfault.bort.ota.lib

import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.memfault.bort.shared.SoftwareUpdateSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.IllegalStateException

private const val OLD_SOFTWARE_VERSION = "old"

class RecoveryBasedUpdateActionHandlerTest {
    private lateinit var softwareUpdateCheckerMock: SoftwareUpdateChecker
    private lateinit var recoveryInterface: RecoveryInterface
    private lateinit var startUpdateDownload: (url: String) -> Unit
    private lateinit var handler: RecoveryBasedUpdateActionHandler
    private lateinit var updater: Updater
    private lateinit var application: Application
    private lateinit var settings: SoftwareUpdateSettings
    private lateinit var scheduleDownload: ScheduleDownload
    private lateinit var otaRulesProvider: OtaRulesProvider
    private lateinit var settingsProvider: SoftwareUpdateSettingsProvider

    private val testDispatcher = UnconfinedTestDispatcher()

    private val collectedStates = mutableListOf<State>()
    private val collectedEvents = mutableListOf<Event>()

    private var ota: Ota? = Ota(
        url = "http://localhost/ota.zip",
        version = "1.3.2",
        releaseNotes = "Fixed some bugs, added some new features.",
        isForced = null,
    )

    @Before
    fun setup() {
        softwareUpdateCheckerMock = mockk {
            coEvery { getLatestRelease() } coAnswers { ota }
        }
        recoveryInterface = mockk {
            every { verifyOrThrow(any()) } answers { }
            every { install(any()) } answers { }
        }
        startUpdateDownload = mockk {
            every { this@mockk.invoke(any()) } answers { }
        }
        application = mockk()
        settings = mockk {
            every { currentVersion } answers { OLD_SOFTWARE_VERSION }
        }
        updater = mockk {
            coEvery { setState(any()) } answers { collectedStates.add(arg(0)) }
            coEvery { triggerEvent(any()) } answers { collectedEvents.add(arg(0)) }
        }
        scheduleDownload = mockk(relaxed = true)
        otaRulesProvider = mockk()
        settingsProvider = mockk {
            every { get() } answers { settings }
        }
        handler =
            RecoveryBasedUpdateActionHandler(
                recoveryInterface = recoveryInterface,
                startUpdateDownload = startUpdateDownload,
                metricLogger = { },
                updater = updater,
                scheduleDownload = scheduleDownload,
                softwareUpdateChecker = softwareUpdateCheckerMock,
                application = application,
                otaRulesProvider = otaRulesProvider,
                settingsProvider = settingsProvider,
                ioCoroutineContext = testDispatcher,
            )
    }

    @Test
    fun testCheckForUpdate_foreground() = runTest {
        ota = ota?.copy(isForced = null)
        handler.handle(State.Idle, Action.CheckForUpdate(background = false))
        assertThat(collectedStates).isEqualTo(
            listOf(State.CheckingForUpdates, State.UpdateAvailable(ota!!, showNotification = false)),
        )
        assertThat(collectedEvents).isEmpty()
        verify(exactly = 0) { scheduleDownload.scheduleDownload(any()) }
    }

    @Test
    fun testCheckForUpdate_forcedNotSet() = runTest {
        ota = ota?.copy(isForced = null)
        handler.handle(State.Idle, Action.CheckForUpdate(background = true))
        assertThat(collectedStates).isEqualTo(
            listOf(State.CheckingForUpdates, State.UpdateAvailable(ota!!, showNotification = true)),
        )
        assertThat(collectedEvents).isEmpty()
        verify(exactly = 0) { scheduleDownload.scheduleDownload(any()) }
    }

    @Test
    fun testCheckForUpdate_notForced() = runTest {
        ota = ota?.copy(isForced = false)
        handler.handle(State.Idle, Action.CheckForUpdate(background = true))
        assertThat(collectedStates).isEqualTo(
            listOf(State.CheckingForUpdates, State.UpdateAvailable(ota!!, showNotification = true)),
        )
        assertThat(collectedEvents).isEmpty()
        verify(exactly = 0) { scheduleDownload.scheduleDownload(any()) }
    }

    @Test
    fun testCheckForUpdate_forced_scheduleAutoDownload() = runTest {
        ota = ota?.copy(isForced = true)
        handler.handle(State.Idle, Action.CheckForUpdate(background = true))
        assertThat(collectedStates).isEqualTo(
            listOf(State.CheckingForUpdates, State.UpdateAvailable(ota!!, showNotification = false)),
        )
        assertThat(collectedEvents).isEmpty()
        verify(exactly = 1) { scheduleDownload.scheduleDownload(ota!!) }
    }

    @Test
    fun testCheckForUpdateAlreadyAtLatest() = runTest {
        ota = null
        handler.handle(State.Idle, Action.CheckForUpdate())
        assertThat(collectedStates).containsExactly(State.CheckingForUpdates, State.Idle)
        assertThat(collectedEvents).containsExactly(Event.NoUpdatesAvailable)
    }

    @Test
    fun testDownloadUpdate() = runTest {
        handler.handle(State.UpdateAvailable(ota!!), Action.DownloadUpdate)
        assertThat(collectedStates).containsExactly(State.UpdateDownloading(ota!!))
        assertThat(collectedEvents).isEmpty()
        verify(exactly = 1) { startUpdateDownload.invoke(ota!!.url) }
    }

    @Test
    fun testUpdateDownloadProgress() = runTest {
        handler.handle(State.UpdateDownloading(ota!!), Action.DownloadProgress(50))
        handler.handle(State.UpdateDownloading(ota!!), Action.DownloadProgress(100))
        assertThat(collectedStates).isEqualTo(
            listOf(
                State.UpdateDownloading(ota!!, progress = 50),
                State.UpdateDownloading(ota!!, progress = 100),
            ),
        )
        assertThat(collectedEvents).isEmpty()
    }

    @Test
    fun testDownloadCompletedVerificationOk() = runBlocking {
        handler.handle(State.UpdateDownloading(ota!!), Action.DownloadCompleted("dummy"))
        assertThat(collectedStates).containsExactly(State.ReadyToInstall(ota!!, "dummy"))
        assertThat(collectedEvents).isEmpty()

        verify(exactly = 1) { recoveryInterface.verifyOrThrow(File("dummy")) }
    }

    @Test
    fun testDownloadCompletedVerificationFailed() = runBlocking {
        every { recoveryInterface.verifyOrThrow(any()) } throws IllegalStateException("oops")
        handler.handle(State.UpdateDownloading(ota!!), Action.DownloadCompleted("dummy"))
        assertThat(collectedStates).containsExactly(State.Idle)
        assertThat(collectedEvents).containsExactly(Event.VerificationFailed)

        verify(exactly = 1) { recoveryInterface.verifyOrThrow(File("dummy")) }
    }

    @Test
    fun testDownloadFailed() = runBlocking {
        handler.handle(State.UpdateDownloading(ota!!), Action.DownloadFailed)
        assertThat(collectedStates).containsExactly(State.UpdateAvailable(ota!!, showNotification = false))
        assertThat(collectedEvents).containsExactly(Event.DownloadFailed)
    }

    @Test
    fun testInstallUpdateSucceeds() = runBlocking {
        handler.handle(State.ReadyToInstall(ota!!, path = "dummy"), Action.InstallUpdate)
        assertThat(collectedStates).containsExactly(State.RebootedForInstallation(ota!!, OLD_SOFTWARE_VERSION))
        assertThat(collectedEvents).isEmpty()
        verify(exactly = 1) { recoveryInterface.install(File("dummy")) }
    }

    @Test
    fun testInstallUpdateFails() = runBlocking {
        // This should never happen in a real use case because install only ever happens after the update is verified
        // and a verified update will always be able to call install(). Nevertheless, test that we go back to idle.
        every { recoveryInterface.install(any()) } throws IllegalStateException("oops")
        handler.handle(State.ReadyToInstall(ota!!, path = "dummy"), Action.InstallUpdate)
        assertThat(collectedStates).isEqualTo(
            listOf(
                // Shortly transitioned while attempted rebooting
                State.RebootedForInstallation(ota!!, OLD_SOFTWARE_VERSION),
                // Then back to idle because it failed
                State.Idle,
            ),
        )
        assertThat(collectedEvents).isEmpty()
        verify(exactly = 1) { recoveryInterface.install(File("dummy")) }
    }
}
