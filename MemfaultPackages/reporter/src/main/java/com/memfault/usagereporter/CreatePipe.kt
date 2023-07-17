package com.memfault.usagereporter

import android.os.ParcelFileDescriptor

fun interface CreatePipe : () -> Array<ParcelFileDescriptor>
