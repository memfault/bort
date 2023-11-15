package com.memfault.bort.ota

import com.memfault.bort.ota.lib.State
import com.memfault.bort.ota.lib.TestOtaModePreferenceProvider.Companion.AB
import com.memfault.bort.ota.lib.TestOtaModePreferenceProvider.Companion.RECOVERY
import com.memfault.bort.ota.lib.testLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
class TestOtaApp : OtaApp() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.Default).launch {
            updater.updateState.collect { state ->
                testLog("updater state=$state")
                handleState(state)
            }
        }
        val otaModeString = if (otaMode()) AB else RECOVERY
        testLog("test OTA mode: $otaModeString")
    }

    /**
     * Simulates user interaction (i.e. when there's an update available, download it, when it is ready to install
     * install it).
     */
    private fun handleState(state: State) {
        when (state) {
            is State.CheckingForUpdates -> {
                testLog("checking for updates")
            }
            is State.UpdateAvailable -> {
                testLog(
                    "update available version=${state.ota.version} url=${state.ota.url} " +
                        "isForced=${state.ota.isForced}",
                )
            }
            is State.ReadyToInstall -> {
                testLog("ready to install version=${state.ota.version} url=${state.ota.url}")
            }
            is State.RebootNeeded -> {
                testLog("ota applied version=${state.ota.version} url=${state.ota.url}")
            }
            else -> {}
        }
    }
}
