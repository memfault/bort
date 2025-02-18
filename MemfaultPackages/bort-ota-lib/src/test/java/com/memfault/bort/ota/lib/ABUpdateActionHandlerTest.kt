package com.memfault.bort.ota.lib

import android.app.Application
import android.content.SharedPreferences
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.memfault.bort.shared.SoftwareUpdateSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

private const val OLD_SOFTWARE_VERSION = "old"

class ABUpdateActionHandlerTest {
    private lateinit var testingUpdateEngine: AndroidUpdateEngine
    private lateinit var softwareUpdateCheckerMock: SoftwareUpdateChecker
    private lateinit var rebootDevice: RebootDevice
    private lateinit var handler: ABUpdateActionHandler
    private lateinit var cachedOtaProvider: CachedOtaProvider
    private lateinit var updater: Updater
    private lateinit var application: Application
    private lateinit var settings: SoftwareUpdateSettings
    private lateinit var scheduleDownload: ScheduleDownload
    private lateinit var otaRulesProvider: OtaRulesProvider
    private lateinit var settingsProvider: SoftwareUpdateSettingsProvider

    private val testScheduler = UnconfinedTestDispatcher()

    private val updateEngineCallbacks = mutableListOf<AndroidUpdateEngineCallback>()
    private val collectedStates = mutableListOf<State>()
    private val collectedEvents = mutableListOf<Event>()

    private var ota: Ota? = Ota(
        url = "http://localhost/ota.zip",
        version = "1.3.2",
        releaseNotes = "Fixed some bugs, added some new features.",
        artifactMetadata = mapOf(
            "METADATA_HASH" to "z4x6Wb+qNYpMKA7+KnMcbSFK6fxX8vbyEzhK2gBfJbQ=",
            "FILE_SIZE" to "99449",
            "METADATA_SIZE" to "51846",
            "FILE_HASH" to "X9zpqKb2z15s5eNhRuzntqYlPSB011/aGcdftaTRsrI=",
            "_MFLT_PAYLOAD_SIZE" to "99449",
            "_MFLT_PAYLOAD_OFFSET" to "1295",
        ),
        isForced = null,
    )

    @Before
    fun setup() {
        softwareUpdateCheckerMock = mockk {
            coEvery { getLatestRelease() } coAnswers { ota }
        }
        rebootDevice = mockk {
            every { this@mockk.invoke() } returns Unit
        }
        cachedOtaProvider = object : CachedOtaProvider {
            private var cachedOta: Ota? = null
            override fun get(): Ota? = ota
            override fun set(ota: Ota?) {
                cachedOta = ota
            }
        }
        testingUpdateEngine = mockk {
            every { bind(any()) } answers {
                updateEngineCallbacks += firstArg<AndroidUpdateEngineCallback>()
            }
            every { applyPayload(any(), any(), any(), any()) } returns Unit
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
            ABUpdateActionHandler(
                androidUpdateEngine = testingUpdateEngine,
                rebootDevice = rebootDevice,
                cachedOtaProvider = cachedOtaProvider,
                updater = updater,
                scheduleDownload = scheduleDownload,
                softwareUpdateChecker = softwareUpdateCheckerMock,
                application = application,
                otaRulesProvider = otaRulesProvider,
                settingsProvider = settingsProvider,
                defaultCoroutineContext = testScheduler,
            )
        handler.initialize()
    }

    private suspend fun forEachCallback(block: AndroidUpdateEngineCallback.() -> Unit) {
        updateEngineCallbacks.forEach(block)
        delay(100)
    }

    @Test
    fun testCallbacks() = runBlocking {
        assertThat(updateEngineCallbacks.size).isEqualTo(1)
    }

    @Test
    fun testCheckForUpdate_foreground() = runBlocking {
        handler.handle(State.Idle, Action.CheckForUpdate(background = false))
        assertThat(collectedStates).isEqualTo(
            listOf(State.CheckingForUpdates, State.UpdateAvailable(ota!!, showNotification = false)),

        )
        assertThat(collectedEvents).isEmpty()
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
    fun testCheckForUpdateAlreadyAtLatest() = runBlocking {
        ota = null
        handler.handle(State.Idle, Action.CheckForUpdate())
        assertThat(collectedStates).containsExactly(State.CheckingForUpdates, State.Idle)
        assertThat(collectedEvents).containsExactly(Event.NoUpdatesAvailable)
    }

    @Test
    fun testDownloadUpdate() = runBlocking {
        handler.handle(State.UpdateAvailable(ota!!), Action.DownloadUpdate)

        forEachCallback {
            onStatusUpdate(UPDATE_ENGINE_STATUS_DOWNLOADING, 0f)
        }

        assertThat(collectedStates).containsExactly(State.UpdateDownloading(ota!!))
        assertThat(collectedEvents).isEmpty()
        verify(exactly = 1) {
            testingUpdateEngine.applyPayload(
                ota!!.url,
                ota!!.artifactMetadata["_MFLT_PAYLOAD_OFFSET"]!!.toLong(),
                ota!!.artifactMetadata["_MFLT_PAYLOAD_SIZE"]!!.toLong(),
                ota!!.artifactMetadata.map { "${it.key}=${it.value}" }.toTypedArray(),
            )
        }
    }

    @Test
    fun testUpdateDownloadProgress() = runBlocking {
        handler.handle(State.UpdateAvailable(ota!!), Action.DownloadUpdate)
        forEachCallback {
            onStatusUpdate(UPDATE_ENGINE_STATUS_DOWNLOADING, .5f)
        }
        forEachCallback {
            onStatusUpdate(UPDATE_ENGINE_STATUS_DOWNLOADING, 1f)
        }

        assertThat(collectedStates).isEqualTo(
            listOf(
                State.UpdateDownloading(ota!!, progress = 50),
                State.UpdateDownloading(ota!!, progress = 100),
            ),
        )
        assertThat(collectedEvents).isEmpty()
        verify(exactly = 1) {
            testingUpdateEngine.applyPayload(
                ota!!.url,
                ota!!.artifactMetadata["_MFLT_PAYLOAD_OFFSET"]!!.toLong(),
                ota!!.artifactMetadata["_MFLT_PAYLOAD_SIZE"]!!.toLong(),
                ota!!.artifactMetadata.map { "${it.key}=${it.value}" }.toTypedArray(),
            )
        }
    }

    @Test
    fun testRebootAfterCompletion() = runBlocking {
        handler.handle(State.RebootNeeded(ota!!), Action.Reboot)
        assertThat(collectedStates).isEqualTo(
            listOf<State>(
                State.RebootedForInstallation(ota!!, OLD_SOFTWARE_VERSION),
            ),
        )
        assertThat(collectedEvents).isEmpty()

        verify(exactly = 1) { rebootDevice() }
    }

    @Test
    fun testFinalizingProgress() = runBlocking {
        forEachCallback {
            onStatusUpdate(UPDATE_ENGINE_FINALIZING, .5f)
        }
        forEachCallback {
            onStatusUpdate(UPDATE_ENGINE_FINALIZING, 1f)
        }

        assertThat(collectedStates).isEqualTo(
            listOf(
                State.Finalizing(ota!!, progress = 50),
                State.Finalizing(ota!!, progress = 100),
            ),
        )
        assertThat(collectedEvents).isEmpty()
    }

    @Test
    fun testBecomeIdle() = runBlocking {
        forEachCallback {
            onStatusUpdate(UPDATE_ENGINE_STATUS_IDLE, 0f)
        }
        assertThat(collectedStates).isEqualTo(
            listOf(
                State.Idle,
            ),
        )
        assertThat(collectedEvents).isEmpty()
    }

    @Test
    fun testUpdateFinishedNeedReboot() = runBlocking {
        forEachCallback {
            onStatusUpdate(UPDATE_ENGINE_UPDATED_NEED_REBOOT, 0f)
        }
        assertThat(collectedStates).isEqualTo(
            listOf(
                State.RebootNeeded(ota!!),
            ),
        )
        assertThat(collectedEvents).isEmpty()
    }

    @Test
    fun testDownloadFailedTrigger() = runBlocking {
        forEachCallback {
            onPayloadApplicationComplete(UPDATE_ENGINE_ERROR_DOWNLOAD_TRANSFER_ERROR)
        }
        assertThat(collectedStates).isEmpty()
        assertThat(collectedEvents).isEqualTo(
            listOf<Event>(
                Event.DownloadFailed,
            ),
        )
    }

    @Test
    fun testDownloadSuccessTrigger() = runBlocking {
        forEachCallback {
            onPayloadApplicationComplete(-1)
        }
        assertThat(collectedStates).isEmpty()
        assertThat(collectedEvents).isEqualTo(
            listOf<Event>(
                Event.VerificationFailed,
            ),
        )
    }

    @Test
    fun testCachedOtaProviderHandlesEmpty() {
        val editor = mockk<SharedPreferences.Editor> {
            every { putString(any(), any()) } returns this
            every { apply() } returns Unit
        }
        val prefs = mockk<SharedPreferences> {
            every { edit() } returns editor
        }
        val provider = SharedPreferenceCachedOtaProvider(prefs)
        provider.set(null)
    }
}
