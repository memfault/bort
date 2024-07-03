package com.memfault.bort.battery

import android.app.Application
import android.content.Intent
import android.content.Intent.ACTION_BATTERY_CHANGED
import android.os.BatteryManager
import android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
import androidx.annotation.VisibleForTesting
import com.memfault.bort.android.registerForIntents
import com.memfault.bort.reporting.NumericAgg.VALUE_DROP
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg.TIME_TOTALS
import com.memfault.bort.scopes.Scope
import com.memfault.bort.scopes.Scoped
import com.memfault.bort.scopes.coroutineScope
import com.memfault.bort.time.AbsoluteTimeProvider
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import javax.inject.Inject

// Note that these 2 metrics are removed from reports before they're uploaded.
const val BATTERY_CHARGING_METRIC = "temp.battery.charging"
const val BATTERY_LEVEL_METRIC = "temp.battery.level"

interface BatterySessionVitals {
    fun onChargingChanged(isCharging: Boolean)
}

/**
 * Used to calculate the Battery Device Vitals for Sessions.
 *
 * Composed of 3 signals:
 *   1. listens to the power connected/disconnected intent (will wake app up)
 *   2. listens to the battery changed intent
 *   3. waits for the app to wake up
 *
 * When any signal fires, we record both the battery level and the charging status. This allows us to calculate the
 * total battery level drop, because we record all battery changes, and we'll be notified whenever the charger
 * is plugged or unplugged, so we can calculate the time that the charger is unplugged.
 *
 * This is one case that we basically can't cover, which is if the device is discharging, our application is sleeping,
 * and then the device is powered off, and then charged, and then powered back on again. We won't have captured the
 * battery drop accurately, and we won't calculate the discharge time properly. Since this is a rare situation
 * where the device is asleep, it really shouldn't count as a proper "session", and we warn about that in the docs.
 */
@ContributesMultibinding(SingletonComponent::class, boundType = Scoped::class)
@ContributesBinding(SingletonComponent::class, boundType = BatterySessionVitals::class)
class RealBatterySessionVitals
@Inject constructor(
    private val application: Application,
    private val absoluteTimeProvider: AbsoluteTimeProvider,
    private val batteryManager: BatteryManager,
) : BatterySessionVitals, Scoped {

    private val chargingMetric by lazy {
        Reporting.report()
            .boolStateTracker(
                name = BATTERY_CHARGING_METRIC,
                aggregations = listOf(TIME_TOTALS),
                internal = true,
            )
    }

    private val levelMetric by lazy {
        Reporting.report()
            .distribution(
                name = BATTERY_LEVEL_METRIC,
                aggregations = listOf(VALUE_DROP),
                internal = true,
            )
    }

    override fun onEnterScope(scope: Scope) {
        scope.coroutineScope().launch {
            application.registerForIntents(ACTION_BATTERY_CHANGED)
                .collect { intent ->
                    if (intent.action == ACTION_BATTERY_CHANGED) {
                        val now = absoluteTimeProvider().timestamp.toEpochMilli()
                        record(
                            isCharging = intent.isPlugged,
                            level = intent.batteryPercentage,
                            timestampMs = now,
                        )
                    }
                }
        }
    }

    override fun onExitScope() = Unit

    override fun onChargingChanged(isCharging: Boolean) {
        val now = absoluteTimeProvider().timestamp.toEpochMilli()
        val level = batteryManager.getLongProperty(BATTERY_PROPERTY_CAPACITY).toDouble()

        record(
            isCharging = isCharging,
            level = level,
            timestampMs = now,
        )
    }

    private val Intent.batteryPercentage: Double
        get() {
            val level: Int = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            return level * 100 / scale.toDouble()
        }

    private val Intent.isPlugged: Boolean
        get() = getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) == 1

    @VisibleForTesting
    fun record(
        isCharging: Boolean,
        level: Double,
        timestampMs: Long,
    ) {
        chargingMetric.state(isCharging, timestamp = timestampMs)
        levelMetric.record(level, timestamp = timestampMs)
    }
}
