package com.memfault.bort.metrics.statsd

import android.app.Application
import com.memfault.bort.shared.Logger
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import javax.inject.Inject

class StatsdManagerProxy @Inject constructor(
    application: Application,
) {
    private val statsdManager: Any? by lazy {
        application.getSystemService(STATS_MANAGER_SERVICE)
    }

    private val addConfigMethod: Method? by lazy {
        statsdManager?.javaClass?.getMethod(
            "addConfig",
            Long::class.javaPrimitiveType,
            ByteArray::class.java,
        )
    }
    private val getReportsMethod: Method? by lazy {
        statsdManager?.javaClass?.getMethod("getReports", Long::class.javaPrimitiveType)
    }

    fun addConfig(key: Long, config: ByteArray) {
        try {
            addConfigMethod?.invoke(statsdManager, key, config)
        } catch (e: InvocationTargetException) {
            Logger.e("Unable to call StatsManager#addConfig reflectively", e)
        }
    }

    fun getReports(key: Long): ByteArray? = try {
        getReportsMethod?.invoke(statsdManager, key) as? ByteArray
    } catch (ex: InvocationTargetException) {
        Logger.e("Unable to call StatsManager#getReports reflectively", ex)
        null
    }
}

private const val STATS_MANAGER_SERVICE = "stats"
