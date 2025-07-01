package com.memfault.bort.connectivity

import android.net.wifi.WifiInfo

private fun WifiInfo.invokeDoubleMethodReflectively(methodName: String): Double? = try {
    val method = this.javaClass.getMethod(methodName)
    method.invoke(this) as? Double
} catch (e: Exception) {
    null
}

fun WifiInfo.getLostTxPacketsPerSecondReflectively(): Double? =
    invokeDoubleMethodReflectively("getLostTxPacketsPerSecond")

fun WifiInfo.getRetriedTxPacketsPerSecondReflectively(): Double? =
    invokeDoubleMethodReflectively("getRetriedTxPacketsPerSecond")

fun WifiInfo.getSuccessfulTxPacketsPerSecondReflectively(): Double? =
    invokeDoubleMethodReflectively("getSuccessfulTxPacketsPerSecond")

fun WifiInfo.getSuccessfulRxPacketsPerSecondReflectively(): Double? =
    invokeDoubleMethodReflectively("getSuccessfulRxPacketsPerSecond")
