package com.memfault.bort.battery

import android.app.Application
import android.content.Intent
import android.content.Intent.ACTION_BATTERY_CHANGED
import android.os.BatteryManager
import android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
import androidx.annotation.VisibleForTesting
import com.memfault.bort.Default
import com.memfault.bort.android.registerForIntents
import com.memfault.bort.reporting.NumericAgg.LATEST_VALUE
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

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
    @Default private val defaultCoroutineContext: CoroutineContext,
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

    private val chargeCycleMetric = Reporting.report()
        .distribution(
            name = "battery.charge_cycle_count",
            aggregations = listOf(
                LATEST_VALUE,
            ),
        )

    override fun onEnterScope(scope: Scope) {
        scope.coroutineScope(defaultCoroutineContext).launch {
            application.registerForIntents(ACTION_BATTERY_CHANGED)
                .flowOn(defaultCoroutineContext)
                .collect { intent ->
                    if (intent.action == ACTION_BATTERY_CHANGED) {
                        val now = absoluteTimeProvider().timestamp.toEpochMilli()
                        record(
                            isCharging = intent.isPlugged,
                            level = intent.batteryPercentage,
                            timestampMs = now,
                            cycleCount = intent.cycleCount,
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

    private val Intent.cycleCount: Int?
        // Use the string because the constant was only added in API 34.
        get() = getIntExtra("android.os.extra.CYCLE_COUNT", -1).let {
            when (it) {
                -1 -> null
                else -> it
            }
        }

    @VisibleForTesting
    fun record(
        isCharging: Boolean,
        level: Double,
        timestampMs: Long,
        cycleCount: Int? = null,
    ) {
        chargingMetric.state(isCharging, timestamp = timestampMs)
        levelMetric.record(level, timestamp = timestampMs)
        cycleCount?.let { chargeCycleMetric.record(it.toLong(), timestamp = timestampMs) }
    }
}
