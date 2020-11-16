package com.memfault.bort.dropbox

import android.os.DropBoxManager
import android.os.ParcelFileDescriptor
import android.system.Os.fstat
import android.system.StructStat
import com.memfault.bort.shared.Logger

val DropBoxManager.Entry.fileDescriptor: ParcelFileDescriptor?
    get() =
        try {
            DropBoxManager.Entry::class.java.getDeclaredField("mFileDescriptor").let {
                it.setAccessible(true)
                it.get(this) as? ParcelFileDescriptor
            }
        } catch (e: Exception) {
            Logger.v("Failed to access fileDescriptor", e)
            null
        }

fun DropBoxManager.Entry.fstat(): StructStat? =
    fileDescriptor?.let {
        try {
            fstat(it.fileDescriptor)
        } catch (e: Exception) {
            Logger.v("fstat failed", e)
            null
        }
    }
