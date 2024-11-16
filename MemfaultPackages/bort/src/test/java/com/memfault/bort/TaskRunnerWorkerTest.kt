package com.memfault.bort

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.workDataOf
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.memfault.bort.uploader.mockTaskRunnerWorker
import com.memfault.bort.uploader.mockWorkerFactory
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val SAMPLE_TASK_INPUT_DATA_KEY = "key"

class SampleTask(
    val getMaxAttempts: () -> Int = { 3 },
    val result: TaskResult = TaskResult.SUCCESS,
) : Task<String> {

    override fun getMaxAttempts(input: String): Int = getMaxAttempts()

    var finallyCalled: Boolean = false
    var doWorkInput: String? = null

    override suspend fun doWork(input: String): TaskResult = result.also {
        doWorkInput = input
    }

    override fun convertAndValidateInputData(inputData: Data): String =
        checkNotNull(inputData.getString(SAMPLE_TASK_INPUT_DATA_KEY), { "data missing" })

    override fun finally(input: String?) {
        finallyCalled = true
    }
}

@RunWith(RobolectricTestRunner::class)
class BaseTaskTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun inputDataDeserializationFailed() = runTest {
        val task = SampleTask()
        val worker = mockTaskRunnerWorker<SampleTask>(
            context = context,
            workerFactory = mockWorkerFactory(overrideTask = task),
            inputData = workDataOf(),
        )
        assertThat(worker.doWork()).isEqualTo(Result.failure())
        assertThat(task.finallyCalled).isTrue()
    }

    @Test
    fun limitAttempts() = runTest {
        val runs = listOf(
            Triple(1, Result.retry(), false),
            Triple(2, Result.failure(), true),
        )

        for ((runAttemptCount, expectedResult, expectedFinallyCalled) in runs) {
            val task = SampleTask(getMaxAttempts = { 1 }, result = TaskResult.RETRY)
            mockTaskRunnerWorker<SampleTask>(
                context = context,
                workerFactory = mockWorkerFactory(overrideTask = task),
                inputData = workDataOf(SAMPLE_TASK_INPUT_DATA_KEY to "foo"),
                runAttemptCount = runAttemptCount,
            ).let { worker ->
                assertThat(worker.doWork()).isEqualTo(expectedResult)
                assertThat(task.finallyCalled).isEqualTo(expectedFinallyCalled)
            }
        }
    }

    @Test
    fun happyPath() = runTest {
        val task = SampleTask(result = TaskResult.SUCCESS)
        val worker = mockTaskRunnerWorker<SampleTask>(
            context,
            mockWorkerFactory(overrideTask = task),
            workDataOf(SAMPLE_TASK_INPUT_DATA_KEY to "foo"),
        )

        assertThat(worker.doWork()).isEqualTo(Result.success())
        assertThat(task.finallyCalled).isTrue()
        assertThat(task.doWorkInput).isEqualTo("foo")
    }
}
