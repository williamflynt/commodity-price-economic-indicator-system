package io.collective.start.analyzer

import edu.colorado.AnalysisResult
import edu.colorado.AppDatabase
import edu.colorado.CollectedObservation
import edu.colorado.ZmqRouter
import io.collective.workflow.Worker
import io.ktor.html.*
import kotlinx.coroutines.runBlocking
import org.apache.commons.math3.stat.descriptive.rank.Median
import org.apache.commons.math3.stat.regression.SimpleRegression
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

const val BASE_CATEGORY = "T5YIE"

fun daysBetween(startDate: String, endDate: String): Long {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val start = LocalDate.parse(startDate, formatter)
    val end = LocalDate.parse(endDate, formatter)
    return ChronoUnit.DAYS.between(start, end) + 1 // Inclusive.
}

fun calculateSlope(observations: List<CollectedObservation>): Double {
    val regression = SimpleRegression()
    observations.filter{ it.value != null }.forEachIndexed { index, observation ->
        regression.addData(index.toDouble(), observation.value!!.toDouble())
    }
    return regression.slope
}

fun calculateStatsAndRegression(
    category1Observations: List<CollectedObservation>,
    category2Observations: List<CollectedObservation>
): AnalysisResult {
    val category1Values = category1Observations.map { it.value }
    val category2Values = category2Observations.map { it.value }

    val medianCategory1 = Median().evaluate(category1Values.filter{ it != null }.map { it!!.toDouble() }.toDoubleArray())
    val medianCategory2 = Median().evaluate(category2Values.filter{ it != null }.map { it!!.toDouble() }.toDoubleArray())

    val slopeCategory1 = calculateSlope(category1Observations)
    val slopeCategory2 = calculateSlope(category2Observations)

    val regression = SimpleRegression()
    category1Values
        .zip(category2Values)
        .filter{ it.first != null && it.second != null}
        .forEach { (x, y) ->
        regression.addData(x!!.toDouble(), y!!.toDouble())
    }

    val regressionSlope = regression.slope.toFloat()
    val regressionIntercept = regression.intercept.toFloat()

    return AnalysisResult(
        id = "",
        startDate = "",
        endDate = "",
        category1 = "",
        category2 = "",
        medianCategory1 = medianCategory1.toFloat(),
        slopeCategory1 = slopeCategory1.toFloat(),
        medianCategory2 = medianCategory2.toFloat(),
        slopeCategory2 = slopeCategory2.toFloat(),
        regressionSlope = regressionSlope,
        regressionIntercept = regressionIntercept
    )
}

class AnalyzeWorker(
    private val db: AppDatabase,
    private val eventBus: ZmqRouter,
    override val name: String = "data-analyzer"
) : Worker<AnalyzeTask> {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val seen = mutableSetOf<String>()

    override fun execute(task: AnalyzeTask) {
        logger.info("starting data analysis task")
        runBlocking {
            val obs = db.getObservations(task.category,task.startDate, task.endDate)
            val base = db.getObservations(BASE_CATEGORY, task.startDate, task.endDate)
            if (!seen.contains(task.id) && (obs.isEmpty() || base.isEmpty() || obs.size < daysBetween(task.startDate, task.endDate))) {
                seen.add(task.id)
                eventBus.publish("COLLECT ${task.id} startDate=${task.startDate} endDate=${task.endDate} category=${task.category}")
                return@runBlocking
            }
            seen.add(task.id)
            eventBus.publish("ANALYZE-START ${task.id} startDate=${task.startDate} endDate=${task.endDate} category=${task.category}")

            val partial = calculateStatsAndRegression(obs, base)
            db.saveAnalysis(
                task.id,
                task.startDate,
                task.endDate,
                task.category,
                BASE_CATEGORY,
                partial.medianCategory1,
                partial.slopeCategory1,
                partial.medianCategory2,
                partial.slopeCategory2,
                partial.regressionSlope,
                partial.regressionIntercept
            )
            eventBus.publish("ANALYZE-DONE ${task.id} startDate=${task.startDate} endDate=${task.endDate} category=${task.category}")
        }
        logger.info("completed data analysis task")
    }
}
