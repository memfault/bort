package com.memfault.bort.ota.lib

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.lang.IllegalStateException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val OLD_SOFTWARE_VERSION = "old"

class RecoveryBasedUpdateActionHandlerTest {
    private lateinit var softwareUpdateChecker: SoftwareUpdateChecker
    private lateinit var recoveryInterface: RecoveryInterface
    private lateinit var startUpdateDownload: (url: String) -> Unit
    private lateinit var handler: RecoveryBasedUpdateActionHandler

    private val collectedStates = mutableListOf<State>()
    private val collectedEvents = mutableListOf<Event>()

    private var ota: Ota? = Ota(
        url = "http://localhost/ota.zip",
        version = "1.3.2",
        releaseNotes = "Fixed some bugs, added some new features.",
        isForced = null,
    )

    @BeforeEach
    fun setup() {
        softwareUpdateChecker = mockk {
            coEvery { getLatestRelease() } coAnswers { ota }
        }
        recoveryInterface = mockk {
            every { verifyOrThrow(any()) } answers { }
            every { install(any()) } answers { }
        }
        startUpdateDownload = mockk {
            every { this@mockk.invoke(any()) } answers { }
        }
        handler =
            RecoveryBasedUpdateActionHandler(
                softwareUpdateChecker = softwareUpdateChecker,
                recoveryInterface = recoveryInterface,
                startUpdateDownload = startUpdateDownload,
                setState = ::setState,
                triggerEvent = ::triggerEvent,
                currentSoftwareVersion = OLD_SOFTWARE_VERSION,
                metricLogger = { },
            )
    }

    suspend fun setState(state: State) = collectedStates.add(state)
    suspend fun triggerEvent(event: Event) = collectedEvents.add(event)

    @Test
    fun testCheckForUpdate() = runBlockingTest {
        handler.handle(State.Idle, Action.CheckForUpdate())
        assertEquals(listOf(State.CheckingForUpdates, State.UpdateAvailable(ota!!)), collectedStates)
        assertEquals(listOf<Event>(), collectedEvents)
    }

    @Test
    fun testCheckForUpdateAlreadyAtLatest() = runBlockingTest {
        ota = null
        handler.handle(State.Idle, Action.CheckForUpdate())
        assertEquals(listOf(State.CheckingForUpdates, State.Idle), collectedStates)
        assertEquals(listOf<Event>(Event.NoUpdatesAvailable), collectedEvents)
    }

    @Test
    fun testDownloadUpdate() = runBlockingTest {
        handler.handle(State.UpdateAvailable(ota!!), Action.DownloadUpdate)
        assertEquals(listOf(State.UpdateDownloading(ota!!)), collectedStates)
        assertEquals(listOf<Event>(), collectedEvents)
        verify(exactly = 1) { startUpdateDownload.invoke(ota!!.url) }
    }

    @Test
    fun testUpdateDownloadProgress() = runBlockingTest {
        handler.handle(State.UpdateDownloading(ota!!), Action.DownloadProgress(50))
        handler.handle(State.UpdateDownloading(ota!!), Action.DownloadProgress(100))
        assertEquals(
            listOf(
                State.UpdateDownloading(ota!!, progress = 50),
                State.UpdateDownloading(ota!!, progress = 100)
            ),
            collectedStates
        )
        assertEquals(listOf<Event>(), collectedEvents)
    }

    @Test
    fun testDownloadCompletedVerificationOk() = runBlocking {
        handler.handle(State.UpdateDownloading(ota!!), Action.DownloadCompleted("dummy"))
        assertEquals(listOf(State.ReadyToInstall(ota!!, "dummy")), collectedStates)
        assertEquals(listOf<Event>(), collectedEvents)

        verify(exactly = 1) { recoveryInterface.verifyOrThrow(File("dummy")) }
    }

    @Test
    fun testDownloadCompletedVerificationFailed() = runBlocking {
        every { recoveryInterface.verifyOrThrow(any()) } throws IllegalStateException("oops")
        handler.handle(State.UpdateDownloading(ota!!), Action.DownloadCompleted("dummy"))
        assertEquals(listOf(State.Idle), collectedStates)
        assertEquals(listOf(Event.VerificationFailed), collectedEvents)

        verify(exactly = 1) { recoveryInterface.verifyOrThrow(File("dummy")) }
    }

    @Test
    fun testDownloadFailed() = runBlocking {
        handler.handle(State.UpdateDownloading(ota!!), Action.DownloadFailed)
        assertEquals(listOf(State.UpdateAvailable(ota!!)), collectedStates)
        assertEquals(listOf(Event.DownloadFailed), collectedEvents)
    }

    @Test
    fun testInstallUpdateSucceeds() = runBlocking {
        handler.handle(State.ReadyToInstall(ota!!, path = "dummy"), Action.InstallUpdate)
        assertEquals(listOf<State>(State.RebootedForInstallation(ota!!, OLD_SOFTWARE_VERSION)), collectedStates)
        assertEquals(listOf<Event>(), collectedEvents)
        verify(exactly = 1) { recoveryInterface.install(File("dummy")) }
    }

    @Test
    fun testInstallUpdateFails() = runBlocking {
        // This should never happen in a real use case because install only ever happens after the update is verified
        // and a verified update will always be able to call install(). Nevertheless, test that we go back to idle.
        every { recoveryInterface.install(any()) } throws IllegalStateException("oops")
        handler.handle(State.ReadyToInstall(ota!!, path = "dummy"), Action.InstallUpdate)
        assertEquals(
            listOf(
                // Shortly transitioned while attempted rebooting
                State.RebootedForInstallation(ota!!, OLD_SOFTWARE_VERSION),
                // Then back to idle because it failed
                State.Idle,
            ),
            collectedStates
        )
        assertEquals(listOf<Event>(), collectedEvents)
        verify(exactly = 1) { recoveryInterface.install(File("dummy")) }
    }
}
