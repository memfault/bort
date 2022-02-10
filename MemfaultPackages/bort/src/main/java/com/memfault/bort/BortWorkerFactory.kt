package com.memfault.bort

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.memfault.bort.clientserver.MarUploadTask
import com.memfault.bort.dropbox.DropBoxGetEntriesTask
import com.memfault.bort.logcat.LogcatCollectionTask
import com.memfault.bort.metrics.MetricsCollectionTask
import com.memfault.bort.requester.UptimeTickTask
import com.memfault.bort.settings.PeriodicRequesterRestartTask
import com.memfault.bort.settings.SettingsUpdateTask
import com.memfault.bort.uploader.FileUploadTask
import com.memfault.bort.uploader.HttpTask
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

interface IndividualWorkerFactory {
    fun create(workerParameters: WorkerParameters): ListenableWorker
    fun type(): KClass<out ListenableWorker>
}

class BortWorkerFactory @Inject constructor(
    private val workerFactories: InjectSet<IndividualWorkerFactory>,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = workerFactories.firstOrNull {
        it.type().qualifiedName == workerClassName
    }?.create(workerParameters)
}

class BortTaskFactory @Inject constructor(
    private val http: Provider<HttpTask>,
    private val fileUpload: Provider<FileUploadTask>,
    private val dropBox: Provider<DropBoxGetEntriesTask>,
    private val metrics: Provider<MetricsCollectionTask>,
    private val bugReportTimeout: Provider<BugReportRequestTimeoutTask>,
    private val logcat: Provider<LogcatCollectionTask>,
    private val fileUploadTimeout: Provider<FileUploadHoldingAreaTimeoutTask>,
    private val settings: Provider<SettingsUpdateTask>,
    private val periodicReq: Provider<PeriodicRequesterRestartTask>,
    private val uptime: Provider<UptimeTickTask>,
    private val marUpload: Provider<MarUploadTask>,
) : TaskFactory {
    override fun create(inputData: Data): Task<*>? {
        return when (inputData.workDelegateClass) {
            HttpTask::class.qualifiedName -> http.get()
            FileUploadTask::class.qualifiedName -> fileUpload.get()
            DropBoxGetEntriesTask::class.qualifiedName -> dropBox.get()
            MetricsCollectionTask::class.qualifiedName -> metrics.get()
            BugReportRequestTimeoutTask::class.qualifiedName -> bugReportTimeout.get()
            LogcatCollectionTask::class.qualifiedName -> logcat.get()
            FileUploadHoldingAreaTimeoutTask::class.qualifiedName -> fileUploadTimeout.get()
            SettingsUpdateTask::class.qualifiedName -> settings.get()
            PeriodicRequesterRestartTask::class.qualifiedName -> periodicReq.get()
            UptimeTickTask::class.qualifiedName -> uptime.get()
            MarUploadTask::class.qualifiedName -> marUpload.get()
            else -> null
        }
    }
}
