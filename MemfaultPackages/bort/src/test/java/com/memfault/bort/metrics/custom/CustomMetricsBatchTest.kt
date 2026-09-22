package com.memfault.bort.metrics.custom

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.memfault.bort.connectivity.CONNECTIVITY_TYPE_METRIC
import com.memfault.bort.connectivity.ConnectivityState
import com.memfault.bort.connectivity.ConnectivityState.NONE
import com.memfault.bort.metrics.MetricsDbTestEnvironment
import com.memfault.bort.metrics.database.DbMetricMetadata
import com.memfault.bort.metrics.database.MetricsDb
import com.memfault.bort.reporting.NumericAgg.SUM
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg.TIME_TOTALS
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * Counts transactions committing at least one `metric_values` row; Room's own bookkeeping transactions are
 * ignored, as are transactions that roll back. State is per-thread, since a transaction runs entirely on one
 * thread.
 */
private class MetricValueCommitCounter : SupportSQLiteOpenHelper.Factory {
    private class TransactionState {
        /** One entry per open transaction, true once it has been marked successful. */
        val successful = mutableListOf<Boolean>()
        val depth: Int get() = successful.size
        var wroteMetricValue = false
        var rolledBack = false

        fun begin() {
            successful += false
        }

        fun markSuccessful() {
            if (successful.isNotEmpty()) successful[successful.lastIndex] = true
        }

        /** A level ending without being marked successful rolls back the whole transaction. */
        fun end() {
            if (!successful.removeAt(successful.lastIndex)) rolledBack = true
        }
    }

    private val commitCount = AtomicInteger()
    private val threadState = ThreadLocal.withInitial { TransactionState() }
    private val state: TransactionState get() = checkNotNull(threadState.get())

    val commits: Int get() = commitCount.get()

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        CountingOpenHelper(FrameworkSQLiteOpenHelperFactory().create(configuration))

    private inner class CountingOpenHelper(
        private val delegate: SupportSQLiteOpenHelper,
    ) : SupportSQLiteOpenHelper by delegate {
        private val writable by lazy { CountingDatabase(delegate.writableDatabase) }
        private val readable by lazy { CountingDatabase(delegate.readableDatabase) }

        override val writableDatabase: SupportSQLiteDatabase get() = writable
        override val readableDatabase: SupportSQLiteDatabase get() = readable
    }

    private inner class CountingDatabase(
        private val delegate: SupportSQLiteDatabase,
    ) : SupportSQLiteDatabase by delegate {
        override fun beginTransaction() {
            state.begin()
            delegate.beginTransaction()
        }

        override fun beginTransactionNonExclusive() {
            state.begin()
            delegate.beginTransactionNonExclusive()
        }

        override fun setTransactionSuccessful() {
            delegate.setTransactionSuccessful()
            state.markSuccessful()
        }

        override fun endTransaction() {
            delegate.endTransaction()
            val transaction = state
            transaction.end()
            if (transaction.depth == 0) {
                if (transaction.wroteMetricValue && !transaction.rolledBack) commitCount.incrementAndGet()
                transaction.wroteMetricValue = false
                transaction.rolledBack = false
            }
        }

        override fun compileStatement(sql: String): SupportSQLiteStatement =
            delegate.compileStatement(sql).let { statement ->
                if (METRIC_VALUES_TABLE in sql) MetricValueStatement(statement) else statement
            }
    }

    private inner class MetricValueStatement(
        private val delegate: SupportSQLiteStatement,
    ) : SupportSQLiteStatement by delegate {
        override fun executeInsert(): Long {
            state.wroteMetricValue = true
            return delegate.executeInsert()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class CustomMetricsBatchTest {
    private val commits = MetricValueCommitCounter()

    @get:Rule
    val metricsDbTestEnvironment = MetricsDbTestEnvironment().apply {
        openHelperFactory = commits
    }

    private val db: MetricsDb get() = metricsDbTestEnvironment.db
    private val dao: CustomMetrics get() = metricsDbTestEnvironment.dao

    @Test
    fun `a batch commits once, instead of once per metric`() = runTest {
        val counter = Reporting.report().counter("test", sumInReport = true)

        val unbatched = commits.measure {
            repeat(METRIC_COUNT) { counter.increment() }
        }

        val batched = commits.measure {
            dao.batchMetricWrites {
                repeat(METRIC_COUNT) { counter.increment() }
            }
        }

        assertThat(unbatched).isEqualTo(METRIC_COUNT)
        assertThat(batched).isEqualTo(1)
    }

    @Test
    fun `batched metrics end up in the report`() = runTest {
        val now = System.currentTimeMillis()

        dao.batchMetricWrites {
            Reporting.report().counter("counter", sumInReport = true).incrementBy(2, now, now)
            Reporting.report().distribution("distribution", aggregations = listOf(SUM)).record(3.0, now, now)
            Reporting.report().stringProperty("property", addLatestToReport = true).update("value", now, now)
        }

        val report = dao.collectHeartbeat(endTimestampMs = now + 1, endUptimeMs = now + 1)

        assertThat(report).prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
            "counter.sum" to JsonPrimitive(2.0),
            "distribution.sum" to JsonPrimitive(3.0),
            "property.latest" to JsonPrimitive("value"),
        )
    }

    @Test
    fun `the buffer is flushed when the batch throws`() = runTest {
        val now = System.currentTimeMillis()

        val result = runCatching {
            dao.batchMetricWrites {
                Reporting.report().counter("counter", sumInReport = true).incrementBy(2, now, now)
                error("collection failed")
            }
        }

        assertThat(result.isFailure).isTrue()
        assertThat(dao.collectHeartbeat(endTimestampMs = now + 1, endUptimeMs = now + 1))
            .prop(CustomReport::hourlyHeartbeatReport)
            .prop(MetricReport::metrics)
            .containsOnly("counter.sum" to JsonPrimitive(2.0))
    }

    @Test
    fun `starting a session flushes the buffer, so it can read the latest metric values`() = runTest {
        val now = System.currentTimeMillis()

        dao.batchMetricWrites {
            Reporting.report()
                .stateTracker<ConnectivityState>(name = CONNECTIVITY_TYPE_METRIC, aggregations = listOf(TIME_TOTALS))
                .state(NONE, now, now)

            assertThat(db.dao().dump().values).isEmpty()

            Reporting.session("session-1").start(now + 1, now + 1)

            assertThat(db.dao().dump().metadata.map(DbMetricMetadata::eventName)).contains(CONNECTIVITY_TYPE_METRIC)
        }
    }

    private suspend fun MetricValueCommitCounter.measure(block: suspend () -> Unit): Int {
        val before = commits
        block()
        return commits - before
    }
}

private const val METRIC_COUNT = 20
private const val METRIC_VALUES_TABLE = "metric_values"
