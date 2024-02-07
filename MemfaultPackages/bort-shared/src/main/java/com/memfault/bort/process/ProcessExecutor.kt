package com.memfault.bort.process

import com.memfault.bort.IO
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

class ProcessExecutor
@Inject constructor(
    @IO private val ioDispatcher: CoroutineContext,
) {
    suspend fun <T> execute(
        command: List<String>,
        handleLines: (InputStream) -> T,
    ): T? = withContext(ioDispatcher) {
        Logger.d("ProcessExecutor: executing $command")
        val process = ProcessBuilder(command).start()

        try {
            val output = handleLines(process.inputStream)

            runInterruptible {
                val exitCode = process.waitFor()
                Logger.d("ProcessExecutor: $command exit code $exitCode")
                if (exitCode != 0) return@runInterruptible null
                output
            }
        } catch (e: CancellationException) {
            Logger.d("ProcessExecutor", e)
            process.destroy()
            null
        }
    }
}
