package com.memfault.bort.parsers

import com.memfault.bort.AndroidPackage
import com.memfault.bort.shared.PackageManagerCommand.Util.isValidAndroidApplicationId
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull

data class Package(
    val id: String,
    val userId: Int? = null,
    val versionCode: Long? = null,
    val versionName: String? = null,
) {
    fun toUploaderPackage(): AndroidPackage? {
        userId ?: return null
        versionCode ?: return null
        versionName ?: return null
        return AndroidPackage(
            id = id,
            versionCode = versionCode,
            versionName = versionName,
            userId = userId,
        )
    }
}

data class PackageManagerReport(val packages: List<Package> = emptyList()) {
    suspend fun findPackagesByProcessName(processName: String): Package? =
        appIdGuessesFromProcessName(processName).asFlow()
            .mapNotNull { appGuess -> packages.find { it.id == appGuess } }
            .firstOrNull()

    fun findByPackage(packageName: String): Package? = packages.firstOrNull { it.id == packageName }

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
            if (!processName.isValidAndroidApplicationId()) {
                emptySequence()
            } else {
                generateSequence(processName) {
                    if (it.count { c -> c == '.' } <= 1) {
                        null
                    } else {
                        it.substringBeforeLast('.')
                    }
                }
            }
    }
}
