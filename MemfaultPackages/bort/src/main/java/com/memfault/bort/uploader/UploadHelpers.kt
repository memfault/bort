package com.memfault.bort.uploader

import androidx.work.CoroutineWorker
import com.memfault.bort.shared.Logger
import com.memfault.bort.TaskResult

inline fun <W : CoroutineWorker> W.limitAttempts(
    maxAttempts: Int = 3,
    finallyBlock: W.() -> Unit = {},
    block: W.() -> TaskResult
): TaskResult =
    if (runAttemptCount > maxAttempts) {
        Logger.e("Reached max attempts ($maxAttempts) for job $id with tags $tags")
        finallyBlock()
        TaskResult.FAILURE
    } else {
        block().also {
            if (it != TaskResult.RETRY) {
                finallyBlock()
            }
        }
    }
