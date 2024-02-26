package edu.colorado

import io.collective.start.analyzer.AnalyzeTask
import io.collective.start.analyzer.AnalyzeWorker
import io.collective.start.collector.CollectorTask
import io.collective.start.collector.CollectorWorker
import io.collective.workflow.ContinuousWorkSupervisor
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
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
import java.time.Duration
import java.util.*


fun main() {
    val logger = LoggerFactory.getLogger("main")

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    val opts = SingleAppConfig(
        System.getenv("PORT")?.toInt() ?: 8888,
        System.getenv("ZMQPORT")?.toInt() ?: 8889,
    )

    val database = initializeDatabase()
    val eventBus = ZmqRouter(opts.zmqPort)
    val fredClient = FredApiClient(System.getenv("FRED_API_KEY"))
    logger.info("got FRED with ${System.getenv("FRED_API_KEY")[0]} and ${System.getenv("FRED_API_KEY")[31]}")

    logger.info("listening on ZeroMQ...")
    eventBus.startListening()

    logger.info("starting data collector...")
    setupCollection(database, eventBus, fredClient)

    logger.info("starting data analyzer...")
    setupAnalysis(database, eventBus)

    embeddedServer(
        Netty,
        opts.port,
        watchPaths = listOf("basic-server"),
        module = { module(database, eventBus) }).start(wait = true)
}

// --- TYPES ---

data class SingleAppConfig(val port: Int, val zmqPort: Int)

@Serializable
data class SseEvent(val type: String?, val id: String?, val payload: String?)

// --- APPLICATION ---

fun Application.module(db: AppDatabase, zmqRouter: ZmqRouter) {
    val logger = LoggerFactory.getLogger(this.javaClass)

    logger.info("installing production monitoring/instrumenting endpoint...")
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    setupMonitoring(appMicrometerRegistry)

    logger.info("setting up SSE SharedFlow...")
    val sseFlow = buildSharedSseFlow(zmqRouter.fullStream).shareIn(GlobalScope, SharingStarted.Eagerly)

    logger.info("installing middleware...")
    setupMiddleware()

    logger.info("installing routes...")
    setupRoutes(sseFlow, zmqRouter, db, appMicrometerRegistry)
}

fun Application.setupMonitoring(appMicrometerRegistry: PrometheusMeterRegistry) {
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
        distributionStatisticConfig = DistributionStatisticConfig.Builder()
            .percentilesHistogram(true)
            .maximumExpectedValue(Duration.ofSeconds(20).toNanos().toDouble())
            .serviceLevelObjectives(
                Duration.ofMillis(100).toNanos().toDouble(),
                Duration.ofMillis(500).toNanos().toDouble()
            )
            .build()
        meterBinders = listOf(
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics()
        )
    }
}

fun Application.setupMiddleware() {
    install(DefaultHeaders)
    install(CallLogging)
}

fun Application.setupRoutes(
    sseFlow: SharedFlow<SseEvent>,
    zmq: ZmqRouter,
    db: AppDatabase,
    registry: PrometheusMeterRegistry
) {
    install(Routing) {
        singlePageApplication {
            useResources = true
            filesPath = "web"
            defaultPage = "index.html"
        }
        staticResources("/web", "web")
        staticResources("/images", "images")
        staticResources("/style", "style")

        get("/metrics") { call.respond(registry.scrape()) }

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
    return AppDatabase("appDb", false)
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
