import Foundation

/// Print job status enum matching TypeScript spec
public enum PrintJobStatusType: String {
    case queued
    case printing
    case completed
    case failed
}

/// Represents a print job in the queue
public struct PrintJob {
    public let id: String
    public let execute: () async throws -> Void
    public var status: PrintJobStatusType
    public var error: String?

    public init(
        id: String = UUID().uuidString,
        execute: @escaping () async throws -> Void,
        status: PrintJobStatusType = .queued,
        error: String? = nil
    ) {
        self.id = id
        self.execute = execute
        self.status = status
        self.error = error
    }
}

/// Print job status data for external API
public struct PrintJobStatus {
    public let jobId: String
    public let status: String
    public let error: String?

    public init(jobId: String, status: String, error: String? = nil) {
        self.jobId = jobId
        self.status = status
        self.error = error
    }
}

/// Manages print job queue to ensure sequential execution
/// and prevent race conditions.
public actor PrintQueue {
    private var jobs: [String: PrintJob] = [:]
    private var queue: [PrintJob] = []
    private var isProcessing = false
    public private(set) var isPrinting = false

    public init() {}

    /// Enqueue a print job
    public func enqueue(_ execute: @escaping () async throws -> Void) async -> PrintJob {
        var job = PrintJob(execute: execute)
        jobs[job.id] = job
        queue.append(job)

        if !isProcessing {
            Task { await processQueue() }
        }

        return job
    }

    private let jobTimeoutSeconds: TimeInterval = 60 // 60 seconds max for any print job

    /// Process the queue
    private func processQueue() async {
        isProcessing = true

        while !queue.isEmpty {
            var job = queue.removeFirst()
            isPrinting = true
            job.status = .printing
            jobs[job.id] = job

            do {
                // Add timeout to prevent stuck jobs from blocking the queue
                try await withThrowingTaskGroup(of: Void.self) { group in
                    group.addTask {
                        try await job.execute()
                    }
                    group.addTask {
                        try await Task.sleep(nanoseconds: UInt64(self.jobTimeoutSeconds * 1_000_000_000))
                        throw PrintQueueError.timeout
                    }

                    // Wait for first task to complete
                    try await group.next()
                    group.cancelAll()
                }
                job.status = .completed
            } catch is PrintQueueError {
                job.status = .failed
                job.error = "Print job timed out after \(Int(jobTimeoutSeconds)) seconds"
            } catch {
                job.status = .failed
                job.error = error.localizedDescription
            }

            jobs[job.id] = job
            isPrinting = false
        }

        isProcessing = false
    }

    private enum PrintQueueError: Error {
        case timeout
    }

    /// Wait for a job to complete
    public func awaitJob(id: String, timeoutMs: Int = 30000) async -> PrintJobStatus {
        let startTime = Date()
        let timeout = TimeInterval(timeoutMs) / 1000.0

        while Date().timeIntervalSince(startTime) < timeout {
            if let job = jobs[id],
               job.status == .completed || job.status == .failed {
                return PrintJobStatus(jobId: job.id, status: job.status.rawValue, error: job.error)
            }
            try? await Task.sleep(nanoseconds: 50_000_000) // 50ms
        }

        // Timeout - return current status
        if let job = jobs[id] {
            return PrintJobStatus(jobId: job.id, status: job.status.rawValue, error: job.error)
        }
        return PrintJobStatus(jobId: id, status: PrintJobStatusType.failed.rawValue, error: "Timeout waiting for print job")
    }

    /// Get status of all jobs
    public func getQueueStatus() -> [PrintJobStatus] {
        return jobs.values.map { job in
            PrintJobStatus(jobId: job.id, status: job.status.rawValue, error: job.error)
        }
    }

    /// Get status of a specific job
    public func getJobStatus(id: String) -> PrintJobStatus? {
        guard let job = jobs[id] else { return nil }
        return PrintJobStatus(jobId: job.id, status: job.status.rawValue, error: job.error)
    }

    /// Clear completed and failed jobs
    public func clearCompleted() {
        jobs = jobs.filter { _, job in
            job.status != .completed && job.status != .failed
        }
    }
}
