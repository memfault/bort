package com.memfault.bort

import android.os.ParcelFileDescriptor
import com.memfault.bort.shared.CommandRunnerOptions
import java.io.Closeable
import java.io.FileInputStream

interface CommandRunnerClientFactory {
    fun create(
        mode: CommandRunnerClient.StdErrMode = CommandRunnerClient.StdErrMode.NULL,
        timeoutSeconds: Int = CommandRunnerOptions.DEFAULT_TIMEOUT_SECONDS
    ): CommandRunnerClient
}

class CommandRunnerClient(
    val options: CommandRunnerOptions,
    private val out: FileInputStream,
    private var writeFd: ParcelFileDescriptor?
) : Closeable {
    companion object RealFactory : CommandRunnerClientFactory {
        override fun create(
            mode: StdErrMode,
            timeoutSeconds: Int
        ): CommandRunnerClient {
            val (readFd, writeFd) = ParcelFileDescriptor.createPipe()
            return CommandRunnerClient(
                CommandRunnerOptions(
                    writeFd, mode == StdErrMode.REDIRECT, timeoutSeconds
                ),
                ParcelFileDescriptor.AutoCloseInputStream(readFd),
                writeFd
            )
        }
    }

    override fun close() {
        writeFd?.close()
        out.close()
    }

    /**
     * This deals with a peculiarity of ParcelFileDescriptor.createPipe:
     * if the write end PFD of the pipe is closed before it is sent to the other process,
     * an exception will happen when trying to send it over, while parcelling the PFD.
     * If the PFD is *not* closed explicitly (or references to it still exist), reading
     * from the input stream will block indefinitely!
     */
    fun handOffAndGetInputStream(): FileInputStream =
        out.also {
            writeFd?.close()
            writeFd = null
        }

    enum class StdErrMode {
        NULL,
        REDIRECT,
    }
}
