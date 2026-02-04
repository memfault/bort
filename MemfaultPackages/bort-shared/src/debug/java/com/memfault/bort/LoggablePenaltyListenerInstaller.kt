package com.memfault.bort

import android.os.Build
import android.os.StrictMode
import androidx.annotation.RequiresApi
import com.memfault.bort.shared.Logger
import java.util.concurrent.Executors

object LoggablePenaltyListenerInstaller {
    @RequiresApi(Build.VERSION_CODES.P)
    fun installInThreadPolicy(builder: StrictMode.ThreadPolicy.Builder) {
        builder.penaltyListener(Executors.newSingleThreadExecutor()) {
            Logger.test("Bort StrictMode violation!!")
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun installInVmPolicy(builder: StrictMode.VmPolicy.Builder) {
        builder.penaltyListener(Executors.newSingleThreadExecutor()) {
            Logger.test("Bort StrictMode violation!!")
        }
    }
}
