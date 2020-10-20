package com.memfault.usagereporter

import android.os.ParcelFileDescriptor
import com.memfault.bort.shared.Command
import com.memfault.bort.shared.CommandRunnerOptions
import com.memfault.bort.shared.Logger
import java.io.Closeable
import java.io.OutputStream
import java.util.concurrent.TimeUnit

private const val EXIT_TIMEOUT_SECS: Long = 5

class CommandRunner(
    val command: List<String>,
    val options: CommandRunnerOptions,
    val outputStreamFactory: (ParcelFileDescriptor) -> OutputStream = ParcelFileDescriptor::AutoCloseOutputStream
) : Runnable {
    var process: Process? = null
        private set

    override fun toString() =
        "`${command.joinToString(" ")}` (id=${options.id})"

    override fun run() {
        val outFd = options.outFd
        if (outFd == null) {
            Logger.e("Failed to run command $this. outFd was null!")
            return
        }
        Logger.v("Running: $this")
        try {
            outputStreamFactory(outFd).use { outputStream ->
                val p = ProcessBuilder()
                    .command(command)
                    .redirectErrorStream(options.redirectErr)
                    .start()

                process = p

                ProcessResource(p).use {
                    p.inputStream.use {
                        it.copyTo(outputStream)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("Exception: $this", e)
        }
    }

    private inner class ProcessResource(val process: Process) : Closeable {
        override fun close() {
            process.destroy()
            val exited = try {
                process.waitFor(EXIT_TIMEOUT_SECS, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                false
            }
            val exitValue = try {
                process.exitValue()
            } catch (e: IllegalThreadStateException) {
                null
            }
            Logger.v("Done: ${this}, exited=$exited, exitValue=$exitValue")
        }
    }
}
