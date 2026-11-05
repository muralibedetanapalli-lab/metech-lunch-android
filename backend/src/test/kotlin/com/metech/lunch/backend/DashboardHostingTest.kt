package com.metech.lunch.backend

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DashboardHostingTest {

    @Test
    fun `dashboard files and routes are served, unknown API paths stay JSON 404s`() {
        val dir = Files.createTempDirectory("dashboard")
        Files.writeString(dir.resolve("index.html"), "<html>dashboard</html>")
        Files.createDirectories(dir.resolve("assets"))
        Files.writeString(dir.resolve("assets/app.js"), "console.log(1)")
        val services = Services(testConfig.copy(dashboardDir = dir.toString()), TestDb.db)

        testApplication {
            application { module(services) }
            assertEquals("<html>dashboard</html>", client.get("/").bodyAsText())
            assertEquals("console.log(1)", client.get("/assets/app.js").bodyAsText())
            // A dashboard page opened directly gets index.html; the dashboard's router takes it from there.
            assertEquals("<html>dashboard</html>", client.get("/orders").bodyAsText())

            assertEquals(HttpStatusCode.OK, client.get("/api/health").status)
            val missing = client.get("/api/no-such-thing")
            assertEquals(HttpStatusCode.NotFound, missing.status)
            assertTrue("NOT_FOUND" in missing.bodyAsText())
        }
    }
}
