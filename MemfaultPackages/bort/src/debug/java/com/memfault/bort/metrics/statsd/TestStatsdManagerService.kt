package com.memfault.bort.metrics.statsd

import com.memfault.bort.metrics.statsd.proto.ConfigMetricsReport
import com.memfault.bort.metrics.statsd.proto.StatsdConfig
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@ContributesBinding(SingletonComponent::class, replaces = [RealStatsdManagerService::class])
class TestStatsdManagerService @Inject constructor(
    statsdManagerProxy: StatsdManagerProxy,
) : StatsdManagerService {
    var overrideInjectedReports: () -> List<ConfigMetricsReport> = { emptyList() }

    private val delegate = RealStatsdManagerService(statsdManagerProxy)

    override fun addConfig(key: Long, config: StatsdConfig) {
        delegate.addConfig(key, config)
    }

    override fun getReports(key: Long): List<ConfigMetricsReport> =
        delegate.getReports(key) + overrideInjectedReports()
}
