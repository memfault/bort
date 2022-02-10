package com.memfault.bort.uploader

import androidx.work.CoroutineWorker
import com.memfault.bort.TaskResult
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.metrics.MAX_ATTEMPTS
import com.memfault.bort.shared.Logger

inline fun <W : CoroutineWorker> W.limitAttempts(
    maxAttempts: Int = 3,
    metrics: BuiltinMetricsStore,
    finallyBlock: W.() -> Unit = {},
    block: W.() -> TaskResult
): TaskResult =
    if (runAttemptCount > maxAttempts) {
        Logger.e("Reached max attempts ($maxAttempts) for job $id with tags $tags")
        metrics.increment("${MAX_ATTEMPTS}_$tags")
        finallyBlock()
        TaskResult.FAILURE
    } else {
        block().also {
            if (it != TaskResult.RETRY) {
                finallyBlock()
            }
        }
    }
