package io.collective.workflow

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.sync.Mutex
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

class ContinuousWorkSupervisor<T>(private val workChannel: ReceiveChannel<T>, workers: List<Worker<T>>) {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val service = Executors.newFixedThreadPool(workers.size)

    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisorJob)

    private val workerStatuses = workers.associateWith { Mutex() }
    private var started = false

    fun start() {
        if (started) {
            return
        }
        scope.launch {
            for (work in workChannel) {
                dispatchWork(work)
            }
            // Call when workChannel is closed.
            shutdown()
        }
        started = true
    }

    private fun shutdown() {
        service.shutdown()
        scope.cancel()
    }

    private suspend fun dispatchWork(work: T) {
        val worker = selectWorker() // waits until a worker is free
        workerStatuses[worker]?.lock() // mark worker as busy
        service.submit {
            try {
                worker.execute(work)
                logger.info("completed work.")
            } catch (e: Throwable) {
                logger.error("unable to complete work", e)
            } finally {
                workerStatuses[worker]?.unlock() // mark worker as free
            }
        }
    }

    private suspend fun selectWorker(): Worker<T> {
        // Wait until a worker is free
        while (true) {
            for ((worker, mutex) in workerStatuses) {
                if (!mutex.isLocked) {
                    return worker
                }
            }
            delay(100) // wait a bit before checking again
        }
    }
}
