package test.colorado

import edu.colorado.AppDatabase
import edu.colorado.FredApiClient
import edu.colorado.ZmqRouter
import edu.colorado.module
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppTest {

    @Test
    fun testEmptyHome() = testApp {
        handleRequest(HttpMethod.Get, "/").apply {
            assertEquals(200, response.status()?.value)
            assertTrue(response.content!!.contains("An example application using Kotlin and Ktor"))
        }
    }

    private fun testApp(callback: TestApplicationEngine.() -> Unit) {
        withTestApplication({ module(initializeDatabase(), ZmqRouter(7779), FredApiClient("test")) }) { callback() }
    }
}

fun initializeDatabase(): AppDatabase {
    return AppDatabase("appTestDb")
}
