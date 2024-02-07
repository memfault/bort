package com.memfault.bort.dropbox

import android.content.ContentResolver
import android.os.Build
import android.provider.Settings
import com.memfault.bort.settings.DropBoxForceEnableWtfTags
import com.memfault.bort.shared.Logger
import javax.inject.Inject

/**
 * Enables dropbox tags at the OS level, if they are disabled.
 *
 * Only applies from Android 14+, where some dropbox tags are disabled by default.
 *
 * See https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/DropBoxManagerService.java#110
 */
class DropBoxTagEnabler @Inject constructor(
    private val contentResolver: ContentResolver,
    private val dropBoxForceEnableWtfTags: DropBoxForceEnableWtfTags,
) {
    fun enableTagsIfRequired() {
        if (!dropBoxForceEnableWtfTags()) {
            return
        }
        // Only needed on Android 14+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return
        }
        enableTagIfRequired("data_app_wtf")
        enableTagIfRequired("system_app_wtf")
        enableTagIfRequired("system_server_wtf")
    }

    private fun enableTagIfRequired(tag: String) {
        try {
            val key = "$DROPBOX_TAG_PREFIX$tag"
            val enabled = (Settings.Global.getString(contentResolver, key) ?: "") == ENABLED
            if (!enabled) {
                Logger.i("DropBoxTagEnabler Enabling dropbox tag: $tag")
                Settings.Global.putString(contentResolver, key, ENABLED)
            }
        } catch (e: Exception) {
            Logger.w("Failed to check/enable dropbox tag: $tag", e)
        }
    }

    companion object {
        private const val DROPBOX_TAG_PREFIX = "dropbox:"
        private const val ENABLED = "enabled"
    }
}
