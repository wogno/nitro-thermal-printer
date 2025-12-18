package com.nitro.thermalprinter

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Print job status enum matching TypeScript spec
 */
enum class PrintJobStatusType(val value: String) {
    QUEUED("queued"),
    PRINTING("printing"),
    COMPLETED("completed"),
    FAILED("failed")
}

/**
 * Represents a print job in the queue
 */
data class PrintJob(
    val id: String = UUID.randomUUID().toString(),
    val execute: suspend () -> Unit,
    var status: PrintJobStatusType = PrintJobStatusType.QUEUED,
    var error: String? = null
)

/**
 * Print job status data for external API
 */
data class PrintJobStatus(
    val jobId: String,
    val status: String,
    val error: String? = null
)

/**
 * Manages print job queue to ensure sequential execution
 * and prevent race conditions.
 */
class PrintQueue(private val scope: CoroutineScope) {
    private val queue = Channel<PrintJob>(Channel.UNLIMITED)
    private val jobs = ConcurrentHashMap<String, PrintJob>()
    private val _isPrinting = MutableStateFlow(false)

    val isPrinting: StateFlow<Boolean> = _isPrinting.asStateFlow()

    private var processingJob: Job? = null

    init {
        startProcessing()
    }

    private fun startProcessing() {
        processingJob = scope.launch {
            for (job in queue) {
                processJob(job)
            }
        }
    }

    private suspend fun processJob(job: PrintJob) {
        _isPrinting.value = true
        job.status = PrintJobStatusType.PRINTING
        jobs[job.id] = job

        try {
            // Add timeout to prevent stuck jobs from blocking the queue
            withTimeout(JOB_TIMEOUT_MS) {
                job.execute()
            }
            job.status = PrintJobStatusType.COMPLETED
        } catch (e: TimeoutCancellationException) {
            job.status = PrintJobStatusType.FAILED
            job.error = "Print job timed out after ${JOB_TIMEOUT_MS / 1000} seconds"
        } catch (e: Exception) {
            job.status = PrintJobStatusType.FAILED
            job.error = e.message ?: "Unknown error"
        } finally {
            jobs[job.id] = job
            _isPrinting.value = false
        }
    }

    companion object {
        private const val JOB_TIMEOUT_MS = 60000L // 60 seconds max for any print job
    }

    /**
     * Enqueue a print job
     * @param execute Suspend function to execute
     * @returns The created print job
     */
    suspend fun enqueue(execute: suspend () -> Unit): PrintJob {
        val job = PrintJob(execute = execute)
        jobs[job.id] = job
        queue.send(job)
        return job
    }

    /**
     * Wait for a job to complete
     * @param jobId ID of the job to wait for
     * @param timeoutMs Maximum time to wait
     * @returns Final job status
     */
    suspend fun awaitJob(jobId: String, timeoutMs: Long = 30000): PrintJobStatus {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val job = jobs[jobId]
            if (job != null && (job.status == PrintJobStatusType.COMPLETED ||
                                job.status == PrintJobStatusType.FAILED)) {
                return PrintJobStatus(job.id, job.status.value, job.error)
            }
            delay(50)
        }

        // Timeout - return current status
        val job = jobs[jobId]
        return PrintJobStatus(
            jobId,
            job?.status?.value ?: PrintJobStatusType.FAILED.value,
            job?.error ?: "Timeout waiting for print job"
        )
    }

    /**
     * Get status of all jobs in queue
     */
    fun getQueueStatus(): List<PrintJobStatus> {
        return jobs.values.map { job ->
            PrintJobStatus(job.id, job.status.value, job.error)
        }
    }

    /**
     * Get status of a specific job
     */
    fun getJobStatus(jobId: String): PrintJobStatus? {
        val job = jobs[jobId] ?: return null
        return PrintJobStatus(job.id, job.status.value, job.error)
    }

    /**
     * Clear completed and failed jobs from history
     */
    fun clearCompleted() {
        jobs.entries.removeIf {
            it.value.status == PrintJobStatusType.COMPLETED ||
            it.value.status == PrintJobStatusType.FAILED
        }
    }

    /**
     * Cancel processing and clean up
     */
    fun dispose() {
        processingJob?.cancel()
        queue.close()
        jobs.clear()
    }
}
