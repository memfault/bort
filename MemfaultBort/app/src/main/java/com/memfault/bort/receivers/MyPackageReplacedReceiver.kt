package com.memfault.bort.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.memfault.bort.*
import com.memfault.bort.requester.BugReportRequester

class MyPackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return
        when {
            isBuildTypeBlacklisted() -> return
            intent.action != INTENT_ACTION_MY_PACKAGE_REPLACED -> return
        }
        Logger.v("Requesting periodic bug report from MyPackageReplacedReceiver")
        BugReportRequester(
            context
        ).requestPeriodic(
            Bort.serviceLocator().settingsProvider().bugReportRequestIntervalHours()
        )
    }
}
