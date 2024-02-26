package test.colorado

import edu.colorado.AppDatabase
import edu.colorado.ZmqRouter
import edu.colorado.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


class AppTest {

    private lateinit var db: AppDatabase
    private lateinit var router: ZmqRouter

    @Before
    fun setup() {
        db = AppDatabase(generateRandomAsciiString(10), true)
        router = ZmqRouter(getFreePortAbove16000())
    }

    @Test
    fun testHtmlAtHome() = testApplication {
        application {
            module(db, router)
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("<!doctype html>"))
    }

    @Test
    fun testHandleListAnalysis() = testApplication {
        application {
            module(db, router)
        }
        val response = client.get("/analysis")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testHandleRequestAnalysis() = testApplication {
        application {
            module(db, router)
        }
        val response = client.get("/analysis/test123/2022-01-01/2022-12-31")
        assertEquals(HttpStatusCode.Accepted, response.status)
    }

    @Test
    fun testHandleAnalysisById() = testApplication {
        application {
            module(db, router)
        }
        // Insert a test analysis result into the database.
        val analysisId = "testId"
        db.saveAnalysis(
            analysisId,
            "2022-01-01",
            "2022-12-31",
            "test",
            "test",
            0f,
            0f,
            0f,
            0f,
            0f,
            0f
        )
        val response = client.get("/analysis/$analysisId")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testAppDatabase() = runBlocking {
        // Test saving and retrieving observations.
        val observation = db.saveObservation("test", "2022-01-01", null)
        assertEquals("test", observation.category)
        assertEquals("2022-01-01", observation.date)
        assertNull(observation.value)

        val retrievedObservation = db.getObservations("test", "2022-01-01", "2022-12-31").firstOrNull()
        assertNotNull(retrievedObservation)
        assertEquals(observation.id, retrievedObservation.id)

        // Test saving and retrieving analysis results.
        val analysis = db.saveAnalysis(
            "testId",
            "2022-01-01",
            "2022-12-31",
            "test",
            "test",
            0f,
            0f,
            0f,
            0f,
            0f,
            0f
        )
        assertEquals("testId", analysis.id)

        val retrievedAnalysis = db.getAnalysisById("testId")
        assertNotNull(retrievedAnalysis)
        assertEquals(analysis.id, retrievedAnalysis.id)
    }
}


fun generateRandomAsciiString(length: Int): String {
    val asciiStart = 97
    val asciiEnd = 122
    return (1..length)
        .map { Random.nextInt(asciiStart, asciiEnd).toChar() }
        .joinToString("")
}

/**
 * I'm not super proud of this but I don't know another way.
 */
fun getFreePortAbove16000(): Int {
    for (port in 16001..65535) {
        try {
            ServerSocket(port).use {
                return port
            }
        } catch (ex: Exception) {
            // Try again!!
        }
    }
    throw RuntimeException("no free port found")
}