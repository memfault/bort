package com.memfault.bort.bugreport

/**
 * Plug-in mechanism to control whether a bugreport should run or not. In cases where running a bugreport
 * during another CPU-heavy operation is not desirable because it would cause the device to stutter or lag, you can
 * implement a constraint via a Dagger Multibinding that'll be called before a bugreport is started.
 *
 * If any constraint is not met, `ERROR_CONSTRAINTS_NOT_SATISFIED` will be returned as the status of the request.
 *
 * ex:
 *
 * @ContributesMultibinding(SingletonComponent::class)
 * class DeviceIdleStartBugReportConstraint
 * @Inject constructor(
 *   private val deviceStatusApi: DeviceStatusApi,
 * ): StartBugReportConstraint {
 *
 *   override suspend fun ok(): Boolean {
 *     return deviceStatusApi.status == DeviceStatus.IDLE
 *   }
 * }
 */
interface StartBugReportConstraint {
    suspend fun ok(): Boolean
}
