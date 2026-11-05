package com.metech.lunch.backend

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
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

    @Test
    fun `the dashboard's own public address passes CORS behind an HTTPS proxy`() {
        val dir = Files.createTempDirectory("dashboard")
        Files.writeString(dir.resolve("index.html"), "<html>dashboard</html>")
        Files.createDirectories(dir.resolve("assets"))
        Files.writeString(dir.resolve("assets/app.js"), "console.log(1)")
        // In Azure, TLS ends at the proxy: the app sees http://<internal host>, the browser sends Origin: https://<public host>.
        val config = testConfig.copy(dashboardDir = dir.toString(), publicBaseUrl = "https://lunch.example.com", corsOrigins = listOf("http://localhost:5173"))

        testApplication {
            application { module(Services(config, TestDb.db)) }
            // Browsers send Origin with module scripts (<script type="module" crossorigin>).
            val asset = client.get("/assets/app.js") { header(HttpHeaders.Origin, "https://lunch.example.com") }
            assertEquals(HttpStatusCode.OK, asset.status)
            val foreign = client.get("/api/health") { header(HttpHeaders.Origin, "https://evil.example.com") }
            assertEquals(HttpStatusCode.Forbidden, foreign.status)
        }
    }
}
