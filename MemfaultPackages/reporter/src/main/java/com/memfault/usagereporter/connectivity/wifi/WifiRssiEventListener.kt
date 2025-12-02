package com.memfault.usagereporter.connectivity.wifi

import android.app.Application
import com.memfault.bort.Default
import com.memfault.bort.android.registerForIntents
import com.memfault.bort.reporting.NumericAgg.EXP_MOVING_AVG_RSSI
import com.memfault.bort.reporting.NumericAgg.LATEST_VALUE
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.scopes.Scope
import com.memfault.bort.scopes.Scoped
import com.memfault.bort.scopes.coroutineScope
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@ContributesMultibinding(SingletonComponent::class, boundType = Scoped::class)
class WifiRssiEventListener @Inject constructor(
    private val application: Application,
    @Default private val defaultCoroutineContext: CoroutineContext,
) : Scoped {
    private val metric = Reporting.report()
        .distribution("connectivity.wifi.rssi", aggregations = listOf(EXP_MOVING_AVG_RSSI, LATEST_VALUE))

    override fun onEnterScope(scope: Scope) {
        Logger.v("Registering for wifi rssi changes")

        val coroutineScope = scope.coroutineScope(defaultCoroutineContext)
        application.registerForIntents(ACTION_RSSI_CHANGED)
            .onEach { intent ->
                val newRssi = intent.getIntExtra(EXTRA_NEW_RSSI, Int.MIN_VALUE)
                if (newRssi != Int.MIN_VALUE) {
                    Logger.test("Received new wifi rssi value: $newRssi")
                    metric.record(newRssi.toLong())
                }
            }
            .launchIn(coroutineScope)
    }

    override fun onExitScope() {
        Logger.v("Unregistering for wifi rssi changes")
    }

    companion object {
        const val ACTION_RSSI_CHANGED = "android.net.wifi.RSSI_CHANGED"
        const val EXTRA_NEW_RSSI = "newRssi"
    }
}
