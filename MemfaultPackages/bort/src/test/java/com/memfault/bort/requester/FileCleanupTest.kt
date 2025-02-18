package com.memfault.bort.requester

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.memfault.bort.fileExt.deleteSilently
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

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
        cleanupFiles(
            dir = folder.root,
            maxDirStorageBytes = 2,
            maxFileAge = ZERO,
            timeNowMs = NOW_MS,
        )

        assertThat(file1_7days_old.exists()).isFalse()
        assertThat(file2_1day_old.exists()).isTrue()
        assertThat(file3_1hour_old.exists()).isTrue()
    }

    @Test
    fun deleteNoBugReportsWhenNotOverLimit() {
        cleanupFiles(
            dir = folder.root,
            maxDirStorageBytes = 3,
            maxFileAge = ZERO,
            timeNowMs = NOW_MS,
        )

        assertThat(file1_7days_old.exists()).isTrue()
        assertThat(file2_1day_old.exists()).isTrue()
        assertThat(file3_1hour_old.exists()).isTrue()
    }

    @Test
    fun doesNotCrashIfDirectoryDoesNotExist() {
        folder.root.deleteRecursively()

        cleanupFiles(
            dir = folder.root,
            maxDirStorageBytes = 3,
            maxFileAge = ZERO,
            timeNowMs = NOW_MS,
        )
    }

    @Test
    fun noBugReportFilesToDelete() {
        file1_7days_old.deleteSilently()
        file2_1day_old.deleteSilently()
        file3_1hour_old.deleteSilently()

        cleanupFiles(
            dir = folder.root,
            maxDirStorageBytes = 3,
            maxFileAge = ZERO,
            timeNowMs = NOW_MS,
        )
    }

    @Test
    fun deleteOldBugReportsOverMaxAge() {
        cleanupFiles(
            dir = folder.root,
            maxDirStorageBytes = 3,
            maxFileAge = 2.days,
            timeNowMs = NOW_MS,
        )

        assertThat(file1_7days_old.exists()).isFalse()
        assertThat(file2_1day_old.exists()).isTrue()
        assertThat(file3_1hour_old.exists()).isTrue()
    }

    @Test
    fun deleteOldBugReportsOverMaxAge_allOverLimit() {
        cleanupFiles(
            dir = folder.root,
            maxDirStorageBytes = 3,
            maxFileAge = 1.minutes,
            timeNowMs = NOW_MS,
        )

        assertThat(file1_7days_old.exists()).isFalse()
        assertThat(file2_1day_old.exists()).isFalse()
        assertThat(file3_1hour_old.exists()).isFalse()
    }
}
