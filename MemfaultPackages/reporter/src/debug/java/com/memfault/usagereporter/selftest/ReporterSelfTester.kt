package com.memfault.usagereporter.selftest

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.runAndTrackExceptions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject

private fun Boolean.toTestResult(): String =
    when (this) {
        true -> "success"
        false -> "failed"
    }

interface ReporterSelfTestCase {
    suspend fun test(): Boolean
}

class ReporterSelfTester
@Inject constructor(
    private val initializedTestCase: InitializedTestCase,
) {
    suspend fun run(): Boolean =
        listOf(
            initializedTestCase
        ).map { case ->
            try {
                Logger.test("Running $case...")
                case.test()
            } catch (e: Exception) {
                Logger.e("Test $case raised an exception", e)
                false
            }.also { result ->
                Logger.test("Done $case: ${result.toTestResult()}")
            }
        }.all { it }
}

@HiltWorker
class ReporterSelfTesterWorker
@AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val workerParameters: WorkerParameters,
    private val tester: ReporterSelfTester,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = runAndTrackExceptions(jobName = "SelfTestWorker") {
        Logger.test("UsageReporter self test: ${tester.run().toTestResult()}")
        Result.success()
    }

    companion object {
        private const val WORK_UNIQUE_NAME_REPORTER_SELF_TESTER = "WORK_UNIQUE_NAME_REPORTER_SELF_TESTER"
        fun schedule(
            context: Context,
            request: OneTimeWorkRequest,
        ) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_UNIQUE_NAME_REPORTER_SELF_TESTER,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
