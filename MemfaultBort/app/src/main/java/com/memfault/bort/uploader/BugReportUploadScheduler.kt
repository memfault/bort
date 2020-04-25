package com.memfault.bort.uploader

import android.content.Context
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.INTENT_EXTRA_BUGREPORT_PATH
import com.memfault.bort.SettingsProvider
import java.io.File
import java.util.*

private const val WORK_TAG = "com.memfault.bort.work.tag.UPLOAD"

class BugReportUploadScheduler(
    private val context: Context,
    private val settingsProvider: SettingsProvider
) {
    private val constraints: Constraints by lazy {
        Constraints.Builder()
            .setRequiredNetworkType(settingsProvider.bugReportNetworkConstraint().networkType)
            .build()
    }

    fun enqueue(file: File): UUID =
        OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(INTENT_EXTRA_BUGREPORT_PATH to file.toString()))
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build().also {
                WorkManager.getInstance(context)
                    .enqueue(it)
            }.id
}
