package com.memfault.bort.ota

import com.memfault.bort.ota.lib.IsAbDevice
import com.memfault.bort.ota.lib.State
import com.memfault.bort.ota.lib.TestOtaModePreferenceProvider.Companion.AB
import com.memfault.bort.ota.lib.TestOtaModePreferenceProvider.Companion.RECOVERY
import com.memfault.bort.ota.lib.Updater
import com.memfault.bort.scopes.Scope
import com.memfault.bort.scopes.Scoped
import com.memfault.bort.scopes.coroutineScope
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(SingletonComponent::class)
class TestOtaStateLogger
@Inject constructor(
    private val updater: Updater,
    private val otaMode: IsAbDevice,
) : Scoped {
    override fun onEnterScope(scope: Scope) {
        scope.coroutineScope().launch {
            updater.updateState.collect { state ->
                Logger.test("updater state=$state")
                logState(state)
            }
        }

        val otaModeString = if (otaMode()) AB else RECOVERY
        Logger.test("test OTA mode: $otaModeString")
    }

    override fun onExitScope() = Unit

    private fun logState(state: State) {
        when (state) {
            is State.CheckingForUpdates -> {
                Logger.test("checking for updates")
            }

            is State.UpdateAvailable -> {
                Logger.test(
                    "update available version=${state.ota.version} url=${state.ota.url} " +
                        "isForced=${state.ota.isForced}",
                )
            }

            is State.ReadyToInstall -> {
                Logger.test("ready to install version=${state.ota.version} url=${state.ota.url}")
            }

            is State.RebootNeeded -> {
                Logger.test("ota applied version=${state.ota.version} url=${state.ota.url}")
            }

            else -> Unit
        }
    }
}
