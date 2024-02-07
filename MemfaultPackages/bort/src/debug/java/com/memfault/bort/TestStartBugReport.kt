package com.memfault.bort

import android.content.Context
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.requester.StartBugReport
import com.memfault.bort.requester.StartRealBugReport
import com.memfault.bort.settings.BugReportSettings
import com.memfault.bort.shared.BugReportRequest
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlin.time.Duration

@ContributesBinding(SingletonComponent::class, replaces = [StartRealBugReport::class])
class TestStartBugReport @Inject constructor() : StartBugReport {
    override suspend fun requestBugReport(
        context: Context,
        pendingBugReportRequestAccessor: PendingBugReportRequestAccessor,
        request: BugReportRequest,
        requestTimeout: Duration,
        bugReportSettings: BugReportSettings,
        builtInMetricsStore: BuiltinMetricsStore,
    ): Boolean {
        Logger.i("** MFLT-TEST ** Periodic Bug Report Request")
        return true
    }
}
