package com.memfault.bort.boot

import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject

fun interface LinuxBootId : () -> String

@ContributesBinding(SingletonComponent::class)
class LinuxBootIdFileReader
@Inject constructor() : LinuxBootId {
    override fun invoke(): String = try {
        File("/proc/sys/kernel/random/boot_id").readText().trim()
    } catch (e: FileNotFoundException) {
        Logger.w("LinuxBootId", e)
        "unknown"
    }
}
