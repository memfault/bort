package com.memfault.bort.ota

import android.content.Context
import android.util.Log
import com.memfault.bort.ota.lib.ABUpdateActionHandler
import com.memfault.bort.ota.lib.Action
import com.memfault.bort.ota.lib.AndroidUpdateEngine
import com.memfault.bort.ota.lib.AndroidUpdateEngineCallback
import com.memfault.bort.ota.lib.BortSoftwareUpdateSettingsProvider
import com.memfault.bort.ota.lib.DEFAULT_STATE_PREFERENCE_FILE
import com.memfault.bort.ota.lib.Event
import com.memfault.bort.ota.lib.MemfaultSoftwareUpdateChecker
import com.memfault.bort.ota.lib.Ota
import com.memfault.bort.ota.lib.RealMetricLogger
import com.memfault.bort.ota.lib.RecoveryBasedUpdateActionHandler
import com.memfault.bort.ota.lib.RecoveryInterface
import com.memfault.bort.ota.lib.SharedPreferenceCachedOtaProvider
import com.memfault.bort.ota.lib.SoftwareUpdateChecker
import com.memfault.bort.ota.lib.SoftwareUpdateSettingsProvider
import com.memfault.bort.ota.lib.State
import com.memfault.bort.ota.lib.UPDATE_ENGINE_STATUS_IDLE
import com.memfault.bort.ota.lib.UPDATE_ENGINE_UPDATED_NEED_REBOOT
import com.memfault.bort.ota.lib.UpdateActionHandler
import com.memfault.bort.ota.lib.UpdateActionHandlerFactory
import com.memfault.bort.ota.lib.Updater
import com.memfault.bort.ota.lib.download.DownloadOtaService
import com.memfault.bort.shared.SoftwareUpdateSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * A test application for E2E testing. It extends from the main app and listens to state changes. When it
 * reaches a state where user intervention is required, it will perform the affirmative action automatically
 * (i.e. download the update, install the update).
 *
 * States are observed and logged so that they can be easily matched with test expectations. Some components
 * are slighly altered (i.e. update checker is reconstructed dynamically because of changing APIs in E2E tests)
 * and the recovery interface is mocked out because the emulator does not have a recovery.
 */
class TestApp : App() {
    private var otaMode: OtaTestMode = OtaTestMode.Recovery
    private var collectJob: Job? = null

    /**
     * Simulates user interaction (i.e. when there's an update available, download it, when it is ready to install
     * install it).
     */
    private suspend fun handleState(updater: Updater, state: State) {
        with(updater) {
            when (state) {
                is State.CheckingForUpdates -> {
                    testLog("checking for updates")
                }
                is State.UpdateAvailable -> {
                    testLog(
                        "update available version=${state.ota.version} url=${state.ota.url} " +
                            "isForced=${state.ota.isForced}"
                    )
                    perform(Action.DownloadUpdate)
                }
                is State.ReadyToInstall -> {
                    testLog("ready to install version=${state.ota.version} url=${state.ota.url}")
                    perform(Action.InstallUpdate)
                }
                is State.RebootNeeded -> {
                    testLog("ota applied version=${state.ota.version} url=${state.ota.url}")
                    perform(Action.Reboot)
                }
                else -> {}
            }
        }
    }

    override fun createComponents(applicationContext: Context): AppComponents {
        val updater = Updater.create(
            context = applicationContext,
            actionHandlerFactory = object : UpdateActionHandlerFactory {
                override fun create(
                    setState: suspend (state: State) -> Unit,
                    triggerEvent: suspend (event: Event) -> Unit,
                    settings: () -> SoftwareUpdateSettings,
                ): UpdateActionHandler = createTestActionHandler(applicationContext, setState, triggerEvent)
            }
        )

        CoroutineScope(Dispatchers.Default).launch {
            updater.updateState.collect { state ->
                testLog("updater state=$state")
                handleState(updater(), state)
            }
        }.also {
            collectJob?.cancel()
            collectJob = it
        }

        return object : AppComponents {
            override fun updater(): Updater = updater
        }
    }

    private fun createTestActionHandler(
        context: Context,
        setState: suspend (state: State) -> Unit,
        triggerEvent: suspend (event: Event) -> Unit,
    ): UpdateActionHandler = when (otaMode) {
        OtaTestMode.Recovery -> {
            val settingsProvider = BortSoftwareUpdateSettingsProvider(context.contentResolver)
            RecoveryBasedUpdateActionHandler(
                softwareUpdateChecker = createTestSoftwareUpdateChecker(settingsProvider),
                recoveryInterface = createLoggingRecoveryInterface(),
                startUpdateDownload = { DownloadOtaService.download(context, it) },
                currentSoftwareVersion = settingsProvider.settings()?.currentVersion ?: "n/a",
                metricLogger = RealMetricLogger(context),
                setState = setState,
                triggerEvent = triggerEvent,
            )
        }
        OtaTestMode.AB -> {
            val settingsProvider = BortSoftwareUpdateSettingsProvider(context.contentResolver)
            val updateEngine = object : AndroidUpdateEngine {
                var callback: AndroidUpdateEngineCallback? = null
                override fun bind(callback: AndroidUpdateEngineCallback) {
                    this.callback = callback
                    callback.onStatusUpdate(UPDATE_ENGINE_STATUS_IDLE, 0f)
                }

                override fun applyPayload(url: String, offset: Long, size: Long, metadata: Array<String>) {
                    // simulate the happy path of a payload applying, a real device would download, verify and then
                    // reach this state
                    callback?.onStatusUpdate(UPDATE_ENGINE_UPDATED_NEED_REBOOT, 100f)
                }
            }
            ABUpdateActionHandler(
                androidUpdateEngine = updateEngine,
                softwareUpdateChecker = createTestSoftwareUpdateChecker(settingsProvider),
                setState = setState,
                triggerEvent = triggerEvent,
                rebootDevice = { testLog("reboot triggered") },
                SharedPreferenceCachedOtaProvider(
                    context.getSharedPreferences(DEFAULT_STATE_PREFERENCE_FILE, MODE_PRIVATE)
                ),
                currentSoftwareVersion = settingsProvider.settings()?.currentVersion
                    ?: throw IllegalStateException("cannot find current version")
            )
        }
    }

    /**
     * The testing software update checker is the same as the production one but we recreate it on every call
     * because E2E tests change randomize the project api key for each test.
     */
    private fun createTestSoftwareUpdateChecker(provider: SoftwareUpdateSettingsProvider): SoftwareUpdateChecker =
        object : SoftwareUpdateChecker {
            override suspend fun getLatestRelease(): Ota? =
                MemfaultSoftwareUpdateChecker.create(
                    settings = {
                        provider.settings()?.also {
                            testLog("api key = ${it.projectApiKey}")
                        } ?: throw IllegalStateException("Could not obtain settings from Bort")
                    },
                    metricLogger = RealMetricLogger(this@TestApp),
                ).getLatestRelease().also { testLog("ota = ${it ?: "nada"}") }
        }

    /**
     * Emulated test devices do not have a real recovery. Instead, we log and check for the output.
     */
    private fun createLoggingRecoveryInterface(): RecoveryInterface = object : RecoveryInterface {
        override fun verifyOrThrow(path: File) {
            testLog("verify path=$path")
        }

        override fun install(path: File) {
            testLog("install path=$path")
        }
    }

    fun changeMode(mode: OtaTestMode) {
        otaMode = mode
        components = createComponents(this)
    }

    enum class OtaTestMode {
        Recovery,
        AB
    }
}

fun testLog(msg: String, tag: String = "bort-ota-test") = Log.v(tag, msg)
