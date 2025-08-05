package com.memfault.bort.shared

import android.content.Intent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val INTENT_ACTION_BUG_REPORT_START = "com.memfault.intent.action.BUG_REPORT_START"

private const val INTENT_EXTRA_BUG_REPORT_MINIMAL_MODE_BOOL = "com.memfault.intent.extra.BUG_REPORT_MINIMAL_MODE_BOOL"
const val INTENT_EXTRA_BUG_REPORT_REQUEST_ID = "com.memfault.intent.extra.BUG_REPORT_REQUEST_ID"
private const val BUG_REPORT_REQUEST_ID_MAXIMUM_LENGTH = 40
private const val INTENT_EXTRA_BUG_REPORT_REQUEST_REPLY_RECEIVER =
    "com.memfault.intent.extra.BUG_REPORT_REQUEST_REPLY_RECEIVER"
private const val INTENT_EXTRA_BUG_REPORT_TRACE_ID = "com.memfault.intent.extra.BUG_REPORT_TRACE_ID"
private const val INTENT_EXTRA_BUG_REPORT_EXTRA_INFO = "com.memfault.intent.extra.BUG_REPORT_EXTRA_INFO"
const val INTENT_EXTRA_BUG_REPORT_REQUEST_TIMEOUT_MS = "com.memfault.intent.extra.BUG_REPORT_REQUEST_TIMEOUT_MS"

@Serializable
data class BugReportOptions(
    val minimal: Boolean = false,
)

@Serializable
data class BugReportRequest(
    val options: BugReportOptions = BugReportOptions(),

    // Note that periodic bug reports always have a null requestId, but explicit bug report requests must have a
    // request_id specified in the request intent.
    @SerialName("request_id")
    val requestId: String? = null,

    @SerialName("reply_receiver")
    val replyReceiver: Component? = null,

    @SerialName("trace_id")
    val traceId: String? = null,

    @SerialName("extra_info")
    val extraInfo: String? = null,
) {
    @Serializable
    data class Component(val pkg: String, val cls: String) {
        override fun toString() = "$pkg/$cls"

        companion object {
            fun fromString(pkgAndCls: String): Component {
                val pattern = Regex("(^[^/]+)/(.+)$")
                val match = pattern.matchEntire(pkgAndCls)
                requireNotNull(match) {
                    "Failed to parse component string '$pkgAndCls'. Expecting 'com.package.id/qualified.class.name'"
                }
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
        traceId?.let { intent.putExtra(INTENT_EXTRA_BUG_REPORT_TRACE_ID, it) }
        extraInfo?.let { intent.putExtra(INTENT_EXTRA_BUG_REPORT_EXTRA_INFO, it) }
    }

    companion object {
        fun fromIntent(intent: Intent) =
            BugReportRequest(
                options = BugReportOptions(
                    minimal = intent.getBooleanExtra(INTENT_EXTRA_BUG_REPORT_MINIMAL_MODE_BOOL, false),
                ),
                requestId = intent.getStringExtra(INTENT_EXTRA_BUG_REPORT_REQUEST_ID)
                    ?.also(::validateRequestId),
                replyReceiver = intent.getStringExtra(INTENT_EXTRA_BUG_REPORT_REQUEST_REPLY_RECEIVER)
                    ?.let(Component::fromString),
                traceId = intent.getStringExtra(INTENT_EXTRA_BUG_REPORT_TRACE_ID),
                extraInfo = intent.getStringExtra(INTENT_EXTRA_BUG_REPORT_EXTRA_INFO),
            )

        private fun validateRequestId(requestId: String) =
            require(requestId.length <= BUG_REPORT_REQUEST_ID_MAXIMUM_LENGTH) {
                "Bug report request ID is longer than $BUG_REPORT_REQUEST_ID_MAXIMUM_LENGTH chars: $requestId"
            }
    }
}
