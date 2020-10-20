package com.memfault.bort.shared

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.ParcelUuid
import java.util.*


private const val OUT_FD = "OUT_FD"
private const val REDIRECT_ERR = "REDIR"
private const val TIMEOUT = "TIMEOUT"
private const val ID = "ID"

private val UUID_NULL = UUID.fromString("00000000-0000-0000-0000-000000000000")

data class CommandRunnerOptions(
    val outFd: ParcelFileDescriptor?,
    val redirectErr: Boolean = false,
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    val id: UUID = UUID.randomUUID()
) {
    fun toBundle(): Bundle =
        Bundle().apply {
            outFd?.let { putParcelable(OUT_FD, it) }
            if (redirectErr) { putBoolean(REDIRECT_ERR, true) }
            putInt(TIMEOUT, timeoutSeconds)
            putParcelable(ID, ParcelUuid(id))
        }

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS: Int = 5

        fun fromBundle(bundle: Bundle) = CommandRunnerOptions(
            bundle.getParcelable(OUT_FD),
            bundle.getBoolean(REDIRECT_ERR),
            bundle.getInt(TIMEOUT),
        bundle.getParcelable<ParcelUuid>(ID)?.uuid ?: UUID_NULL
        )
    }
}
