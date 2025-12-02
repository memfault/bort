package com.memfault.bort.metrics.statsd

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.metrics.statsd.proto.AtomMatcher
import com.memfault.bort.metrics.statsd.proto.EventMetric
import com.memfault.bort.metrics.statsd.proto.LogicalOperation
import com.memfault.bort.metrics.statsd.proto.SimpleAtomMatcher
import com.memfault.bort.metrics.statsd.proto.StatsdConfig
import com.memfault.bort.metrics.statsd.proto.StatsdConfig.StatsdConfigOptions
import org.junit.Test

internal class StatsdConfigDslTest {
    @Test fun `builds an exhaustive config of all top level elements`() {
        val config = statsdConfig(0xdeadbeef) {
            allowFromSystem()
            allowFromLowMemoryKiller()
            whitelistAllAtomIds(true)

            eventMetric {
                +simpleMatcher(72)
            }

            eventMetric {
                +and(
                    simpleMatcher(12),
                    simpleMatcher(31),
                    or(
                        simpleMatcher(91),
                    ),
                )
            }
        }
        assertThat(config).isEqualTo(
            StatsdConfig(
                id = 0xdeadbeef,
                allowed_log_source = listOf("AID_LMKD", "AID_SYSTEM"),
                whitelisted_atom_ids = listOf(12, 31, 72, 91),
                atom_matcher = listOf(
                    AtomMatcher(
                        id = 1002,
                        simple_atom_matcher = SimpleAtomMatcher(atom_id = 72),
                    ),
                    AtomMatcher(
                        id = 1004,
                        simple_atom_matcher = SimpleAtomMatcher(atom_id = 12),
                    ),
                    AtomMatcher(
                        id = 1005,
                        simple_atom_matcher = SimpleAtomMatcher(atom_id = 31),
                    ),
                    AtomMatcher(
                        id = 1006,
                        simple_atom_matcher = SimpleAtomMatcher(atom_id = 91),
                    ),
                    AtomMatcher(
                        id = 1007,
                        combination = AtomMatcher.Combination(
                            operation = LogicalOperation.OR,
                            matcher = listOf(1006),
                        ),
                    ),
                    AtomMatcher(
                        id = 1008,
                        combination = AtomMatcher.Combination(
                            operation = LogicalOperation.AND,
                            matcher = listOf(1004, 1005, 1007),
                        ),
                    ),
                ),
                event_metric = listOf(
                    EventMetric(
                        id = 1001,
                        what = 1002,
                    ),
                    EventMetric(
                        id = 1003,
                        what = 1008,
                    ),
                ),
                statsd_config_options = StatsdConfigOptions(),
            ),
        )
    }
}
