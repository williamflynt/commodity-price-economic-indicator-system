package edu.colorado

import io.ktor.application.*
import io.ktor.freemarker.*
import io.ktor.http.*
import io.ktor.response.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object Cache {
    private val analysisResults = ConcurrentHashMap<String, String>()
    fun get(key: String): String? = analysisResults[key]

    fun set(key: String, value: String) {
        analysisResults[key] = value
    }
}

// --- HANDLERS ---

suspend fun handleRoot(call: ApplicationCall, headers: Map<String, String>) {
    call.respond(FreeMarkerContent("index.ftl", mapOf("headers" to headers)))
}

suspend fun handleListAnalysis(call: ApplicationCall, db: AppDatabase) {
    val results = db.getAnalysisResults()
    call.respond(Json.encodeToString(results))
}

suspend fun handleRequestAnalysis(call: ApplicationCall, eventBus: ZmqRouter, db: AppDatabase) {
    val start = call.parameters["startDate"]
    val end = call.parameters["endDate"]
    val category = call.parameters["category"]
    if (start == null || end == null || category == null) {
        call.respond(HttpStatusCode.BadRequest, "missing required parameters: start|end|category")
        return
    }

    val cacheKey = "$start::$end::$category"
    Cache.get(cacheKey)?.let {
        val result = db.getAnalysisById(it)
        if (result != null) {
            call.respondText(Json.encodeToString(result), status = HttpStatusCode.OK)
            return
        }
    }

    val result = db.getAnalysisByDates(start, end, category)
    if (result != null) {
        call.respondText(Json.encodeToString(result), status = HttpStatusCode.OK)
        return
    }

    val analysisId = UUID.randomUUID().toString().take(8)
    eventBus.publish("ANALYZE $analysisId id=$analysisId startDate=$start endDate=$end category=$category")
    Cache.set(cacheKey, analysisId)
    call.respondText("""{"id":"$analysisId"}""", status = HttpStatusCode.Accepted)
}

suspend fun handleAnalysisById(call: ApplicationCall, db: AppDatabase) {
    val id = call.parameters["id"]
    if (id == null) {
        call.respond(HttpStatusCode.BadRequest, "missing analysis ID")
        return
    }

    val result = db.getAnalysisById(id)
    if (result == null) {
        call.respond(HttpStatusCode.NotFound)
        return
    }
    call.respondText(Json.encodeToString(result), status = HttpStatusCode.OK)
}

suspend fun handleSse(call: ApplicationCall, sseFlow: SharedFlow<SseEvent>) {
    call.respondSse(sseFlow)
}
