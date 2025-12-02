package com.memfault.bort.boot

import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject

fun interface LinuxBootId : () -> String
fun interface LinuxBootIdFileProvider : () -> File

@ContributesBinding(SingletonComponent::class)
class RealLinuxBootIdFileProvider
@Inject constructor() : LinuxBootIdFileProvider {
    override fun invoke(): File = File("/proc/sys/kernel/random/boot_id")
}

@ContributesBinding(SingletonComponent::class)
class LinuxBootIdFileReader
@Inject constructor(
    private val linuxBootIdFileProvider: LinuxBootIdFileProvider,
) : LinuxBootId {
    private val bootId: String by lazy {
        try {
            linuxBootIdFileProvider.invoke().readText().trim()
        } catch (e: FileNotFoundException) {
            Logger.w("LinuxBootId", e)
            "00000000-0000-0000-0000-000000000000"
        }
    }
    override fun invoke(): String = bootId
}
