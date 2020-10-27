package com.memfault.bort.shared

import android.content.Intent

private const val INTENT_EXTRA_BUG_REPORT_MINIMAL_MODE_BOOL = "com.memfault.intent.extra.BUG_REPORT_MINIMAL_MODE_BOOL"

data class BugReportOptions(val minimal: Boolean = false) {

    fun applyToIntent(intent: Intent) {
        intent.putExtra(INTENT_EXTRA_BUG_REPORT_MINIMAL_MODE_BOOL, minimal)
    }

    companion object {
        fun fromIntent(intent: Intent) =
            BugReportOptions(
                minimal = intent.getBooleanExtra(INTENT_EXTRA_BUG_REPORT_MINIMAL_MODE_BOOL, false)
            )
    }
}
