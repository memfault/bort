package com.memfault.bort.parsers

import android.os.Process
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
    private val uidMapper: Map<Int, List<Package>> by lazy {
        packages.groupBy { it.userId ?: -1 }
    }

    suspend fun findPackagesByProcessName(processName: String): Package? =
        appIdGuessesFromProcessName(processName).asFlow()
            .mapNotNull { appGuess -> packages.find { it.id == appGuess } }
            .firstOrNull()

    fun findByPackage(packageName: String): Package? = packages.firstOrNull { it.id == packageName }

    fun findByUid(uid: Int): List<Package> = uidMapper[uid] ?: emptyList()

    companion object {
        /**
         * https://cs.android.com/android/platform/superproject/main/+/main:system/core/libcutils/include/private/android_filesystem_config.h
         *
         * Map of Process UIDs to a named component. This allows us to more consistently refer to the same service
         * even if that UID might map to multiple components.
         *
         * The recommendation is to never change process name.
         */
        enum class ProcessUid(val processName: String, val uid: Int) {
            PROCESS_SYSTEM("system", Process.SYSTEM_UID),
            PROCESS_PHONE("com.android.phone", Process.PHONE_UID),
            PROCESS_SHELL("com.android.shell", 2000),
            PROCESS_LOG("android.uid.log", 1007),
            PROCESS_WIFI("android.uid.wifi", 1010),
            PROCESS_MEDIA("UID_MEDIA", 1013),
            PROCESS_NFC("com.android.nfc", 1027),
            PROCESS_SE("com.android.se", 1068),
            PROCESS_NETWORKSTACK("com.android.networkstack", 1073),
            PROCESS_UWB("android.uid.uwb", 1083),
            PROCESS_BLUETOOTH("com.android.bluetooth", 1002),
            PROCESS_AUDIOSERVER("UID_AUDIOSERVER", 1041),
            PROCESS_CAMERASERVER("UID_CAMERASERVER", 1047),
            PROCESS_DNS_TETHER("UID_DNS_TETHER", 1052),
        }

        val PROCESS_UID_COMPONENT_MAP = ProcessUid.entries.associate { p -> p.uid to p.processName }

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
