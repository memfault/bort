package com.memfault.bort

import androidx.work.Data
import androidx.work.workDataOf
import com.memfault.bort.uploader.mockTaskRunnerWorker
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val SAMPLE_TASK_INPUT_DATA_KEY = "key"

class SampleTask(
    val result: TaskResult = TaskResult.SUCCESS,
    override val maxAttempts: Int = 3
) : Task<String>() {

    var finallyCalled: Boolean = false
    var doWorkInput: String? = null

    override suspend fun doWork(worker: TaskRunnerWorker, input: String): TaskResult =
        result.also {
            doWorkInput = input
        }

    override fun convertAndValidateInputData(inputData: Data): String =
        checkNotNull(inputData.getString(SAMPLE_TASK_INPUT_DATA_KEY), { "data missing" })

    override fun finally(input: String?) {
        finallyCalled = true
    }
}

class BaseTaskTest {
    @Test
    fun inputDataDeserializationFailed() {
        val worker = mockTaskRunnerWorker(workDataOf())
        val task = SampleTask()
        runBlocking {
            assertEquals(TaskResult.FAILURE, task.doWork(worker))
        }
        assertTrue(task.finallyCalled)
    }

    @Test
    fun limitAttempts() {
        val runs = listOf(
            Triple(1, TaskResult.RETRY, false),
            Triple(2, TaskResult.FAILURE, true)
        )

        for ((runAttemptCount, expectedResult, expectedFinallyCalled) in runs) {
            mockTaskRunnerWorker(
                inputData = workDataOf(SAMPLE_TASK_INPUT_DATA_KEY to "foo"),
                runAttemptCount = runAttemptCount
            ).let { worker ->
                SampleTask(maxAttempts = 1, result = TaskResult.RETRY).let { task ->
                    assertEquals(
                        expectedResult,
                        runBlocking {
                            task.doWork(worker)
                        }
                    )
                    assertEquals(expectedFinallyCalled, task.finallyCalled)
                }
            }
        }
    }

    @Test
    fun happyPath() {
        val worker = mockTaskRunnerWorker(workDataOf(SAMPLE_TASK_INPUT_DATA_KEY to "foo"))
        val task = SampleTask(result = TaskResult.SUCCESS)
        runBlocking {
            assertEquals(TaskResult.SUCCESS, task.doWork(worker))
        }
        assertTrue(task.finallyCalled)
        assertEquals("foo", task.doWorkInput)
    }
}
