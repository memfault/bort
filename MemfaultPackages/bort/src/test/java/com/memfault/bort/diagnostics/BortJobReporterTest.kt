package com.memfault.bort.diagnostics

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.memfault.bort.diagnostics.BortJobReporter.BortJob
import com.memfault.bort.dropbox.DropBoxGetEntriesTask
import com.memfault.bort.metrics.MetricsCollectionTask
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.uploader.FileUploadTask
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class BortJobReporterTest {
    private var absoluteTimeMs: Long = 1714150878000
    private val absoluteTimeProvider = { AbsoluteTime(Instant.ofEpochMilli(absoluteTimeMs)) }
    private val bortErrors: BortErrors = mockk(relaxed = true)
    private lateinit var db: BortErrorsDb
    private lateinit var bortJobReporter: BortJobReporter

    @Before
    fun createDB() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, BortErrorsDb::class.java)
            .fallbackToDestructiveMigration().allowMainThreadQueries().build()
        bortJobReporter =
            BortJobReporter(bortErrorsDb = db, bortErrors = bortErrors, absoluteTimeProvider = absoluteTimeProvider)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun recordJob() = runTest {
        val startTimeMs = 1714150878000
        val endTimeMs = startTimeMs + 5_000
        val queryTimeMs = startTimeMs + 10_000
        val jobName = "job"
        val result = "result"
        val job = BortJob(
            jobName = jobName,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            result = result,
            duration = 5.seconds,
            sinceStart = 10.seconds,
        )

        absoluteTimeMs = job.startTimeMs
        val id = bortJobReporter.onJobStarted(jobName = jobName)

        absoluteTimeMs = endTimeMs
        bortJobReporter.onJobFinished(id, result)

        absoluteTimeMs = queryTimeMs
        assertThat(bortJobReporter.getLatestForEachJob()).containsExactly(job)
        assertThat(bortJobReporter.getIncompleteJobs()).isEmpty()
        val jobStats = bortJobReporter.jobStats()
        assertThat(jobStats[jobName]).isEqualTo("ran 1 times")
    }

    @Test
    fun incompleteJob() = runTest {
        val startTimeMs = 1714150878000
        val queryTimeMs = startTimeMs + 10_000
        val jobName = "job"

        absoluteTimeMs = startTimeMs
        bortJobReporter.onJobStarted(jobName = jobName)

        val job = BortJob(
            jobName = jobName,
            startTimeMs = startTimeMs,
            endTimeMs = null,
            result = null,
            duration = null,
            sinceStart = 10.seconds,
        )

        absoluteTimeMs = queryTimeMs
        assertThat(bortJobReporter.getLatestForEachJob()).containsExactly(job)
        assertThat(bortJobReporter.getIncompleteJobs()).containsExactly(job)
        val jobStats = bortJobReporter.jobStats()
        assertThat(jobStats[jobName]).isEqualTo("ran 1 times")
    }

    @Test
    fun cleanup() = runTest {
        val startTime1 = 1714150878000
        val endTime1 = startTime1 + 5_000
        val jobName1 = FileUploadTask::class.qualifiedName!!
        val result1 = "result"

        val queryTimeMs = startTime1 + 25.hours.inWholeMilliseconds + 10_000

        val startTime2 = startTime1 + 2.hours.inWholeMilliseconds
        val endTime2 = startTime2 + 1
        val jobName2 = DropBoxGetEntriesTask::class.qualifiedName!!
        val result2 = "result2"
        val job2 = BortJob(
            jobName = jobName2,
            startTimeMs = startTime2,
            endTimeMs = endTime2,
            result = result2,
            duration = 1.milliseconds,
            sinceStart = 23.hours + 10.seconds,
        )

        val startTime3 = startTime1 + 25.hours.inWholeMilliseconds
        val endTime3 = startTime3 + 5
        val jobName3 = MetricsCollectionTask::class.qualifiedName!!
        val result3 = "result3"
        val job3 = BortJob(
            jobName = jobName3,
            startTimeMs = startTime3,
            endTimeMs = endTime3,
            result = result3,
            duration = 5.milliseconds,
            sinceStart = 10.seconds,
        )

        absoluteTimeMs = startTime1
        val id1 = bortJobReporter.onJobStarted(jobName = jobName1)

        absoluteTimeMs = endTime1
        bortJobReporter.onJobFinished(id1, result1)

        absoluteTimeMs = startTime2
        val id2 = bortJobReporter.onJobStarted(jobName = jobName2)

        absoluteTimeMs = endTime2
        bortJobReporter.onJobFinished(id2, result2)

        absoluteTimeMs = startTime3
        val id3 = bortJobReporter.onJobStarted(jobName = jobName3)

        absoluteTimeMs = endTime3
        bortJobReporter.onJobFinished(id3, result3)

        absoluteTimeMs = queryTimeMs
        assertThat(bortJobReporter.getLatestForEachJob()).containsExactly(job3, job2)
        assertThat(bortJobReporter.getIncompleteJobs()).isEmpty()
        val jobStats = bortJobReporter.jobStats()
        assertThat(jobStats[jobName1]).isEqualTo("no record")
        assertThat(jobStats[jobName2]).isEqualTo("ran 1 times")
        assertThat(jobStats[jobName3]).isEqualTo("ran 1 times")
    }
}
