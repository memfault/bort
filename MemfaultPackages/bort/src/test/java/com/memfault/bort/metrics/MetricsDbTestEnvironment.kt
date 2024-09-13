package com.memfault.bort.metrics

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.memfault.bort.DeviceInfo
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.metrics.custom.CustomMetrics
import com.memfault.bort.metrics.custom.RealCustomMetrics
import com.memfault.bort.metrics.database.MetricsDb
import com.memfault.bort.reporting.FinishReport
import com.memfault.bort.reporting.MetricValue
import com.memfault.bort.reporting.RemoteMetricsService
import com.memfault.bort.reporting.StartReport
import com.memfault.bort.settings.DailyHeartbeatEnabled
import com.memfault.bort.settings.HighResMetricsEnabled
import com.memfault.bort.test.util.TemporaryFolderTemporaryFileFactory
import com.memfault.bort.tokenbucket.TokenBucketStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import org.junit.rules.TemporaryFolder

class MetricsDbTestEnvironment(
    private val temporaryFolder: TemporaryFolder,
) : ExternalResource() {

    lateinit var db: MetricsDb
    lateinit var dao: CustomMetrics

    var dailyHeartbeatEnabledValue: Boolean = false
    private val dailyHeartbeatEnabled = DailyHeartbeatEnabled { dailyHeartbeatEnabledValue }

    private val tokenBucketStore = mockk<TokenBucketStore> {
        val rateLimited = false
        every { takeSimple(any(), any(), any()) } answers { !rateLimited }
    }

    var deviceInfo = DeviceInfo(
        deviceSerial = "device-serial",
        hardwareVersion = "hardware-version",
        softwareVersion = "software-version",
    )
    private val deviceInfoProvider = object : DeviceInfoProvider {
        override suspend fun getDeviceInfo(): DeviceInfo = deviceInfo
    }

    var highResMetricsEnabledValue = false
    private val highResMetricsEnabled = HighResMetricsEnabled { highResMetricsEnabledValue }

    override fun before() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MetricsDb::class.java)
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()

        dao = RealCustomMetrics(
            db = db,
            temporaryFileFactory = TemporaryFolderTemporaryFileFactory(temporaryFolder),
            dailyHeartbeatEnabled = dailyHeartbeatEnabled,
            highResMetricsEnabled = highResMetricsEnabled,
            sessionMetricsTokenBucketStore = tokenBucketStore,
            deviceInfoProvider = deviceInfoProvider,
        )

        val contentResolver = mockk<ContentResolver> {
            every { insert(any(), any()) } answers {
                runBlocking {
                    val uri = it.invocation.args[0] as Uri
                    val values = it.invocation.args[1] as ContentValues
                    when (uri) {
                        RemoteMetricsService.URI_ADD_CUSTOM_METRIC -> {
                            val metricJson = values.getAsString(RemoteMetricsService.KEY_CUSTOM_METRIC)
                            val metricValue = MetricValue.fromJson(metricJson)
                            dao.add(metricValue)
                            uri
                        }

                        RemoteMetricsService.URI_START_CUSTOM_REPORT -> {
                            val startJson = values.getAsString(RemoteMetricsService.KEY_CUSTOM_METRIC)
                            val startValue = StartReport.fromJson(startJson)
                            if (dao.start(startValue) != -1L) uri else null
                        }

                        RemoteMetricsService.URI_FINISH_CUSTOM_REPORT -> {
                            val finishJson = values.getAsString(RemoteMetricsService.KEY_CUSTOM_METRIC)
                            val finishValue = FinishReport.fromJson(finishJson)
                            if (dao.finish(finishValue) != -1L) uri else null
                        }

                        else -> {
                            error("Unhandled uri: $uri")
                        }
                    }
                }
            }
        }
        RemoteMetricsService.context = mockk {
            every { getContentResolver() } returns contentResolver
        }
    }

    override fun after() {
        db.close()
    }
}
