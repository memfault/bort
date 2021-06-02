package com.memfault.bort

import android.os.RemoteException
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import com.github.michaelbull.result.toErrorIf
import com.memfault.bort.parsers.Package
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.parsers.PackageManagerReportParser
import com.memfault.bort.settings.ConfigValue
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PackageManagerCommand
import com.memfault.bort.shared.PackageManagerCommand.Util.isValidAndroidApplicationId
import kotlin.time.Duration
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull

class PackageManagerClient(
    private val reporterServiceConnector: ReporterServiceConnector,
    private val commandTimeoutConfig: ConfigValue<Duration>,
) {
    suspend fun findPackagesByProcessName(processName: String): Package? =
        appIdGuessesFromProcessName(processName).asFlow().mapNotNull(::findPackageByApplicationId).firstOrNull()

    suspend fun findPackageByApplicationId(appId: String): Package? =
        getPackageManagerReport(appId).packages.firstOrNull()

    suspend fun getPackageManagerReport(appId: String? = null): PackageManagerReport {
        if (appId != null && !appId.isValidAndroidApplicationId()) return PackageManagerReport()
        val cmdOrAppId = appId ?: PackageManagerCommand.CMD_PACKAGES

        return try {
            reporterServiceConnector.connect { getClient ->
                getClient().packageManagerRun(
                    cmd = PackageManagerCommand(cmdOrAppId = cmdOrAppId),
                    timeout = commandTimeoutConfig()
                ) { invocation ->
                    invocation.awaitInputStream().andThen {
                        runCatching {
                            PackageManagerReportParser(it).parse()
                        }
                    }.andThen { packages ->
                        invocation.awaitResponse(commandTimeoutConfig()).toErrorIf({ it.exitCode != 0 }) {
                            Exception("Remote error while running dumpsys package! result=$it")
                        }.map { packages }
                    }
                }
            }.onFailure {
                Logger.e("Error getting package manager report", it)
            }.getOr(PackageManagerReport())
        } catch (e: RemoteException) {
            Logger.w("Unable to connect to ReporterService to get package manager report")
            PackageManagerReport()
        }
    }

    companion object Util {
        /*
         * The approach here isn't correct but works for some common apps:
         *
         *  * com.google.android.gms.persistent (package: com.google.android.gms)
         *
         * The "android:process" manifest field can hold a name that doesn't share
         * any resemblance with the APK package name.
         *
         * https://developer.android.com/guide/topics/manifest/application-element.html
         */
        fun appIdGuessesFromProcessName(processName: String): Sequence<String> =
            if (!processName.isValidAndroidApplicationId()) emptySequence()
            else generateSequence(processName) {
                if (it.count { c -> c == '.' } <= 1) null
                else it.substringBeforeLast('.')
            }
    }
}
