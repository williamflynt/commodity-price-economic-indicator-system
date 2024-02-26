package test.colorado

import edu.colorado.AppDatabase
import edu.colorado.ZmqRouter
import edu.colorado.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppTest {

    @Test
    fun testHtmlAtHome() = testApplication {
        application {
            module(AppDatabase("appDbTest"), ZmqRouter(7779))
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("<!doctype html>"))
    }
}
