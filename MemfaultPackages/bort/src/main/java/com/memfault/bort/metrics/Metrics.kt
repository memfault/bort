package com.memfault.bort.metrics

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.memfault.bort.BuildConfig
import com.memfault.bort.DumpsterClient
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.parsers.Package
import com.memfault.bort.shared.APPLICATION_ID_MEMFAULT_USAGE_REPORTER
import com.memfault.bort.shared.BuildConfig as SharedBuildConfig
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FIELD
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.serialization.json.JsonPrimitive

const val BORT_CRASH = "bort_crash"
const val BORT_STARTED = "bort_started"
const val REQUEST_ATTEMPT = "request_attempt"
const val REQUEST_TIMING = "request_timing"
const val REQUEST_FAILED = "request_failed"
const val UPLOAD_FILE_FILE_MISSING = "upload_failed_file_missing"
const val RATE_LIMIT_APPLIED = "rate_limit_applied"
const val BUG_REPORT_DELETED_STORAGE = "bug_report_deleted_storage"
const val BUG_REPORT_DELETED_OLD = "bug_report_deleted_old"
const val SETTINGS_CHANGED = "settings_changed"
const val BATTERYSTATS_FAILED = "batterystats_failed"
const val MAX_ATTEMPTS = "max_attempts"
private const val DROP_BOX_TRACE_TAG_COUNT_PER_HOUR_TEMPLATE = "drop_box_trace_%s_count"
private const val BORT_VERSION_CODE = "bort_version_code"
private const val BORT_VERSION_NAME = "bort_version_name"
private const val BORT_UPSTREAM_VERSION_CODE = "bort_upstream_version_code"
private const val BORT_UPSTREAM_VERSION_NAME = "bort_upstream_version_name"
private const val USAGE_REPORTER_VERSION_CODE = "usagereporter_version_code"
private const val USAGE_REPORTER_VERSION_NAME = "usagereporter_version_name"
private const val DUMPSTER_VERSION = "dumpster_version"
private const val RUNTIME_ENABLE_REQUIRED = "runtime_enable_required"
private const val OS_VERSION = "os_version"

// Value metrics are stored using 3 values:
private const val VALUE_COUNT_POSTFIX = "_count"
private const val VALUE_SUM_POSTFIX = "_sum"
private const val VALUE_MIN_POSTFIX = "_min"
private const val VALUE_MAX_POSTFIX = "_max"

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
    fun store(name: String, value: Float?, synchronous: Boolean = false)

    /**
     * Atomically updates the value of a metric, allowing callers to compute the new value based
     * on the previous one. The old value is returned.
     * Implementations must guarantee read-write order to ensure consistency.
     */
    fun reduce(name: String, synchronous: Boolean = false, newValueFn: (oldValue: Float?) -> Float?): Float?

    /**
     * Obtain a list of existing keys
     */
    fun keys(): Collection<String>
}

private fun Float.nanAsNull(): Float? =
    if (this.isNaN()) null
    else this

@Qualifier
@Retention(RUNTIME)
@Target(FIELD, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
annotation class Metrics

/**
 * An implementation of metric registry backed by shared preferences, read-write order is guaranteed
 * via local locking in the registry. The registry implementation only guarantees read-write order
 * for calls within this registry instance.
 * This implementation uses Float.NaN with an internal semantic meaning of null, thus Float.Nan is not
 * supported as a valid metric value.
 * If keys() is used, it is important that the shared preferences instance is used exclusively for
 * metrics to prevent contamination by other uses.
 */
@Singleton
@ContributesBinding(scope = SingletonComponent::class)
class SharedPreferencesMetricRegistry @Inject constructor(
    @Metrics private val preferences: SharedPreferences,
) : MetricRegistry {
    private val lock = ReentrantReadWriteLock()

    override fun get(name: String): Float? = lock.read {
        preferences.getFloat(name, Float.NaN)
            .nanAsNull()
    }

    @SuppressLint("ApplySharedPref")
    override fun store(name: String, value: Float?, synchronous: Boolean) {
        // note: if !synchronous, apply will return instantly and schedule a write in the future
        // readers will see the pending value even if read before writing is complete.
        // If bort crashes before writing is scheduled, the metric might be lost, if this
        // is considered an issue, synchronous should be set.
        lock.write {
            preferences
                .edit()
                .let {
                    if (value == null) it.remove(name)
                    else it.putFloat(name, value)
                }.also {
                    if (synchronous) it.commit() else it.apply()
                }
        }
    }

    override fun reduce(
        name: String,
        synchronous: Boolean,
        newValueFn: (oldValue: Float?) -> Float?,
    ): Float? = lock.write {
        val old = get(name)
        val new = newValueFn(old)
        store(name, new, synchronous)
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
class BuiltinMetricsStore @Inject constructor(
    private val registry: MetricRegistry,
) {
    /**
     * Add a simple counting metric: number of times an event happened.
     *
     * @param synchronous should be written to persistent storage immediately?
     */
    fun increment(name: String, synchronous: Boolean = false) {
        registry.reduce(name, synchronous) { old -> (old ?: 0.0f) + 1 }
    }

    /**
     * Add a metric where we want to keep track of the average/min/maximum value observed.
     */
    fun addValue(name: String, value: Float, synchronous: Boolean = false) {
        increment(name + VALUE_COUNT_POSTFIX, synchronous)
        registry.reduce(name + VALUE_SUM_POSTFIX, synchronous) { old -> (old ?: 0.0f) + value }
        val minKey = name + VALUE_MIN_POSTFIX
        if (value < registry.get(minKey) ?: Float.MAX_VALUE) {
            registry.store(minKey, value, synchronous)
        }
        val maxKey = name + VALUE_MAX_POSTFIX
        if (value > registry.get(maxKey) ?: Float.MIN_VALUE) {
            registry.store(maxKey, value, synchronous)
        }
    }

    @VisibleForTesting
    fun collect(name: String): Float =
        registry.reduce(name) { _ -> null } ?: 0.0f

    @VisibleForTesting
    fun get(name: String): Float? = registry.get(name)

    fun collectMetrics(): Map<String, JsonPrimitive> =
        registry.keys()
            .map { it to JsonPrimitive(collect(it)) }
            .toMap()
}

fun metricForTraceTag(tag: String) = DROP_BOX_TRACE_TAG_COUNT_PER_HOUR_TEMPLATE
    .format(tag.toLowerCase(Locale.ROOT))

private var cachedReporterVersion: Package? = null
private var cachedDumpsterVersion: Int? = null

private suspend fun PackageManagerClient.getUsageReporterVersion(): Package? = cachedReporterVersion
    ?: findPackageByApplicationId(APPLICATION_ID_MEMFAULT_USAGE_REPORTER)?.also {
        cachedReporterVersion = it
    }

private fun DumpsterClient.getDumpsterVersion(): Int = cachedDumpsterVersion
    ?: availableVersion().also {
        cachedDumpsterVersion = it
    } ?: 0

suspend fun updateBuiltinProperties(
    packageManagerClient: PackageManagerClient,
    devicePropertiesStore: DevicePropertiesStore,
    dumpsterClient: DumpsterClient,
) {
    devicePropertiesStore.upsert(name = BORT_VERSION_CODE, value = BuildConfig.VERSION_CODE, internal = true)
    devicePropertiesStore.upsert(name = BORT_VERSION_NAME, value = BuildConfig.VERSION_NAME, internal = true)
    devicePropertiesStore.upsert(
        name = BORT_UPSTREAM_VERSION_CODE,
        value = SharedBuildConfig.UPSTREAM_VERSION_CODE,
        internal = true,
    )
    devicePropertiesStore.upsert(
        name = BORT_UPSTREAM_VERSION_NAME,
        value = SharedBuildConfig.UPSTREAM_VERSION_NAME,
        internal = true,
    )
    devicePropertiesStore.upsert(
        name = USAGE_REPORTER_VERSION_CODE,
        value = packageManagerClient.getUsageReporterVersion()?.versionCode ?: 0,
        internal = true,
    )
    devicePropertiesStore.upsert(
        name = USAGE_REPORTER_VERSION_NAME,
        value = packageManagerClient.getUsageReporterVersion()?.versionName ?: "",
        internal = true,
    )
    devicePropertiesStore.upsert(name = DUMPSTER_VERSION, value = dumpsterClient.getDumpsterVersion(), internal = true)
    devicePropertiesStore.upsert(
        name = RUNTIME_ENABLE_REQUIRED,
        value = BuildConfig.RUNTIME_ENABLE_REQUIRED,
        internal = true,
    )
    devicePropertiesStore.upsert(name = OS_VERSION, value = Build.VERSION.SDK_INT, internal = true)
}
