package com.memfault.usagereporter.receivers

import android.app.Application
import android.content.Context
import android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import com.memfault.bort.android.NetworkCallbackEvent
import com.memfault.bort.android.registerForDefaultNetworkCallback
import com.memfault.bort.android.registerForIntents
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg
import com.memfault.bort.shared.Logger
import com.memfault.usagereporter.ReporterSettingsPreferenceProvider
import com.memfault.usagereporter.onBortEnabledFlow
import com.memfault.usagereporter.receivers.ConnectivityState.CELLULAR
import com.memfault.usagereporter.receivers.ConnectivityState.ETHERNET
import com.memfault.usagereporter.receivers.ConnectivityState.NONE
import com.memfault.usagereporter.receivers.ConnectivityState.UNKNOWN
import com.memfault.usagereporter.receivers.ConnectivityState.WIFI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityReceiver
@Inject constructor(
    private val application: Application,
    private val reporterSettingsPreferenceProvider: ReporterSettingsPreferenceProvider,
) {
    private val connectivityMetric = Reporting.report()
        .stateTracker<ConnectivityState>(
            name = "connectivity.type",
            aggregations = listOf(StateAgg.TIME_PER_HOUR, StateAgg.TIME_TOTALS),
        )
    private val airplaneModeMetric = Reporting.report().boolStateTracker(name = "airplane_mode")
    private val validatedMetric = Reporting.report().boolStateTracker(name = "connectivity.validated")
    private val captivePortalMetric = Reporting.report().boolStateTracker(name = "connectivity.captive_portal")

    private val replaySettingsFlow = MutableSharedFlow<Unit>()

    fun start(scope: CoroutineScope) {
        scope.registerAirplaneMode()
        scope.registerConnectivity()
    }

    /**
     * Triggers a re-emission of the latest connectivity event.
     */
    suspend fun replayLatest() {
        replaySettingsFlow.emit(Unit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun CoroutineScope.registerAirplaneMode() {
        reporterSettingsPreferenceProvider.settings
            .onBortEnabledFlow("ACTION_AIRPLANE_MODE_CHANGED") {
                application.registerForIntents(ACTION_AIRPLANE_MODE_CHANGED)
            }
            .flatMapLatest { intent -> replaySettingsFlow.map { intent }.onStart { emit(intent) } }
            .onEach { intent ->
                val hasState = intent.hasExtra("state")
                if (!hasState) return@onEach
                val state = intent.getBooleanExtra("state", false)
                airplaneModeMetric.state(state)
            }
            .launchIn(this)

        val initialAirplaneMode = isAirplaneModeOn(application)
        airplaneModeMetric.state(initialAirplaneMode)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun CoroutineScope.registerConnectivity() {
        val connectivityService = application.getSystemService(ConnectivityManager::class.java)

        reporterSettingsPreferenceProvider.settings
            .flatMapLatest { request -> replaySettingsFlow.map { request }.onStart { emit(request) } }
            .onBortEnabledFlow("NetworkCallback") {
                connectivityService.registerForDefaultNetworkCallback()
            }
            .flatMapLatest { event -> replaySettingsFlow.map { event }.onStart { emit(event) } }
            .onEach { event ->
                when (event) {
                    is NetworkCallbackEvent.OnAvailable -> {
                        // Not used (we get all the info we need from onCapabilitiesChanged,
                        // which follows this callback).
                    }

                    is NetworkCallbackEvent.OnLost -> {
                        // Always called before onAvailable/onCapabilitiesChanged in my testing.
                        connectivityMetric.state(NONE)
                        validatedMetric.state(false)
                        captivePortalMetric.state(false)
                    }

                    is NetworkCallbackEvent.OnCapabilitiesChanged -> {
                        val networkCapabilities = event.networkCapabilities

                        // Dumb API hides getTransport() so we have to test each one individually
                        // (or use reflection..).
                        val state = when {
                            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> WIFI
                            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> CELLULAR
                            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ETHERNET
                            else -> UNKNOWN
                        }
                        connectivityMetric.state(state)

                        val validated =
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        validatedMetric.state(validated)

                        val captivePortal =
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
                        captivePortalMetric.state(captivePortal)

                        Logger.test(
                            """{"json_log": "connectivity", "state": "$state",
                            |"validated": $validated, "captive_portal": $captivePortal}
                            """.trimMargin(),
                        )
                    }
                }
            }
            .launchIn(this)
    }

    private fun isAirplaneModeOn(context: Context): Boolean {
        return Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    }
}

private enum class ConnectivityState {
    WIFI,
    CELLULAR,
    ETHERNET,
    UNKNOWN,
    NONE,
}
