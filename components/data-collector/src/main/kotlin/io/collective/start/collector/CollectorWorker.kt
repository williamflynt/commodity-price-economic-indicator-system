package io.collective.start.collector

import edu.colorado.AppDatabase
import edu.colorado.FredApiClient
import edu.colorado.ZmqRouter
import io.collective.workflow.Worker
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.math.BigDecimal

const val BASE_CATEGORY = "T5YIE"

fun maybeBigDecimal(s: String?): BigDecimal? {
    return try {
        s?.toBigDecimal()
    } catch (e: Throwable) {
        null
    }
}

class CollectorWorker(val db: AppDatabase, private val eventBus: ZmqRouter, private val client: FredApiClient, override val name: String = "data-collector") : Worker<CollectorTask> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun execute(task: CollectorTask) {
        logger.info("starting data collection.")
        runBlocking {
            eventBus.publish("COLLECT-START ${task.id} startDate=${task.startDate} endDate=${task.endDate} category=${task.category}")
            val category1 = client.getSeriesObservations(task.category, task.startDate, task.endDate)
            category1.observations.forEach { db.saveObservation(task.category, it.date, maybeBigDecimal(it.value)) }
            val base = client.getSeriesObservations(BASE_CATEGORY, task.startDate, task.endDate)
            base.observations.forEach { db.saveObservation(BASE_CATEGORY, it.date, maybeBigDecimal(it.value)) }
            eventBus.publish("COLLECT-DONE ${task.id} startDate=${task.startDate} endDate=${task.endDate} category=${task.category}")
        }
        logger.info("completed data collection.")
    }
}
