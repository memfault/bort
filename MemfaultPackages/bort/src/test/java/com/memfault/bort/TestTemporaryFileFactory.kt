package com.memfault.bort.test.util

import com.memfault.bort.TemporaryFileFactory
import org.junit.rules.TemporaryFolder
import java.io.File

object TestTemporaryFileFactory : TemporaryFileFactory {
    override val temporaryFileDirectory: File? = null
}

class TemporaryFolderTemporaryFileFactory(
    temporaryFolder: TemporaryFolder,
) : TemporaryFileFactory {
    override val temporaryFileDirectory: File? = temporaryFolder.newFolder()
}
