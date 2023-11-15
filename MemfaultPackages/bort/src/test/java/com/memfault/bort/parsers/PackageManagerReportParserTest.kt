package com.memfault.bort.parsers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PackageManagerReportParserTest {
    @Test
    fun realFixture() {
        val report = PackageManagerReportParser(PACKAGE_MANAGER_REPORT.byteInputStream()).parse()
        assertEquals(
            listOf(
                Package(
                    id = "com.memfault.usagereporter",
                    userId = 1000,
                    codePath = "/data/app/com.memfault.usagereporter-NxicUQwHlkqAfOu61ATYIQ==",
                    versionCode = 2070100,
                    versionName = "2.7.1+0-DEV-144c8b06b",
                ),
            ),
            report.packages,
        )
    }

    @Test
    fun multiplePackages() {
        val fixture =
            """Packages:
              |  Package [com.a] (4c93b16):
              |  Package [com.b] (4c93b16):
            """.trimMargin()
        val report = PackageManagerReportParser(fixture.byteInputStream()).parse()
        assertEquals(
            listOf(
                Package(id = "com.a"),
                Package(id = "com.b"),
            ),
            report.packages,
        )
    }
}

// Output of `dumpsys package com.memfault.usagereporter` (pared down some of the permissions lists)
private val PACKAGE_MANAGER_REPORT =
    """Receiver Resolver Table:
      |  Non-Data Actions:
      |      com.memfault.intent.action.BUG_REPORT_START:
      |        dcb0cb5 com.memfault.usagereporter/.BugReportStartReceiver filter 742301f
      |          Action: "com.memfault.intent.action.BUG_REPORT_START"
      |
      |Permissions:
      |  Permission [com.memfault.usagereporter.permission.REPORTER_ACCESS] (852f61a):
      |    sourcePackage=com.memfault.usagereporter
      |    uid=1000 gids=null type=0 prot=signature|privileged
      |    perm=Permission{651bf4a com.memfault.usagereporter.permission.REPORTER_ACCESS}
      |    packageSetting=PackageSetting{4c93b16 com.memfault.usagereporter/1000}
      |
      |Key Set Manager:
      |  [com.memfault.usagereporter]
      |      Signing KeySets: 1
      |
      |Packages:
      |  Package [com.memfault.usagereporter] (4c93b16):
      |    userId=1000
      |    sharedUser=SharedUserSetting{bea64b android.uid.system/1000}
      |    pkg=Package{cafe8c0 com.memfault.usagereporter}
      |    codePath=/data/app/com.memfault.usagereporter-NxicUQwHlkqAfOu61ATYIQ==
      |    resourcePath=/data/app/com.memfault.usagereporter-NxicUQwHlkqAfOu61ATYIQ==
      |    legacyNativeLibraryDir=/data/app/com.memfault.usagereporter-NxicUQwHlkqAfOu61ATYIQ==/lib
      |    primaryCpuAbi=null
      |    secondaryCpuAbi=null
      |    versionCode=2070100 minSdk=26 targetSdk=26
      |    versionName=2.7.1+0-DEV-144c8b06b
      |    splits=[base]
      |    apkSigningVersion=3
      |    applicationInfo=ApplicationInfo{450c9f9 com.memfault.usagereporter}
      |    flags=[ SYSTEM HAS_CODE ALLOW_CLEAR_USER_DATA UPDATED_SYSTEM_APP ]
      |    privateFlags=[ PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE_VIA_SDK_VERSION PRIVILEGED ]
      |    dataDir=/data/user/0/com.memfault.usagereporter
      |    supportsScreens=[small, medium, large, xlarge, resizeable, anyDensity]
      |    usesLibraries:
      |      org.apache.http.legacy
      |    usesLibraryFiles:
      |      /system/framework/org.apache.http.legacy.boot.jar
      |    timeStamp=2020-11-12 11:36:33
      |    firstInstallTime=2020-11-12 06:22:29
      |    lastUpdateTime=2020-11-12 11:36:33
      |    signatures=PackageSignatures{25dfa07 version:3, signatures:[b4addb29], past signatures:[]}
      |    installPermissionsFixed=true
      |    pkgFlags=[ SYSTEM HAS_CODE ALLOW_CLEAR_USER_DATA UPDATED_SYSTEM_APP ]
      |    declared permissions:
      |      com.memfault.usagereporter.permission.REPORTER_ACCESS: prot=signature|privileged, INSTALLED
      |    requested permissions:
      |      android.permission.READ_LOGS
      |      android.permission.PACKAGE_USAGE_STATS
      |      android.permission.INTERACT_ACROSS_USERS
      |    install permissions:
      |      android.permission.BIND_INCALL_SERVICE: granted=true
      |      android.permission.WRITE_SETTINGS: granted=true
      |      android.permission.CONFIGURE_WIFI_DISPLAY: granted=true
      |      android.permission.CONFIGURE_DISPLAY_COLOR_MODE: granted=true
      |      android.permission.ACCESS_WIMAX_STATE: granted=true
      |    User 0: ceDataInode=-4294835843 installed=true hidden=false suspended=false stopped=false notLaunched=false enabled=0 instant=false virtual=false
      |
      |Hidden system packages:
      |  Package [com.memfault.usagereporter] (35999cf):
      |    userId=1000
      |    sharedUser=SharedUserSetting{bea64b android.uid.system/1000}
      |    pkg=Package{f549c21 com.memfault.usagereporter}
      |    codePath=/system/priv-app/MemfaultUsageReporter
      |    resourcePath=/system/priv-app/MemfaultUsageReporter
      |    legacyNativeLibraryDir=/system/priv-app/MemfaultUsageReporter/lib
      |    primaryCpuAbi=x86
      |    secondaryCpuAbi=null
      |    versionCode=2070100 minSdk=26 targetSdk=26
      |    versionName=2.7.1+0-DEV-66d7c5a38
      |    splits=[base]
      |    apkSigningVersion=3
      |    applicationInfo=ApplicationInfo{11eb846 com.memfault.usagereporter}
      |    flags=[ SYSTEM HAS_CODE ALLOW_CLEAR_USER_DATA UPDATED_SYSTEM_APP ]
      |    privateFlags=[ PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE_VIA_SDK_VERSION PRIVILEGED ]
      |    dataDir=/data/user/0/com.memfault.usagereporter
      |    supportsScreens=[small, medium, large, xlarge, resizeable, anyDensity]
      |    usesLibraries:
      |      org.apache.http.legacy
      |    usesLibraryFiles:
      |      /system/framework/org.apache.http.legacy.boot.jar
      |    timeStamp=2020-11-12 06:22:29
      |    firstInstallTime=2020-11-12 06:22:29
      |    lastUpdateTime=2020-11-12 06:22:29
      |    signatures=PackageSignatures{25dfa07 version:3, signatures:[b4addb29], past signatures:[]}
      |    installPermissionsFixed=true
      |    pkgFlags=[ SYSTEM HAS_CODE ALLOW_CLEAR_USER_DATA ]
      |    declared permissions:
      |      com.memfault.usagereporter.permission.REPORTER_ACCESS: prot=signature|privileged, INSTALLED
      |    requested permissions:
      |      android.permission.READ_LOGS
      |      android.permission.PACKAGE_USAGE_STATS
      |      android.permission.INTERACT_ACROSS_USERS
      |    install permissions:
      |      android.permission.BIND_INCALL_SERVICE: granted=true
      |      android.permission.WRITE_SETTINGS: granted=true
      |      android.permission.CONFIGURE_WIFI_DISPLAY: granted=true
      |      android.permission.CONFIGURE_DISPLAY_COLOR_MODE: granted=true
      |      android.permission.ACCESS_WIMAX_STATE: granted=true
      |      android.permission.USE_CREDENTIALS: granted=true
      |      android.permission.MODIFY_AUDIO_SETTINGS: granted=true
      |    User 0: ceDataInode=-4294835843 installed=true hidden=false suspended=false stopped=false notLaunched=false enabled=0 instant=false virtual=false
      |
      |Shared users:
      |  SharedUser [android.uid.system] (bea64b):
      |    userId=1000
      |    install permissions:
      |      android.permission.BIND_INCALL_SERVICE: granted=true
      |      android.permission.WRITE_SETTINGS: granted=true
      |      android.permission.CONFIGURE_WIFI_DISPLAY: granted=true
      |    User 0:
      |      gids=[1065, 3002, 1023, 3003, 3001, 1007]
      |      runtime permissions:
      |        android.permission.READ_CALL_LOG: granted=true, flags=[ SYSTEM_FIXED GRANTED_BY_DEFAULT ]
      |        android.permission.ACCESS_FINE_LOCATION: granted=true, flags=[ SYSTEM_FIXED GRANTED_BY_DEFAULT ]
      |        android.permission.READ_EXTERNAL_STORAGE: granted=true, flags=[ SYSTEM_FIXED GRANTED_BY_DEFAULT ]
      |        android.permission.ACCESS_COARSE_LOCATION: granted=true, flags=[ SYSTEM_FIXED GRANTED_BY_DEFAULT ]
      |
      |Package Changes:
      |  Sequence number=17
      |  User 0:
      |    seq=10, package=com.memfault.usagereporter
      |    seq=16, package=com.memfault.smartfridge.bort
      |
      |
      |Dexopt state:
      |  [com.memfault.usagereporter]
      |    path: /data/app/com.memfault.usagereporter-NxicUQwHlkqAfOu61ATYIQ==/base.apk
      |      x86: [status=speed-profile] [reason=install]
      |
      |
      |Compiler stats:
      |  [com.memfault.usagereporter]
      |     base.apk - 694
      |
    """.trimMargin()
