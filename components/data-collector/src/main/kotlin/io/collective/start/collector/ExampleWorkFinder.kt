package io.collective.start.collector

import io.collective.workflow.WorkFinder
import org.slf4j.LoggerFactory

class ExampleWorkFinder : WorkFinder<CollectorTask> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun findRequested(name: String): List<CollectorTask> {
        logger.info("finding work.")

        val work = CollectorTask("test","2000-01-01", "2001-01-01", "T5YIE")

        return mutableListOf(work)
    }

    override fun markCompleted(info: CollectorTask) {
        logger.info("marking work complete.")
    }
}