package edu.colorado

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal

object CollectedObservations : Table() {
    val id = integer("id").autoIncrement()
    val category = text("category").index()
    val date = text("date")
    val value = decimal("value", 10, 4).nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(category, date)
    }
}

object AnalysisResults : Table() {
    val id = text("id")
    val startDate = text("start_date")
    val endDate = text("end_date")
    val category1 = text("category1").index() // The thing we asked for.
    val category2 = text("category2").index() // So far, only "T5YIE".
    val medianCategory1 = decimal("median_category1", 10, 4)
    val slopeCategory1 = decimal("slope_category1", 10, 4)
    val medianCategory2 = decimal("median_category2", 10, 4)
    val slopeCategory2 = decimal("slope_category2", 10, 4)
    val regressionSlope = decimal("regression_slope", 10, 4)
    val regressionIntercept = decimal("regression_intercept", 10, 4)
    override val primaryKey = PrimaryKey(id)
}

/**
 * The inMem is useful for tests.
 */
private class AppDatabaseCore(dbName: String, inMem: Boolean?) {
    init {
        var url = "jdbc:h2:./$dbName.h2.db:$dbName;DB_CLOSE_DELAY=-1;"
        if (inMem == true) {
            url = "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;"
        }
        Database.connect(url, driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(CollectedObservations, AnalysisResults)
        }
    }

    fun getAnalysisResults(): List<ResultRow> = transaction {
        AnalysisResults.selectAll().toList()
    }

    fun getAnalysisById(id: String): ResultRow? = transaction {
        AnalysisResults.select { AnalysisResults.id eq id }.singleOrNull()
    }

    fun getAnalysisByDates(startDate: String, endDate: String, category: String): ResultRow? = transaction {
        AnalysisResults.select { (AnalysisResults.startDate eq startDate) and (AnalysisResults.endDate eq endDate) and (AnalysisResults.category1 eq category) }
            .singleOrNull()
    }

    fun saveAnalysis(
        id: String,
        startDate: String,
        endDate: String,
        category1: String,
        category2: String,
        medianCategory1: Float,
        slopeCategory1: Float,
        medianCategory2: Float,
        slopeCategory2: Float,
        regressionSlope: Float,
        regressionIntercept: Float
    ): ResultRow {
        if (medianCategory1.isNaN() || medianCategory2.isNaN()) {
            return transaction {
                AnalysisResults.insert {
                    it[AnalysisResults.id] = id
                    it[AnalysisResults.startDate] = startDate
                    it[AnalysisResults.endDate] = endDate
                    it[AnalysisResults.category1] = category1
                    it[AnalysisResults.category2] = category2
                    it[AnalysisResults.medianCategory1] = 0.toBigDecimal()
                    it[AnalysisResults.slopeCategory1] = 0.toBigDecimal()
                    it[AnalysisResults.medianCategory2] = 0.toBigDecimal()
                    it[AnalysisResults.slopeCategory2] = 0.toBigDecimal()
                    it[AnalysisResults.regressionSlope] = 0.toBigDecimal()
                    it[AnalysisResults.regressionIntercept] = 0.toBigDecimal()
                }
                AnalysisResults.select { AnalysisResults.id eq id }.single()
            }
        }
        return transaction {
            AnalysisResults.insert {
                it[AnalysisResults.id] = id
                it[AnalysisResults.startDate] = startDate
                it[AnalysisResults.endDate] = endDate
                it[AnalysisResults.category1] = category1
                it[AnalysisResults.category2] = category2
                it[AnalysisResults.medianCategory1] = medianCategory1.toBigDecimal()
                it[AnalysisResults.slopeCategory1] = slopeCategory1.toBigDecimal()
                it[AnalysisResults.medianCategory2] = medianCategory2.toBigDecimal()
                it[AnalysisResults.slopeCategory2] = slopeCategory2.toBigDecimal()
                it[AnalysisResults.regressionSlope] = regressionSlope.toBigDecimal()
                it[AnalysisResults.regressionIntercept] = regressionIntercept.toBigDecimal()
            }
            AnalysisResults.select { AnalysisResults.id eq id }.single()
        }
    }

    fun getObservations(category: String? = null, startDate: String? = null, endDate: String? = null): List<ResultRow> =
        transaction {
            var query = CollectedObservations.selectAll()

            category?.let {
                query = query.andWhere { CollectedObservations.category eq it }
            }

            startDate?.let {
                query = query.andWhere { CollectedObservations.date greaterEq it }
            }

            endDate?.let {
                query = query.andWhere { CollectedObservations.date lessEq it }
            }

            query.toList()
        }

    fun saveObservation(category: String, date: String, value: BigDecimal?): ResultRow = transaction {
        val existingObservation = CollectedObservations.select {
            (CollectedObservations.category eq category) and (CollectedObservations.date eq date)
        }.singleOrNull()

        if (existingObservation != null) {
            CollectedObservations.update({
                (CollectedObservations.category eq category) and (CollectedObservations.date eq date)
            }) {
                it[CollectedObservations.value] = value
            }
            CollectedObservations.select {
                (CollectedObservations.category eq category) and (CollectedObservations.date eq date)
            }.single()
        } else {
            CollectedObservations.insert {
                it[CollectedObservations.category] = category
                it[CollectedObservations.date] = date
                it[CollectedObservations.value] = value
            }
            CollectedObservations.select {
                (CollectedObservations.category eq category) and (CollectedObservations.date eq date)
            }.single()
        }
    }
}

class AppDatabase(dbName: String, inMem: Boolean?) {
    private val db: AppDatabaseCore

    init {
        db = AppDatabaseCore(dbName, inMem)
    }

    fun getAnalysisResults(): List<AnalysisResult> {
        return db.getAnalysisResults().map {
            AnalysisResult(
                it[AnalysisResults.id],
                it[AnalysisResults.startDate],
                it[AnalysisResults.endDate],
                it[AnalysisResults.category1],
                it[AnalysisResults.category2],
                it[AnalysisResults.medianCategory1].toFloat(),
                it[AnalysisResults.slopeCategory1].toFloat(),
                it[AnalysisResults.medianCategory2].toFloat(),
                it[AnalysisResults.slopeCategory2].toFloat(),
                it[AnalysisResults.regressionSlope].toFloat(),
                it[AnalysisResults.regressionIntercept].toFloat(),
            )
        }
    }

    fun getAnalysisById(id: String): AnalysisResult? {
        val resultRow = db.getAnalysisById(id)
        return resultRow?.let {
            AnalysisResult(
                it[AnalysisResults.id],
                it[AnalysisResults.startDate],
                it[AnalysisResults.endDate],
                it[AnalysisResults.category1],
                it[AnalysisResults.category2],
                resultRow[AnalysisResults.medianCategory1].toFloat(),
                resultRow[AnalysisResults.slopeCategory1].toFloat(),
                resultRow[AnalysisResults.medianCategory2].toFloat(),
                resultRow[AnalysisResults.slopeCategory2].toFloat(),
                resultRow[AnalysisResults.regressionSlope].toFloat(),
                resultRow[AnalysisResults.regressionIntercept].toFloat(),
            )
        }
    }

    fun getAnalysisByDates(startDate: String, endDate: String, category: String): AnalysisResult? {
        val resultRow = db.getAnalysisByDates(startDate, endDate, category)
        return resultRow?.let {
            AnalysisResult(
                it[AnalysisResults.id],
                it[AnalysisResults.startDate],
                it[AnalysisResults.endDate],
                it[AnalysisResults.category1],
                it[AnalysisResults.category2],
                resultRow[AnalysisResults.medianCategory1].toFloat(),
                resultRow[AnalysisResults.slopeCategory1].toFloat(),
                resultRow[AnalysisResults.medianCategory2].toFloat(),
                resultRow[AnalysisResults.slopeCategory2].toFloat(),
                resultRow[AnalysisResults.regressionSlope].toFloat(),
                resultRow[AnalysisResults.regressionIntercept].toFloat(),
            )
        }
    }

    fun saveAnalysis(
        id: String,
        startDate: String,
        endDate: String,
        category1: String,
        category2: String,
        medianCategory1: Float,
        slopeCategory1: Float,
        medianCategory2: Float,
        slopeCategory2: Float,
        regressionSlope: Float,
        regressionIntercept: Float
    ): AnalysisResult {
        val resultRow = db.saveAnalysis(
            id,
            startDate,
            endDate,
            category1,
            category2,
            medianCategory1,
            slopeCategory1,
            medianCategory2,
            slopeCategory2,
            regressionSlope,
            regressionIntercept
        )
        return AnalysisResult(
            resultRow[AnalysisResults.id],
            resultRow[AnalysisResults.startDate],
            resultRow[AnalysisResults.endDate],
            resultRow[AnalysisResults.category1],
            resultRow[AnalysisResults.category2],
            resultRow[AnalysisResults.medianCategory1].toFloat(),
            resultRow[AnalysisResults.slopeCategory1].toFloat(),
            resultRow[AnalysisResults.medianCategory2].toFloat(),
            resultRow[AnalysisResults.slopeCategory2].toFloat(),
            resultRow[AnalysisResults.regressionSlope].toFloat(),
            resultRow[AnalysisResults.regressionIntercept].toFloat(),
        )
    }
    
    fun getObservations(
        category: String? = null,
        startDate: String? = null,
        endDate: String? = null
    ): List<CollectedObservation> {
        return db.getObservations(category, startDate, endDate).map {
            CollectedObservation(
                it[CollectedObservations.id],
                it[CollectedObservations.category],
                it[CollectedObservations.date],
                it[CollectedObservations.value]?.toFloat()
            )
        }
    }

    fun saveObservation(category: String, date: String, value: BigDecimal?): CollectedObservation {
        val resultRow = db.saveObservation(category, date, value)
        return CollectedObservation(
            resultRow[CollectedObservations.id],
            resultRow[CollectedObservations.category],
            resultRow[CollectedObservations.date],
            resultRow[CollectedObservations.value]?.toFloat()
        )
    }
}
