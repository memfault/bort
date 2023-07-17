package com.memfault.bort.ota.lib

import android.util.Log
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@ContributesBinding(SingletonComponent::class, replaces = [RealAndroidUpdateEngine::class])
@Singleton
class TestUpdateEngine @Inject constructor() : AndroidUpdateEngine {
    var callback: AndroidUpdateEngineCallback? = null

    override fun bind(callback: AndroidUpdateEngineCallback) {
        this.callback = callback
        callback.onStatusUpdate(UPDATE_ENGINE_STATUS_IDLE, 0f)
    }

    override fun applyPayload(url: String, offset: Long, size: Long, metadata: Array<String>) {
        // simulate the happy path of a payload applying, a real device would download, verify and then
        // reach this state
        testLog("applyPayload: url=$url offset=$offset size=$size metadata=${metadata.joinToString()}")
        callback?.onStatusUpdate(UPDATE_ENGINE_UPDATED_NEED_REBOOT, 100f)
    }
}

fun testLog(msg: String, tag: String = "bort-ota-test") = Log.v(tag, msg)
