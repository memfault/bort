package com.memfault.bort.shared

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.ParcelUuid
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.milliseconds
import kotlin.time.seconds

private const val OUT_FD = "OUT_FD"
private const val REDIRECT_ERR = "REDIR"
private const val TIMEOUT_MILLIS = "TIMEOUT_MILLIS"
private const val ID = "ID"

private val UUID_NULL = UUID.fromString("00000000-0000-0000-0000-000000000000")

data class CommandRunnerOptions(
    val outFd: ParcelFileDescriptor?,
    val redirectErr: Boolean = false,
    val timeout: Duration = DEFAULT_TIMEOUT,
    val id: UUID = UUID.randomUUID()
) {
    fun toBundle(): Bundle =
        Bundle().apply {
            outFd?.let { putParcelable(OUT_FD, it) }
            if (redirectErr) { putBoolean(REDIRECT_ERR, true) }
            putLong(TIMEOUT_MILLIS, timeout.toLongMilliseconds())
            putParcelable(ID, ParcelUuid(id))
        }

    companion object {
        val DEFAULT_TIMEOUT = 5.seconds

        fun fromBundle(bundle: Bundle) = CommandRunnerOptions(
            bundle.getParcelable(OUT_FD),
            bundle.getBoolean(REDIRECT_ERR),
            bundle.getLong(TIMEOUT_MILLIS).milliseconds,
            bundle.getParcelable<ParcelUuid>(ID)?.uuid ?: UUID_NULL
        )
    }
}
