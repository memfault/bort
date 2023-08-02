package com.memfault.bort.ota.lib

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@ContributesBinding(SingletonComponent::class, replaces = [RealAndroidUpdateEngine::class])
@Singleton
class TestUpdateEngine @Inject constructor(
    private val stateStore: StateStore,
) : AndroidUpdateEngine {
    var callback: AndroidUpdateEngineCallback? = null

    fun needsReboot() {
        callback?.onStatusUpdate(UPDATE_ENGINE_UPDATED_NEED_REBOOT, 100f)
    }

    override fun bind(callback: AndroidUpdateEngineCallback) {
        this.callback = callback
        Handler(Looper.getMainLooper()).post {
            val state = stateStore.read()
            // Simulates real behavior where if the app dies, we see the last status as an immediate callback upon
            // restarting.
            if (state is State.UpdateDownloading) {
                callback.onStatusUpdate(UPDATE_ENGINE_UPDATED_NEED_REBOOT, 100f)
            } else {
                callback.onStatusUpdate(UPDATE_ENGINE_STATUS_IDLE, 0f)
            }
        }
    }

    override fun applyPayload(url: String, offset: Long, size: Long, metadata: Array<String>) {
        // simulate the happy path of a payload applying, a real device would download, verify and then
        // reach this state
        testLog("applyPayload: url=$url offset=$offset size=$size metadata=${metadata.joinToString()}")
        callback?.onStatusUpdate(UPDATE_ENGINE_STATUS_DOWNLOADING, 50f)
    }
}

fun testLog(msg: String, tag: String = "bort-ota-test") = Log.v(tag, msg)
