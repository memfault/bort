package com.memfault.bort.metrics

import com.memfault.bort.customevent.Reporting
import com.memfault.bort.shared.Logger

private const val HEARTBEAT_REPORT_TYPE = "Heartbeat"

/**
 * Finishes a heartbeat report via reflection until starting/finishing reports becomes part of the public API
 */
fun Reporting.finishHeartbeat(startNextReport: Boolean = false): Boolean = try {
    val finishReport = Reporting::class.java.getDeclaredMethod(
        "finishReport",
        String::class.java,
        Long::class.java,
        Boolean::class.java,
    )
    finishReport.isAccessible = true
    finishReport.invoke(null, HEARTBEAT_REPORT_TYPE, System.currentTimeMillis(), startNextReport) as Boolean
} catch (ex: Exception) {
    Logger.w("Failed to finish heartbeat report", ex)
    false
}
