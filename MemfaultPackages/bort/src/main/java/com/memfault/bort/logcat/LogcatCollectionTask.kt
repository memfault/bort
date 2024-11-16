package com.memfault.bort.logcat

import androidx.work.Data
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.settings.LogcatCollectionMode.PERIODIC
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.tokenbucket.Logcat
import com.memfault.bort.tokenbucket.TokenBucketStore
import javax.inject.Inject

class LogcatCollectionTask @Inject constructor(
    private val logcatSettings: LogcatSettings,
    private val logcatCollector: PeriodicLogcatCollector,
    @Logcat private val tokenBucketStore: TokenBucketStore,
) : Task<Unit> {
    override fun getMaxAttempts(input: Unit): Int = 1
    override fun convertAndValidateInputData(inputData: Data) = Unit

    override suspend fun doWork(input: Unit): TaskResult {
        if (logcatSettings.collectionMode != PERIODIC) {
            return TaskResult.SUCCESS
        }

        if (!tokenBucketStore.takeSimple(tag = "logcat")) {
            return TaskResult.FAILURE
        }

        logcatCollector.collect()
        return TaskResult.SUCCESS
    }
}
