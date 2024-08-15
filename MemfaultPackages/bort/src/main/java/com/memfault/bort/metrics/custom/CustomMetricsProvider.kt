package com.memfault.bort.metrics.custom

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.memfault.bort.reporting.FinishReport
import com.memfault.bort.reporting.MetricValue
import com.memfault.bort.reporting.RemoteMetricsService.KEY_CUSTOM_METRIC
import com.memfault.bort.reporting.RemoteMetricsService.URI_ADD_CUSTOM_METRIC
import com.memfault.bort.reporting.RemoteMetricsService.URI_FINISH_CUSTOM_REPORT
import com.memfault.bort.reporting.RemoteMetricsService.URI_START_CUSTOM_REPORT
import com.memfault.bort.reporting.StartReport
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.shared.Logger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import org.json.JSONException

class CustomMetricsProvider : ContentProvider() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CustomMetricsProviderEntryPoint {
        fun customMetrics(): CustomMetrics
        fun bortEnabledProvider(): BortEnabledProvider
    }

    val entryPoint: CustomMetricsProviderEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            context!!,
            CustomMetricsProviderEntryPoint::class.java,
        )
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (!listOf(URI_ADD_CUSTOM_METRIC, URI_START_CUSTOM_REPORT, URI_FINISH_CUSTOM_REPORT).contains(uri)) {
            Logger.w("CustomMetricsProvider: unknown uri: $uri")
            return null
        }
        if (!entryPoint.bortEnabledProvider().isEnabled()) {
            Logger.d("CustomMetricsProvider: Bort is disabled")
            return null
        }
        val metricJson = values?.getAsString(KEY_CUSTOM_METRIC)
        if (metricJson == null) {
            Logger.w("CustomMetricsProvider: no metric extra")
            return null
        }
        return try {
            when (uri) {
                URI_ADD_CUSTOM_METRIC -> {
                    val metricValue = MetricValue.fromJson(metricJson)
                    Logger.test("CustomMetricsProvider received: $metricValue")
                    runBlocking {
                        if (entryPoint.customMetrics().add(metricValue) != -1L) {
                            // Return the URI if successful.
                            URI_ADD_CUSTOM_METRIC
                        } else {
                            null
                        }
                    }
                }
                URI_START_CUSTOM_REPORT -> {
                    val startReport = StartReport.fromJson(metricJson)
                    Logger.test("CustomMetricsProvider received: $startReport")
                    runBlocking {
                        if (entryPoint.customMetrics().start(startReport) != -1L) {
                            // Return the URI if successful.
                            URI_START_CUSTOM_REPORT
                        } else {
                            null
                        }
                    }
                }
                URI_FINISH_CUSTOM_REPORT -> {
                    val finishReport = FinishReport.fromJson(metricJson)
                    Logger.test("CustomMetricsProvider received: $finishReport")
                    runBlocking {
                        if (entryPoint.customMetrics().finish(finishReport) != -1L) {
                            // Return the URI if successful.
                            URI_FINISH_CUSTOM_REPORT
                        } else {
                            null
                        }
                    }
                }
                else -> error("CustomMetricsProvider: unknown uri: $uri")
            }
        } catch (e: JSONException) {
            Logger.w("CustomMetricsProvider: error deserializing metric", e)
            null
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        0
}
