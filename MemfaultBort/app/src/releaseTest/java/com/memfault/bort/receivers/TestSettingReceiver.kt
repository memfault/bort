package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.Bort
import com.memfault.bort.ComponentsBuilder
import com.memfault.bort.Logger

class TestSettingReceiver: SingleActionBroadcastReceiver(
    "com.memfault.intent.action.TEST_SETTING_SET"
) {

    override fun onIntentReceived(context: Context, intent: Intent) {
        when {
            intent.hasExtra("project_key") -> {
                val projectKey = intent.getStringExtra(
                        "project_key"
                )
                Logger.d("project_key: $projectKey")
                ComponentsBuilder.updatedProjectKey = projectKey
            }
        }

        Logger.d("Updating app components")
        Bort.updateComponents(ComponentsBuilder())
    }
}
