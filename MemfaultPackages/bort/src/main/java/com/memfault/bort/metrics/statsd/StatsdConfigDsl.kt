package com.memfault.bort.metrics.statsd

import com.memfault.bort.metrics.statsd.proto.AtomMatcher
import com.memfault.bort.metrics.statsd.proto.AtomMatcher.Combination
import com.memfault.bort.metrics.statsd.proto.EventMetric
import com.memfault.bort.metrics.statsd.proto.LogicalOperation
import com.memfault.bort.metrics.statsd.proto.SimpleAtomMatcher
import com.memfault.bort.metrics.statsd.proto.StatsdConfig
import com.memfault.bort.metrics.statsd.proto.StatsdConfig.StatsdConfigOptions
import java.util.concurrent.atomic.AtomicLong

fun statsdConfig(id: Long, init: StatsdConfigBuilder.() -> Unit): StatsdConfig =
    StatsdConfigBuilder(id).apply(init).build()

class StatsdConfigBuilder internal constructor(private val id: Long) {
    private val idGenerator = AtomicLong(INITIAL_ID)
    private val allowedLogSources = mutableSetOf<String>()
    internal val whitelistedAtomIds = mutableSetOf<Int>()
    private var whitelistAllAtomIds = false
    internal val atomMatchers = mutableListOf<AtomMatcher>()
    private val eventMetrics = mutableListOf<EventMetric>()

    fun allowFromSystem() {
        allowedLogSources.add("AID_SYSTEM")
    }

    fun allowFromLowMemoryKiller() {
        allowedLogSources.add("AID_LMKD")
    }

    fun allowFromWifi() {
        allowedLogSources.add("AID_NETWORK_STACK")
        allowedLogSources.add("AID_WIFI")
    }

    fun whitelistAllAtomIds(enabled: Boolean = true) {
        whitelistAllAtomIds = enabled
    }

    fun build(): StatsdConfig =
        StatsdConfig(
            id = id,
            allowed_log_source = allowedLogSources.sorted(),
            whitelisted_atom_ids = if (whitelistAllAtomIds) whitelistedAtomIds.sorted() else emptyList(),
            atom_matcher = atomMatchers,
            event_metric = eventMetrics,
            statsd_config_options = StatsdConfigOptions(),
        )

    fun eventMetric(init: EventMetricBuilder.() -> Unit) {
        val metric = EventMetricBuilder(this, nextId()).apply(init).build()
        eventMetrics.add(metric)
    }

    internal fun nextId(): Long = idGenerator.incrementAndGet()

    companion object {
        private const val INITIAL_ID = 1000L
    }
}

class EventMetricBuilder internal constructor(
    private val configBuilder: StatsdConfigBuilder,
    private val id: Long,
) : AtomMatcherCreator(configBuilder) {
    private var atomMatcherId: Long? = null

    operator fun AtomMatcher.unaryPlus() {
        atomMatcherId = id
    }

    fun build() = EventMetric(
        id = id,
        what = atomMatcherId ?: throw IllegalArgumentException("An atom matcher is required but none was provided"),
    )
}

abstract class AtomMatcherCreator(private val configBuilder: StatsdConfigBuilder) {
    fun simpleMatcher(atomId: Int) = AtomMatcher(
        id = configBuilder.nextId(),
        simple_atom_matcher = SimpleAtomMatcher(
            atom_id = atomId,
        ),
    ).also {
        configBuilder.whitelistedAtomIds.add(atomId)
        configBuilder.atomMatchers.add(it)
    }

    fun and(vararg matchers: AtomMatcher): AtomMatcher = logicalOp(LogicalOperation.AND, matchers.toList())
    fun or(vararg matchers: AtomMatcher): AtomMatcher = logicalOp(LogicalOperation.OR, matchers.toList())
    fun nor(vararg matchers: AtomMatcher): AtomMatcher = logicalOp(LogicalOperation.NOR, matchers.toList())
    fun nand(vararg matchers: AtomMatcher): AtomMatcher = logicalOp(LogicalOperation.NAND, matchers.toList())
    fun not(vararg matchers: AtomMatcher): AtomMatcher = logicalOp(LogicalOperation.NOT, matchers.toList())

    private fun logicalOp(op: LogicalOperation, matchers: List<AtomMatcher>) = AtomMatcher(
        id = configBuilder.nextId(),
        combination = Combination(
            operation = op,
            matcher = matchers.toList().map { it.id ?: throw IllegalArgumentException("Matcher with a null ID") },
        ),
    ).also { configBuilder.atomMatchers.add(it) }
}
