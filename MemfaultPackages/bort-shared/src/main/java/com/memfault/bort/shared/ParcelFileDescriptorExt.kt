package com.memfault.bort.shared

import android.os.ParcelFileDescriptor

/**
 * Helper that gets the private members of ParcelFileDescriptor.
 * For debugging purposes only!
 */
fun ParcelFileDescriptor.getPrivatesMap() =
    listOf("mFd", "mWrapped", "mClosed", "mGuard", "mCommFd", "mStatus").map { fieldName ->
        val field = ParcelFileDescriptor::class.java.getDeclaredField(fieldName)
        field.setAccessible(true)
        fieldName to field.get(this)
    }.toMap()
