package com.metech.lunch.backend

import com.metech.lunch.core.model.OtpRequest
import com.metech.lunch.core.model.OtpRequestResponse
import com.metech.lunch.core.model.OtpVerifyRequest
import com.metech.lunch.core.model.UserRole
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OtpOnScreenTest {

    @BeforeTest
    fun reset() = TestDb.reset()

    @Test
    fun `with codes on screen each request gets a fresh random code that signs in`() {
        val config = AppConfig.fromEnv(mapOf("SHOW_OTP_ON_SCREEN" to "true", "JWT_SECRET" to "t"))
        assertTrue(config.showOtpOnScreen)
        assertNull(config.devFixedOtp)

        testApplication {
            application { module(Services(config, TestDb.db)) }
            val client = createClient { install(ContentNegotiation) { json(apiJson) } }
            suspend fun request(role: UserRole = UserRole.CUSTOMER, phone: String = "9876500001") =
                client.post("/api/auth/otp/request") { contentType(ContentType.Application.Json); setBody(OtpRequest(phone, role)) }
            suspend fun verify(code: String) =
                client.post("/api/auth/otp/verify") { contentType(ContentType.Application.Json); setBody(OtpVerifyRequest("9876500001", code, UserRole.CUSTOMER)) }

            val first = request().body<OtpRequestResponse>().displayCode
            val second = request().body<OtpRequestResponse>().displayCode
            assertNotNull(first); assertNotNull(second)
            assertTrue(Regex("\\d{6}").matches(second))

            if (second != "123456") assertEquals(HttpStatusCode.Unauthorized, verify("123456").status)
            assertEquals(HttpStatusCode.OK, verify(second).status)

            // Unknown staff numbers are told plainly instead of getting a code.
            val unknownOwner = request(UserRole.OWNER, "9000000001")
            assertEquals(HttpStatusCode.NotFound, unknownOwner.status)
            assertTrue("isn't registered" in unknownOwner.bodyAsText())
        }
    }

    @Test
    fun `codes are never shown in production or by default`() {
        assertEquals(false, AppConfig.fromEnv(emptyMap()).showOtpOnScreen)
        val prod = mapOf(
            "APP_ENV" to "production", "SHOW_OTP_ON_SCREEN" to "true",
            "DATABASE_URL" to "jdbc:postgresql://h/db", "DATABASE_USER" to "u", "DATABASE_PASSWORD" to "p", "JWT_SECRET" to "s",
        )
        assertEquals(false, AppConfig.fromEnv(prod).showOtpOnScreen)
    }

    @Test
    fun `without the setting the reply carries no code`() {
        testApplication {
            application { module(Services(testConfig, TestDb.db)) }
            val client = createClient { install(ContentNegotiation) { json(apiJson) } }
            val reply = client.post("/api/auth/otp/request") {
                contentType(ContentType.Application.Json); setBody(OtpRequest("9876500002", UserRole.CUSTOMER))
            }
            assertEquals(HttpStatusCode.OK, reply.status)
            assertNull(reply.body<OtpRequestResponse>().displayCode)
        }
    }
}
