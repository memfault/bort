package com.memfault.bort.requester

import com.memfault.bort.fileExt.deleteSilently
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.days
import kotlin.time.minutes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileCleanupTest {
    @get:Rule
    val folder = TemporaryFolder()

    lateinit var file1_7days_old: File
    lateinit var file2_1day_old: File
    lateinit var file3_1hour_old: File

    companion object {
        private const val NOW_MS: Long = 1620967982000
        private val OLDEST_7DAYS_MODIFIED_TIMESTAMP_MS: Long = NOW_MS - TimeUnit.DAYS.toMillis(7)
        private val MIDDLE_1DAY_MODIFIED_TIMESTAMP_MS: Long = NOW_MS - TimeUnit.DAYS.toMillis(1)
        private val LATEST_1HOUR_MODIFIED_TIMESTAMP_MS: Long = NOW_MS - TimeUnit.HOURS.toMillis(1)
    }

    @Before
    fun setup() {
        file1_7days_old = folder.newFile("file1.zip").apply {
            // Files are 1 byte each in size
            writeText("1")
            setLastModified(OLDEST_7DAYS_MODIFIED_TIMESTAMP_MS)
        }
        file2_1day_old = folder.newFile("file2.zip").apply {
            writeText("2")
            setLastModified(MIDDLE_1DAY_MODIFIED_TIMESTAMP_MS)
        }
        file3_1hour_old = folder.newFile("file3.zip").apply {
            writeText("3")
            setLastModified(LATEST_1HOUR_MODIFIED_TIMESTAMP_MS)
        }
    }

    @Test
    fun deleteOldestBugReportFilesOverLimit() {
        cleanupBugReports(
            bugReportDir = folder.root,
            maxBugReportStorageBytes = 2,
            maxBugReportAge = ZERO,
            timeNowMs = NOW_MS
        )

        assertFalse(file1_7days_old.exists())
        assertTrue(file2_1day_old.exists())
        assertTrue(file3_1hour_old.exists())
    }

    @Test
    fun deleteNoBugReportsWhenNotOverLimit() {
        cleanupBugReports(
            bugReportDir = folder.root,
            maxBugReportStorageBytes = 3,
            maxBugReportAge = ZERO,
            timeNowMs = NOW_MS
        )

        assertTrue(file1_7days_old.exists())
        assertTrue(file2_1day_old.exists())
        assertTrue(file3_1hour_old.exists())
    }

    @Test
    fun doesNotCrashIfDirectoryDoesNotExist() {
        folder.root.deleteRecursively()

        cleanupBugReports(
            bugReportDir = folder.root,
            maxBugReportStorageBytes = 3,
            maxBugReportAge = ZERO,
            timeNowMs = NOW_MS
        )
    }

    @Test
    fun noBugReportFilesToDelete() {
        file1_7days_old.deleteSilently()
        file2_1day_old.deleteSilently()
        file3_1hour_old.deleteSilently()

        cleanupBugReports(
            bugReportDir = folder.root,
            maxBugReportStorageBytes = 3,
            maxBugReportAge = ZERO,
            timeNowMs = NOW_MS
        )
    }

    @Test
    fun deleteOldBugReportsOverMaxAge() {
        cleanupBugReports(
            bugReportDir = folder.root,
            maxBugReportStorageBytes = 3,
            maxBugReportAge = 2.days,
            timeNowMs = NOW_MS
        )

        assertFalse(file1_7days_old.exists())
        assertTrue(file2_1day_old.exists())
        assertTrue(file3_1hour_old.exists())
    }

    @Test
    fun deleteOldBugReportsOverMaxAge_allOverLimit() {
        cleanupBugReports(
            bugReportDir = folder.root,
            maxBugReportStorageBytes = 3,
            maxBugReportAge = 1.minutes,
            timeNowMs = NOW_MS
        )

        assertFalse(file1_7days_old.exists())
        assertFalse(file2_1day_old.exists())
        assertFalse(file3_1hour_old.exists())
    }
}
