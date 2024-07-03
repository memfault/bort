package com.memfault.bort.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memfault.bort.battery.BatterySessionVitals
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val ACTION_POWER_CONNECTED = "android.intent.action.ACTION_POWER_CONNECTED"
private const val ACTION_POWER_DISCONNECTED = "android.intent.action.ACTION_POWER_DISCONNECTED"

/**
 * This is a manifest registered receiver, because [ACTION_POWER_CONNECTED] and [ACTION_POWER_DISCONNECTED] will
 * wake the application up when they're fired.
 */
@AndroidEntryPoint
class ChargeStateReceiver : BroadcastReceiver() {
    @Inject lateinit var batterySessionVitals: BatterySessionVitals

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        if (action in setOf(ACTION_POWER_CONNECTED, ACTION_POWER_DISCONNECTED)) {
            batterySessionVitals.onChargingChanged(isCharging = action == ACTION_POWER_CONNECTED)
        }
    }
}
