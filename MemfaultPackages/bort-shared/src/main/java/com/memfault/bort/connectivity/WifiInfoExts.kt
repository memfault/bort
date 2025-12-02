package com.memfault.bort.connectivity

import android.net.wifi.WifiInfo

private fun WifiInfo.invokeDoubleMethodReflectively(methodName: String): Double? = try {
    val method = this.javaClass.getMethod(methodName)
    method.invoke(this) as? Double
} catch (e: Exception) {
    null
}

private fun WifiInfo.invokeBooleanMethodReflectively(methodName: String): Boolean? = try {
    val method = this.javaClass.getMethod(methodName)
    method.invoke(this) as? Boolean
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

fun WifiInfo.is24GHzReflectively(): Boolean? =
    invokeBooleanMethodReflectively("is24GHz")

fun WifiInfo.is5GHzReflectively(): Boolean? =
    invokeBooleanMethodReflectively("is5GHz")

fun WifiInfo.is6GHzReflectively(): Boolean? =
    invokeBooleanMethodReflectively("is6GHz")
