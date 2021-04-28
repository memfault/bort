package com.memfault.bort.metrics

import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import com.memfault.bort.BuildConfig
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

const val DROP_BOX_TRACES_DROP_COUNT = "drop_box_traces_drop_count"
const val STRUCTURED_LOG_DROP_COUNT = "structured_log_drop_count"
private const val DROP_BOX_TRACE_TAG_COUNT_PER_HOUR_TEMPLATE = "drop_box_trace_%s_count"
private const val BORT_VERSION_CODE = "bort_version_code"

/**
 * An abstraction for metric storage.
 *
 * Metrics are assumed to be floats, even though the endpoint implementation uses doubles,
 * the backing main implementation (SharedPreferences) only supports floats.
 */
interface MetricRegistry {
    /**
     * Obtains the value of a metric, returns null if the metric does not exist.
     */
    fun get(name: String): Float?

    /**
     * Stores a metric value, passing null will delete it.
     */
    fun store(name: String, value: Float?)

    /**
     * Atomically updates the value of a metric, allowing callers to compute the new value based
     * on the previous one. The old value is returned.
     * Implementations must guarantee read-write order to ensure consistency.
     */
    fun reduce(name: String, newValueFn: (oldValue: Float?) -> Float?): Float?

    /**
     * Obtain a list of existing keys
     */
    fun keys(): Collection<String>
}

private fun Float.nanAsNull(): Float? =
    if (this.isNaN()) null
    else this

/**
 * An implementation of metric registry backed by shared preferences, read-write order is guaranteed
 * via local locking in the registry. The registry implementation only guarantees read-write order
 * for calls within this registry instance.
 * This implementation uses Float.NaN with an internal semantic meaning of null, thus Float.Nan is not
 * supported as a valid metric value.
 * If keys() is used, it is important that the shared preferences instance is used exclusively for
 * metrics to prevent contamination by other uses.
 */
class SharedPreferencesMetricRegistry(
    private val preferences: SharedPreferences
) : MetricRegistry {
    private val lock = ReentrantReadWriteLock()

    override fun get(name: String): Float? = lock.read {
        preferences.getFloat(name, Float.NaN)
            .nanAsNull()
    }

    override fun store(name: String, value: Float?) {
        // note: apply will return instantly and schedule a write in the future
        // readers will see the pending value even if read before writing is complete.
        // If bort crashes before writing is scheduled, the metric might be lost, if this
        // is considered an issue, apply() should be changed to save(), which will block
        // until writing is done.
        lock.write {
            preferences
                .edit()
                .let {
                    if (value == null) it.remove(name)
                    else it.putFloat(name, value)
                }.apply()
        }
    }

    override fun reduce(name: String, newValueFn: (oldValue: Float?) -> Float?): Float? = lock.write {
        val old = get(name)
        val new = newValueFn(old)
        store(name, new)
        old
    }

    override fun keys(): Collection<String> = lock.read {
        // note: explicitly copying the list because SharedPreferences#getKeys returns the internal mutable structure
        preferences
            .all
            .keys
            .toList()
    }
}

/**
 * A store for built-in metrics.
 */
class BuiltinMetricsStore(
    private val registry: MetricRegistry
) {
    fun increment(name: String) {
        registry.reduce(name) { old -> (old ?: 0.0f) + 1 }
    }

    @VisibleForTesting
    fun collect(name: String): Float =
        registry.reduce(name) { _ -> null } ?: 0.0f

    @VisibleForTesting
    fun get(name: String): Float? = registry.get(name)

    fun collectMetrics(): Map<String, Float> =
        registry.keys()
            .map { it to collect(it) }
            .toMap()
}

fun metricForTraceTag(tag: String) = DROP_BOX_TRACE_TAG_COUNT_PER_HOUR_TEMPLATE
    .format(tag.toLowerCase(Locale.ROOT))

val constantBuiltinMetrics = mapOf(
    BORT_VERSION_CODE to BuildConfig.VERSION_CODE.toFloat()
)
