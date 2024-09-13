package com.memfault.bort.process

import com.memfault.bort.BasicCommandTimeout
import com.memfault.bort.IO
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

class ProcessExecutor
@Inject constructor(
    @IO private val ioDispatcher: CoroutineContext,
    @BasicCommandTimeout private val basicCommandTimeout: Long,
) {
    suspend fun <T> execute(
        command: List<String>,
        handleLines: (InputStream) -> T,
    ): T? = withContext(ioDispatcher) {
        Logger.d("ProcessExecutor: executing $command")
        val process = runCatching {
            ProcessBuilder(command).start()
        }.getOrNull() ?: return@withContext null

        try {
            val output = handleLines(process.inputStream)

            withTimeoutOrNull(basicCommandTimeout) {
                runInterruptible {
                    val exitCode = process.waitFor()
                    Logger.d("ProcessExecutor: $command exit code $exitCode")
                    if (exitCode != 0) return@runInterruptible null
                    output
                }
            }
        } catch (e: CancellationException) {
            Logger.d("ProcessExecutor", e)
            process.destroy()
            null
        }
    }
}
