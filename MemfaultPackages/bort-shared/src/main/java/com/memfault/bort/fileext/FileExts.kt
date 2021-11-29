package com.memfault.bort.fileExt

import java.io.File

fun File.deleteSilently(): Boolean =
    try { delete() } catch (e: Exception) { false }
