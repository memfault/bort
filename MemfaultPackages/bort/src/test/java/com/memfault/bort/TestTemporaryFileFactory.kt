package com.memfault.bort.test.util

import com.memfault.bort.TemporaryFileFactory
import java.io.File

object TestTemporaryFileFactory : TemporaryFileFactory {
    override val temporaryFileDirectory: File? = null
}
