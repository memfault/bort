package com.memfault.bort.connectivity

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings.Global
import com.memfault.bort.android.NetworkCallbackEvent.OnAvailable
import com.memfault.bort.android.NetworkCallbackEvent.OnCapabilitiesChanged
import com.memfault.bort.android.NetworkCallbackEvent.OnLost
import com.memfault.bort.android.registerForDefaultNetworkCallback
import com.memfault.bort.android.registerForIntents
import com.memfault.bort.connectivity.ConnectivityState.BLUETOOTH
import com.memfault.bort.connectivity.ConnectivityState.CELLULAR
import com.memfault.bort.connectivity.ConnectivityState.ETHERNET
import com.memfault.bort.connectivity.ConnectivityState.NONE
import com.memfault.bort.connectivity.ConnectivityState.UNKNOWN
import com.memfault.bort.connectivity.ConnectivityState.WIFI
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg.TIME_PER_HOUR
import com.memfault.bort.reporting.StateAgg.TIME_TOTALS
import com.memfault.bort.scopes.Scope
import com.memfault.bort.scopes.Scoped
import com.memfault.bort.scopes.coroutineScope
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Don't automatically include this in all apps using @ContributesMultibinding (it'll be included in OTA,
// and Bort has to conditionally enable it only if UsageReporter is not installed).
@Singleton
class ConnectivityMetrics
@Inject constructor(
    private val application: Application,
    private val bortEnabledProvider: BortEnabledProvider,
    private val connectivityManager: ConnectivityManager,
) : Scoped {
    private val connectivityMetric = Reporting.report()
        .stateTracker<ConnectivityState>(
            name = "connectivity.type",
            aggregations = listOf(TIME_PER_HOUR, TIME_TOTALS),
        )
    private val airplaneModeMetric = Reporting.report().boolStateTracker(name = "airplane_mode")
    private val validatedMetric = Reporting.report().boolStateTracker(name = "connectivity.validated")
    private val captivePortalMetric = Reporting.report().boolStateTracker(name = "connectivity.captive_portal")

    private val replaySettingsFlow = MutableSharedFlow<Unit>()

    override fun onEnterScope(scope: Scope) {
        scope.coroutineScope().launch {
            registerAirplaneMode()
            registerConnectivity()
        }
    }

    override fun onExitScope() = Unit

    /**
     * Triggers a re-emission of the latest connectivity event.
     */
    suspend fun replayLatest() {
        replaySettingsFlow.emit(Unit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun CoroutineScope.registerAirplaneMode() {
        bortEnabledProvider.isEnabledFlow()
            .distinctUntilChanged()
            .flatMapLatest { bortEnabled ->
                Logger.test("Listening for ACTION_AIRPLANE_MODE_CHANGED: $bortEnabled")
                if (bortEnabled) {
                    application.registerForIntents(ACTION_AIRPLANE_MODE_CHANGED)
                } else {
                    emptyFlow()
                }
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

    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun CoroutineScope.registerConnectivity() {
        bortEnabledProvider.isEnabledFlow()
            .distinctUntilChanged()
            .flatMapLatest { bortEnabled ->
                Logger.test("Listening for NetworkCallback: $bortEnabled")
                if (bortEnabled) {
                    connectivityManager.registerForDefaultNetworkCallback()
                } else {
                    emptyFlow()
                }
            }
            .flatMapLatest { event -> replaySettingsFlow.map { event }.onStart { emit(event) } }
            .onEach { event ->
                when (event) {
                    is OnAvailable -> {
                        // Not used (we get all the info we need from onCapabilitiesChanged,
                        // which follows this callback).
                    }

                    is OnLost -> {
                        // Always called before onAvailable/onCapabilitiesChanged in my testing.
                        recordNetworkLost()
                    }

                    is OnCapabilitiesChanged -> {
                        recordNetworkCapabilities(event.networkCapabilities)
                    }
                }
            }
            .launchIn(this)

        val defaultNetwork = connectivityManager.activeNetwork
        val defaultNetworkCapabilities = connectivityManager.getNetworkCapabilities(defaultNetwork)
        if (defaultNetworkCapabilities != null) {
            recordNetworkCapabilities(defaultNetworkCapabilities)
        } else {
            recordNetworkLost()
        }
    }

    private fun recordNetworkLost() {
        connectivityMetric.state(NONE)
        validatedMetric.state(false)
        captivePortalMetric.state(false)

        logAsJson(state = NONE, validated = false, captivePortal = false)
    }

    private fun recordNetworkCapabilities(networkCapabilities: NetworkCapabilities) {
        // Dumb API hides getTransport() so we have to test each one individually
        // (or use reflection..).
        val state = when {
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> WIFI
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> CELLULAR
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> BLUETOOTH
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

        logAsJson(state = state, validated = validated, captivePortal = captivePortal)
    }

    private fun logAsJson(
        state: ConnectivityState,
        validated: Boolean,
        captivePortal: Boolean,
    ) {
        Logger.test(
            """{"json_log": "connectivity", "state": "$state", """ +
                """"validated": $validated, "captive_portal": $captivePortal}""",
        )
    }

    private fun isAirplaneModeOn(context: Context): Boolean {
        return Global.getInt(context.contentResolver, Global.AIRPLANE_MODE_ON, 0) != 0
    }
}

private enum class ConnectivityState {
    WIFI,
    CELLULAR,
    ETHERNET,
    BLUETOOTH,
    UNKNOWN,
    NONE,
}
