package com.memfault.bort.metrics.statsd

import com.memfault.bort.metrics.statsd.proto.ConfigMetricsReport
import com.memfault.bort.metrics.statsd.proto.ConfigMetricsReportList
import com.memfault.bort.metrics.statsd.proto.StatsdConfig
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import okio.IOException
import javax.inject.Inject

interface StatsdManagerService {
    fun addConfig(key: Long, config: StatsdConfig)
    fun getReports(key: Long): List<ConfigMetricsReport>
}

@ContributesBinding(SingletonComponent::class)
open class RealStatsdManagerService @Inject constructor(
    private val statsdManagerProxy: StatsdManagerProxy,
) : StatsdManagerService {
    override fun addConfig(key: Long, config: StatsdConfig) {
        statsdManagerProxy.addConfig(key, config.encode())
    }

    override fun getReports(key: Long): List<ConfigMetricsReport> {
        return try {
            val encodedReport = statsdManagerProxy.getReports(key) ?: return emptyList()
            ConfigMetricsReportList.ADAPTER.decode(encodedReport).reports
        } catch (ex: IOException) {
            // Decoding failures might happen due to incomplete reads from statsd
            Logger.d("Ignoring non-decodable statsd report", ex)
            emptyList()
        }
    }
}
