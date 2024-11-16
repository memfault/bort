package com.memfault.usagereporter

import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

fun ScheduledExecutorService.schedule(command: Runnable, delay: Duration) =
    this.schedule(command, delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)

interface TimeoutRunnable : Runnable {
    fun handleTimeout()
}

class TimeoutThreadPoolExecutor(nThreads: Int) : ThreadPoolExecutor(
    nThreads,
    nThreads,
    0L,
    TimeUnit.MILLISECONDS,
    LinkedBlockingQueue(),
) {
    private val timeoutExecutor = Executors.newSingleThreadScheduledExecutor()

    fun submitWithTimeout(task: TimeoutRunnable, timeout: Duration): Future<*> =
        super.submit(task).also {
            timeoutExecutor.schedule(
                {
                    val mayInterruptIfRunning = false
                    if (it.cancel(mayInterruptIfRunning)) {
                        task.handleTimeout()
                    }
                },
                timeout,
            )
        }

    override fun shutdown() {
        timeoutExecutor.shutdown()
        super.shutdown()
    }

    override fun shutdownNow(): List<Runnable> {
        timeoutExecutor.shutdownNow()
        return super.shutdownNow()
    }
}
