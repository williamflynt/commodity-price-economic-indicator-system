package edu.colorado

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

@Serializable
data class FredResponse(
    val realtime_start: String,
    val realtime_end: String,
    val observation_start: String,
    val observation_end: String,
    val units: String,
    val output_type: Int,
    val file_type: String,
    val order_by: String,
    val sort_order: String,
    val count: Int,
    val offset: Int,
    val limit: Int,
    val observations: List<Observation>
)


/**
 *         {
 *             "realtime_start": "2013-08-14",
 *             "realtime_end": "2013-08-14",
 *             "date": "1943-01-01",
 *             "value": "2081.2"
 *         }
 */
@Serializable
data class Observation(
    val realtime_start: String,
    val realtime_end: String,
    val date: String,
    val value: String
)


/**
 * https://fred.stlouisfed.org/docs/api/fred/series_observations.html
 */
class FredApiClient(private val apiKey: String) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    private val baseUrl = "https://api.stlouisfed.org/fred/series/observations"

    suspend fun getSeriesObservations(
        seriesId: String,
        observationStart: String,
        observationEnd: String
    ): FredResponse {
        val response = client.get(baseUrl) {
            parameter("series_id", seriesId)
            parameter("observation_start", observationStart)
            parameter("observation_end", observationEnd)
            parameter("api_key", apiKey)
            parameter("file_type", "json")
        }
        if (response.status != HttpStatusCode.OK || response.bodyAsText().contains("error_code")) {
            logger.warn("got FRED response with status ${response.status}")
            logger.warn(response.bodyAsText())
        }
        return response.body()
    }
}
