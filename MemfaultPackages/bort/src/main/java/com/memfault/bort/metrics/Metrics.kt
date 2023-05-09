package com.memfault.bort.metrics

import android.os.Build
import com.memfault.bort.BuildConfig
import com.memfault.bort.DumpsterClient
import com.memfault.bort.InstallationIdProvider
import com.memfault.bort.IntegrationChecker
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.parsers.Package
import com.memfault.bort.reporting.NumericAgg
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.shared.APPLICATION_ID_MEMFAULT_USAGE_REPORTER
import com.memfault.bort.shared.BuildConfig as SharedBuildConfig
import java.util.Locale
import javax.inject.Inject
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
const val BORT_INSTALLATION_ID = "bort_installation_id"
private const val BORT_VERSION_CODE = "bort_version_code"
private const val BORT_VERSION_NAME = "bort_version_name"
const val BORT_UPSTREAM_VERSION_CODE = "bort_upstream_version_code"
const val BORT_UPSTREAM_VERSION_NAME = "bort_upstream_version_name"
private const val USAGE_REPORTER_VERSION_CODE = "usagereporter_version_code"
private const val USAGE_REPORTER_VERSION_NAME = "usagereporter_version_name"
private const val DUMPSTER_VERSION = "dumpster_version"
private const val RUNTIME_ENABLE_REQUIRED = "runtime_enable_required"
private const val OS_VERSION = "os_version"
private const val BORT_PACKAGE_NAME = "bort_package_name"

/**
 * A store for built-in metrics.
 */
class BuiltinMetricsStore @Inject constructor() {
    /**
     * Add a simple counting metric: number of times an event happened.
     */
    fun increment(name: String, incrementBy: Int = 1) {
        Reporting.report().counter(name = name, sumInReport = true, internal = true).incrementBy(incrementBy)
    }

    /**
     * Add a metric where we want to keep track of the average/min/maximum value observed.
     */
    fun addValue(name: String, value: Double) {
        Reporting.report().distribution(
            name = name,
            aggregations = listOf(NumericAgg.COUNT, NumericAgg.MIN, NumericAgg.MAX, NumericAgg.SUM),
            internal = true
        ).record(value)
    }

    fun addValue(name: String, value: Long) {
        Reporting.report().distribution(
            name = name,
            aggregations = listOf(NumericAgg.COUNT, NumericAgg.MIN, NumericAgg.MAX, NumericAgg.SUM),
            internal = true
        ).record(value)
    }
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

/**
 * Inserts core Bort internal metrics using Custom Metric APIs, and also returns them in a map for fallback use.
 */
suspend fun updateBuiltinProperties(
    packageManagerClient: PackageManagerClient,
    devicePropertiesStore: DevicePropertiesStore,
    dumpsterClient: DumpsterClient,
    integrationChecker: IntegrationChecker,
    installationIdProvider: InstallationIdProvider,
): Map<String, JsonPrimitive> {
    val metrics = mutableMapOf<String, JsonPrimitive>()
    metrics[BORT_VERSION_CODE] = JsonPrimitive(BuildConfig.VERSION_CODE)
    devicePropertiesStore.upsert(name = BORT_VERSION_CODE, value = BuildConfig.VERSION_CODE, internal = true)

    metrics[BORT_VERSION_NAME] = JsonPrimitive(BuildConfig.VERSION_NAME)
    devicePropertiesStore.upsert(name = BORT_VERSION_NAME, value = BuildConfig.VERSION_NAME, internal = true)

    metrics[BORT_UPSTREAM_VERSION_CODE] = JsonPrimitive(SharedBuildConfig.UPSTREAM_VERSION_CODE)
    devicePropertiesStore.upsert(
        name = BORT_UPSTREAM_VERSION_CODE,
        value = SharedBuildConfig.UPSTREAM_VERSION_CODE,
        internal = true,
    )

    metrics[BORT_UPSTREAM_VERSION_NAME] = JsonPrimitive(SharedBuildConfig.UPSTREAM_VERSION_NAME)
    devicePropertiesStore.upsert(
        name = BORT_UPSTREAM_VERSION_NAME,
        value = SharedBuildConfig.UPSTREAM_VERSION_NAME,
        internal = true,
    )

    val usageReporterVersionCode = packageManagerClient.getUsageReporterVersion()?.versionCode ?: 0
    metrics[USAGE_REPORTER_VERSION_CODE] = JsonPrimitive(usageReporterVersionCode)
    devicePropertiesStore.upsert(
        name = USAGE_REPORTER_VERSION_CODE,
        value = usageReporterVersionCode,
        internal = true,
    )

    val usageReporterVersionName = packageManagerClient.getUsageReporterVersion()?.versionName ?: ""
    metrics[USAGE_REPORTER_VERSION_NAME] = JsonPrimitive(usageReporterVersionName)
    devicePropertiesStore.upsert(
        name = USAGE_REPORTER_VERSION_NAME,
        value = usageReporterVersionName,
        internal = true,
    )

    val dumpsterVersion = dumpsterClient.getDumpsterVersion()
    metrics[DUMPSTER_VERSION] = JsonPrimitive(dumpsterVersion)
    devicePropertiesStore.upsert(name = DUMPSTER_VERSION, value = dumpsterVersion, internal = true)

    metrics[RUNTIME_ENABLE_REQUIRED] = JsonPrimitive(BuildConfig.RUNTIME_ENABLE_REQUIRED)
    devicePropertiesStore.upsert(
        name = RUNTIME_ENABLE_REQUIRED,
        value = BuildConfig.RUNTIME_ENABLE_REQUIRED,
        internal = true,
    )

    metrics[OS_VERSION] = JsonPrimitive(Build.VERSION.SDK_INT)
    devicePropertiesStore.upsert(name = OS_VERSION, value = Build.VERSION.SDK_INT, internal = true)

    metrics[BORT_INSTALLATION_ID] = JsonPrimitive(installationIdProvider.id())
    devicePropertiesStore.upsert(name = BORT_INSTALLATION_ID, value = installationIdProvider.id(), internal = true)

    metrics[BORT_PACKAGE_NAME] = JsonPrimitive(BuildConfig.APPLICATION_ID)
    devicePropertiesStore.upsert(name = BORT_PACKAGE_NAME, value = BuildConfig.APPLICATION_ID, internal = true)

    return metrics + integrationChecker.checkIntegrationAndReport()
}
