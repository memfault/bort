package com.memfault.bort.metrics.custom

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.memfault.bort.BortSystemCapabilities
import com.memfault.bort.reporting.MetricValue
import com.memfault.bort.reporting.RemoteMetricsService.KEY_CUSTOM_METRIC
import com.memfault.bort.reporting.RemoteMetricsService.URI_ADD_CUSTOM_METRIC
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
        fun bortSystemCapabilities(): BortSystemCapabilities
    }

    fun entryPoint() = EntryPointAccessors.fromApplication(context!!, CustomMetricsProviderEntryPoint::class.java)

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
        if (!entryPoint().bortSystemCapabilities().useBortMetricsDb()) {
            Logger.v("Not processing metric: Bort Metrics DB disabled")
            return null
        }
        if (uri != URI_ADD_CUSTOM_METRIC) {
            Logger.w("CustomMetricsProvider: unknown uri: $uri")
            return null
        }
        val metricJson = values?.getAsString(KEY_CUSTOM_METRIC)
        if (metricJson == null) {
            Logger.w("CustomMetricsProvider: no metric extra")
            return null
        }
        try {
            val metricValue = MetricValue.fromJson(metricJson)
            Logger.test("CustomMetricsProvider received: $metricValue")
            runBlocking {
                entryPoint().customMetrics().add(metricValue)
            }
        } catch (e: JSONException) {
            Logger.w("CustomMetricsProvider: error deserializing metric", e)
        }
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        0
}
