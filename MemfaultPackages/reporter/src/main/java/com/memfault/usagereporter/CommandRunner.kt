package com.memfault.usagereporter

import android.os.ParcelFileDescriptor
import com.memfault.bort.shared.CommandRunnerOptions
import com.memfault.bort.shared.Logger
import java.io.Closeable
import java.io.OutputStream
import java.util.concurrent.TimeUnit

private const val EXIT_TIMEOUT_SECS: Long = 5

typealias CommandRunnerReportResult = (exitCode: Int?, didTimeout: Boolean) -> Unit

class CommandRunner(
    val command: List<String>,
    val options: CommandRunnerOptions,
    val reportResult: CommandRunnerReportResult,
    val outputStreamFactory: (ParcelFileDescriptor) -> OutputStream = ParcelFileDescriptor::AutoCloseOutputStream
) : TimeoutRunnable {
    var process: Process? = null
        private set
    var didTimeout: Boolean = false
        private set
    var exitCode: Int? = null
        protected set

    override fun toString() =
        "`${command.joinToString(" ")}` (id=${options.id})"

    override fun run() {
        try {
            executeCommand()
        } finally {
            reportResult(exitCode, didTimeout)
        }
    }

    private fun executeCommand() {
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
                        // Skip copy in the unlikely case where the process got created just after handleTimeout() ran:
                        if (!didTimeout) {
                            it.copyTo(outputStream)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("Exception: $this", e)
        }
    }

    override fun handleTimeout() {
        didTimeout = true
        process?.destroy()
    }

    private inner class ProcessResource(val process: Process) : Closeable {
        override fun close() {
            process.destroy()
            val exited = try {
                process.waitFor(EXIT_TIMEOUT_SECS, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                false
            }
            exitCode = try {
                process.exitValue()
            } catch (e: IllegalThreadStateException) {
                null
            }
            Logger.v("Done: $this, exited=$exited, exitCode=$exitCode, didTimeout=$didTimeout")
        }
    }
}
