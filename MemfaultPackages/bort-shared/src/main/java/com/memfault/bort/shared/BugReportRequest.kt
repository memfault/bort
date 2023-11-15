package com.memfault.bort.shared

import android.content.Intent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BugReportOptions(
    val minimal: Boolean = false,
)

@Serializable
data class BugReportRequest(
    val options: BugReportOptions = BugReportOptions(),

    // NOTE: periodic bug report requests have a null requestId
    @SerialName("request_id")
    val requestId: String? = null,

    @SerialName("reply_receiver")
    val replyReceiver: Component? = null,
) {
    @Serializable
    data class Component(val pkg: String, val cls: String) {
        override fun toString() = "$pkg/$cls"

        companion object {
            fun fromString(pkgAndCls: String): Component {
                val pattern = Regex("(^[^/]+)/(.+)$")
                val match = pattern.matchEntire(pkgAndCls) ?: throw IllegalArgumentException(
                    "Failed to parse component string '$pkgAndCls'. Expecting 'com.package.id/qualified.class.name'",
                )
                val (pkg, cls) = match.destructured
                return Component(pkg, cls)
            }
        }
    }

    fun applyToIntent(intent: Intent) {
        intent.putExtra(INTENT_EXTRA_BUG_REPORT_MINIMAL_MODE_BOOL, options.minimal)
        requestId?.let {
            intent.putExtra(INTENT_EXTRA_BUG_REPORT_REQUEST_ID, requestId)
        }
        replyReceiver?.let {
            intent.putExtra(INTENT_EXTRA_BUG_REPORT_REQUEST_REPLY_RECEIVER, replyReceiver.toString())
        }
    }

    companion object {
        fun fromIntent(intent: Intent) =
            BugReportRequest(
                options = BugReportOptions(intent.getBooleanExtra(INTENT_EXTRA_BUG_REPORT_MINIMAL_MODE_BOOL, false)),
                requestId = intent.getStringExtra(INTENT_EXTRA_BUG_REPORT_REQUEST_ID)?.let(::validateRequestId),
                replyReceiver = intent.getStringExtra(INTENT_EXTRA_BUG_REPORT_REQUEST_REPLY_RECEIVER)?.let(
                    Component::fromString,
                ),
            )

        private fun validateRequestId(requestId: String): String =
            requestId.also {
                if (it.length > BUG_REPORT_REQUEST_ID_MAXIMUM_LENGTH) {
                    throw IllegalArgumentException(
                        "Bug report request ID is longer than $BUG_REPORT_REQUEST_ID_MAXIMUM_LENGTH chars: $it",
                    )
                }
            }
    }
}
