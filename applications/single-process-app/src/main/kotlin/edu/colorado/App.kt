package edu.colorado

import freemarker.cache.ClassTemplateLoader
import io.collective.start.analyzer.AnalyzeTask
import io.collective.start.analyzer.AnalyzeWorker
import io.collective.start.collector.CollectorTask
import io.collective.start.collector.CollectorWorker
import io.collective.workflow.ContinuousWorkSupervisor
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.freemarker.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.*

// TODO: Build -> vite -> resources
// TODO: Serve the UI from base /
// TODO: Host this thing somewhere (set up github, secrets, push it)

fun main() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val opts = SingleAppConfig(
        System.getenv("PORT")?.toInt() ?: 8888,
        System.getenv("ZMQPORT")?.toInt() ?: 8889,
    )
    val database = initializeDatabase()
    val eventBus = ZmqRouter(opts.zmqPort)
    val fredClient = FredApiClient(System.getenv("FRED_API_KEY"))

    embeddedServer(
        Netty,
        opts.port,
        watchPaths = listOf("basic-server"),
        module = { module(database, eventBus, fredClient) }).start()
}

// --- TYPES ---

data class SingleAppConfig(val port: Int, val zmqPort: Int)

@Serializable
data class SseEvent(val type: String?, val id: String?, val payload: String?)

// --- APPLICATION ---

fun Application.module(db: AppDatabase, zmqRouter: ZmqRouter, client: FredApiClient) {
    val logger = LoggerFactory.getLogger(this.javaClass)

    logger.info("listening on ZeroMQ...")
    zmqRouter.startListening()

    logger.info("setting up SSE SharedFlow...")
    val sseFlow = buildSharedSseFlow(zmqRouter.fullStream).shareIn(GlobalScope, SharingStarted.Eagerly)

    logger.info("installing middleware...")
    setupMiddleware()

    logger.info("installing routes...")
    setupRoutes(sseFlow, zmqRouter, db)

    logger.info("starting data collector...")
    setupCollection(db, zmqRouter, client)

    logger.info("starting data analyzer...")
    setupAnalysis(db, zmqRouter)
}

fun Application.setupMiddleware() {
    install(DefaultHeaders)
    install(CallLogging)
    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(this::class.java.classLoader, "templates")
    }
}

fun Application.setupRoutes(sseFlow: SharedFlow<SseEvent>, zmq: ZmqRouter, db: AppDatabase) {
    install(Routing) {
        get("/") { handleRoot(call, headers()) }
        static("images") { resources("images") }
        static("style") { resources("style") }
        get("/analysis") { handleListAnalysis(call, db) }
        get("/analysis/{id}") { handleAnalysisById(call, db) }
        get("/analysis/{category}/{startDate}/{endDate}") { handleRequestAnalysis(call, zmq, db) }
        get("/sse") { handleSse(call, sseFlow) }
    }
}

suspend fun ApplicationCall.respondSse(eventFlow: Flow<SseEvent>) {
    response.cacheControl(CacheControl.NoCache(null))
    try {
        respondBytesWriter(contentType = ContentType.Text.EventStream) {
            val initMsg = """{ "type": "HELO", "id": "connInit", "payload": "hello" }"""
            writeStringUtf8("data: $initMsg\n\n")
            flush()

            eventFlow.collect { event ->
                val jsonString = Json.encodeToString(event)
                writeStringUtf8("data: $jsonString\n\n")
                flush()
            }
        }
    } catch (e: CancellationException) {
        println("SSE connection was closed by the client.")
    }
}

internal fun PipelineContext<Unit, ApplicationCall>.headers(): MutableMap<String, String> {
    val headers = mutableMapOf<String, String>()
    call.request.headers.entries().forEach { entry ->
        headers[entry.key] = entry.value.joinToString()
    }
    return headers
}

// --- HELPERS ---

@OptIn(ObsoleteCoroutinesApi::class)
fun buildSharedSseFlow(zmqChan: Channel<Message>): Flow<SseEvent> {
    val tickerChannel = ticker(delayMillis = 5000, initialDelayMillis = 5000, mode = TickerMode.FIXED_DELAY)
    return flow {
        while (true) {
            select {
                zmqChan.onReceive { message ->
                    emit(SseEvent(message.type, message.id, Json.encodeToString(message.payload)))
                }
                tickerChannel.onReceive {
                    emit(SseEvent("HEARTBEAT", null, "{}"))
                }
            }
        }
    }
}

fun initializeDatabase(): AppDatabase {
    return AppDatabase("appDb")
}

@OptIn(ExperimentalCoroutinesApi::class)
fun setupCollection(db: AppDatabase, zmq: ZmqRouter, client: FredApiClient): ReceiveChannel<CollectorTask> {
    val workChan = zmq.registerChannel("COLLECT", Channel())
    val transformedWorkChan = GlobalScope.produce {
        workChan.consumeEach { message ->
            send(
                CollectorTask(
                    message.id,
                    message.payload["startDate"] as String,
                    message.payload["endDate"] as String,
                    message.payload["category"] as String
                )
            )
        }
    }

    val c = ContinuousWorkSupervisor(transformedWorkChan, mutableListOf(CollectorWorker(db, zmq, client)))
    c.start()
    return transformedWorkChan
}

@OptIn(ExperimentalCoroutinesApi::class)
fun setupAnalysis(db: AppDatabase, zmq: ZmqRouter): ReceiveChannel<AnalyzeTask> {
    val workChan = zmq.registerChannel("ANALYZE", Channel())
    val transformedWorkChan = GlobalScope.produce {
        workChan.consumeEach { message ->
            send(
                AnalyzeTask(
                    message.id,
                    message.payload["startDate"] as String,
                    message.payload["endDate"] as String,
                    message.payload["category"] as String
                )
            )
        }
    }
    val collectDoneChan = zmq.registerChannel("COLLECT-DONE", Channel())
    val txCollectDoneChan = GlobalScope.produce {
        collectDoneChan.consumeEach { message ->
            send(
                AnalyzeTask(
                    message.id,
                    message.payload["startDate"] as String,
                    message.payload["endDate"] as String,
                    message.payload["category"] as String
                )
            )
        }
    }
    val workers = mutableListOf(AnalyzeWorker(db, zmq))
    val c1 = ContinuousWorkSupervisor(transformedWorkChan, workers)
    val c2 = ContinuousWorkSupervisor(txCollectDoneChan, workers)
    c1.start()
    c2.start()
    return transformedWorkChan
}
