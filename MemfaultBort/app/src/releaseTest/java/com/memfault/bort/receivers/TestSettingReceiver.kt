package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import com.memfault.bort.*

class TestSettingReceiver: SingleActionReceiver(
    "com.memfault.intent.action.TEST_SETTING_SET"
) {

    override fun onIntentReceived(context: Context, intent: Intent) {
        when {
            intent.hasExtra("project_key") -> {
                val projectKey = intent.getStringExtra(
                        "project_key"
                )
                Logger.test("project_key: $projectKey")
                projectKey ?: return
                PersistentProjectKeyProvider(
                    PreferenceManager.getDefaultSharedPreferences(context)
                ).also {
                    Logger.test("Key was: ${it.getValue()}")
                    it.setValue(projectKey)
                    Logger.test("Updated to key: ${it.getValue()}")
                }
            }
        }
    }
}
