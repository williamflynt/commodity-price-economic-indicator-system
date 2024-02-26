package io.collective.start.analyzer

import io.collective.workflow.WorkFinder
import org.slf4j.LoggerFactory

class ExampleWorkFinder : WorkFinder<AnalyzeTask> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun findRequested(name: String): List<AnalyzeTask> {
        logger.info("finding work.")

        val work = AnalyzeTask("test","2000-01-01", "2001-01-01", "T5YIE")

        return mutableListOf(work)
    }

    override fun markCompleted(info: AnalyzeTask) {
        logger.info("marking work complete.")
    }
}