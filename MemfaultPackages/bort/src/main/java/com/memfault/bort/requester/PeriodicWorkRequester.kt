package com.memfault.bort.requester

abstract class PeriodicWorkRequester {
    abstract fun startPeriodic(justBooted: Boolean = false)
    abstract fun cancelPeriodic()
}
