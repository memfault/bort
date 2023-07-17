package com.memfault.bort.shared

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

private fun isPrimaryUserViaProxy(): Boolean = try {
    val handle = Process.myUserHandle()
    with(Class.forName("android.os.UserHandle")) {
        this.getMethod(
            "isSystem",
        ).invoke(handle) as Boolean
    }
} catch (e: ReflectiveOperationException) {
    Logger.w("Unable to reflectively check if user is the primary user", e)
    true
}

fun isPrimaryUser(): Boolean = isPrimaryUserViaProxy()

fun disableAppComponents(context: Context) = with(context) {
    @Suppress("DEPRECATION") val packageInfo = packageManager.getPackageInfo(
        packageName,
        PackageManager.GET_ACTIVITIES
            or PackageManager.GET_SERVICES
            or PackageManager.GET_PROVIDERS
            or PackageManager.GET_RECEIVERS
    )

    val components = (packageInfo?.activities?.map { it.name } ?: emptyList<String>()) +
        (packageInfo?.services?.map { it.name } ?: emptyList<String>()) +
        (packageInfo?.providers?.map { it.name } ?: emptyList<String>()) +
        (packageInfo?.receivers?.map { it.name } ?: emptyList<String>())

    components.forEach { componentName ->
        packageManager.setComponentEnabledSetting(
            ComponentName(packageName, componentName),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
