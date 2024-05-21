package com.memfault.bort.zip

import com.memfault.bort.shared.Logger
import java.io.File
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipFile

fun openZipFile(maybeZipFile: File): ZipFile? = try {
    ZipFile(maybeZipFile)
} catch (e: ZipException) {
    Logger.e("Unable to open ZipFile $maybeZipFile", e)
    null
} catch (e: IOException) {
    Logger.e("Unable to open ZipFile $maybeZipFile", e)
    null
}
