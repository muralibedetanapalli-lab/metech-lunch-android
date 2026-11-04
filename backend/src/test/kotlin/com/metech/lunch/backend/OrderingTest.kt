package com.metech.lunch.backend

import com.metech.lunch.backend.payments.MockGateway
import com.metech.lunch.backend.payments.MockWebhookBody
import com.metech.lunch.core.model.ApiError
import com.metech.lunch.core.model.DailyMenu
import com.metech.lunch.core.model.ErrorCodes
import com.metech.lunch.core.model.Order
import com.metech.lunch.core.model.OrderStatus
import com.metech.lunch.core.model.PaymentStatus
import com.metech.lunch.core.model.PlaceOrderLine
import com.metech.lunch.core.model.PlaceOrderRequest
import com.metech.lunch.core.model.PlaceOrderResponse
import com.metech.lunch.core.model.RefundStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OrderingTest {

    private val office = "office-tower-a"

    private fun orderRequest(vararg lines: Pair<String, Int>, key: String = UUID.randomUUID().toString()) =
        PlaceOrderRequest(office, null, lines.map { PlaceOrderLine(it.first, it.second) }, key)

    private suspend fun HttpClient.place(token: String, req: PlaceOrderRequest): HttpResponse =
        postJson<Unit>("/api/orders", token, req)

    /** Simulates the gateway's server-to-server callback with a valid signature. */
    private suspend fun HttpClient.gatewayCallback(env: TestEnv, ref: String, success: Boolean, eventId: String = UUID.randomUUID().toString()): HttpResponse {
        val body = Json.encodeToString(MockWebhookBody.serializer(), MockWebhookBody(eventId, ref, if (success) "success" else "failed"))
        return post("/api/payments/webhook/mock") {
            header(MockGateway.SIGNATURE_HEADER, env.services.mockGateway.sign(body))
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    private suspend fun HttpClient.order(token: String, id: String): Order =
        get("/api/orders/$id") { bearerAuth(token) }.body()

    @Test
    fun `menu shows today's meals and is open before cutoff`() = apiTest { _, client ->
        val token = client.login("9876543210")
        val menu = client.get("/api/menu/today?officeId=$office") { bearerAuth(token) }.body<DailyMenu>()
        assertEquals("2026-11-04", menu.date)
        assertTrue(menu.orderingOpen)
        assertEquals(4, menu.items.size)
        assertEquals(50, menu.items.first().remainingCapacity)
    }

    @Test
    fun `order is confirmed only after a verified gateway callback`() = apiTest { env, client ->
        val token = client.login("9876543210")
        val res = client.place(token, orderRequest("meal-veg-thali" to 2))
        assertEquals(HttpStatusCode.Created, res.status)
        val placed = res.body<PlaceOrderResponse>()
        assertEquals(OrderStatus.PENDING_PAYMENT, placed.order.status)
        assertEquals(24000, placed.order.totalPaise)
        assertNotNull(placed.paymentUrl)

        val ref = placed.order.payment.reference!!
        assertEquals(HttpStatusCode.OK, client.gatewayCallback(env, ref, success = true).status)

        val order = client.order(token, placed.order.id)
        assertEquals(OrderStatus.CONFIRMED, order.status)
        assertEquals(PaymentStatus.VERIFIED, order.payment.status)
    }

    @Test
    fun `callback with a bad signature is rejected and changes nothing`() = apiTest { _, client ->
        val token = client.login("9876543210")
        val placed = client.place(token, orderRequest("meal-veg-thali" to 1)).body<PlaceOrderResponse>()
        val body = """{"eventId":"e1","gatewayRef":"${placed.order.payment.reference}","status":"success"}"""
        val res = client.post("/api/payments/webhook/mock") {
            header(MockGateway.SIGNATURE_HEADER, "forged")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.Unauthorized, res.status)
        assertEquals(OrderStatus.PENDING_PAYMENT, client.order(token, placed.order.id).status)
    }

    @Test
    fun `duplicate callbacks do not create duplicate effects`() = apiTest { env, client ->
        val token = client.login("9876543210")
        val placed = client.place(token, orderRequest("meal-veg-thali" to 1)).body<PlaceOrderResponse>()
        val ref = placed.order.payment.reference!!
        client.gatewayCallback(env, ref, true, eventId = "evt-1")
        val replay = client.gatewayCallback(env, ref, true, eventId = "evt-1").bodyAsMap()
        assertEquals("duplicate", replay["result"])
        val second = client.gatewayCallback(env, ref, true, eventId = "evt-2").bodyAsMap()
        assertEquals("already_final", second["result"])

        val orders = client.get("/api/orders") { bearerAuth(token) }.body<List<Order>>()
        assertEquals(1, orders.size)
        assertEquals(OrderStatus.CONFIRMED, orders.single().status)
    }

    @Test
    fun `same idempotency key returns the same order`() = apiTest { _, client ->
        val token = client.login("9876543210")
        val req = orderRequest("meal-veg-thali" to 1, key = "tap-once")
        val first = client.place(token, req).body<PlaceOrderResponse>()
        val second = client.place(token, req).body<PlaceOrderResponse>()
        assertEquals(first.order.id, second.order.id)
        assertEquals(1, client.get("/api/orders") { bearerAuth(token) }.body<List<Order>>().size)
    }

    @Test
    fun `server blocks orders after the 10 AM cutoff`() = apiTest { env, client ->
        val token = client.login("9876543210")
        env.clock.setIst(TEST_DATE, 10, 0)
        val res = client.place(token, orderRequest("meal-veg-thali" to 1))
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertEquals(ErrorCodes.ORDERING_CLOSED, res.body<ApiError>().code)
        val menu = client.get("/api/menu/today?officeId=$office") { bearerAuth(token) }.body<DailyMenu>()
        assertFalse(menu.orderingOpen)
    }

    @Test
    fun `payment that lands after cutoff is honoured for an order placed before it`() = apiTest { env, client ->
        val token = client.login("9876543210")
        env.clock.setIst(TEST_DATE, 9, 58)
        val placed = client.place(token, orderRequest("meal-veg-thali" to 1)).body<PlaceOrderResponse>()
        env.clock.setIst(TEST_DATE, 10, 3)
        client.gatewayCallback(env, placed.order.payment.reference!!, true)
        assertEquals(OrderStatus.CONFIRMED, client.order(token, placed.order.id).status)
    }

    @Test
    fun `capacity is enforced including unpaid orders inside the hold window`() = apiTest { env, client ->
        env.setCapacity("meal-chicken-biryani", 3)
        val alice = client.login("9876543210")
        val bob = client.login("9876543211")

        assertEquals(HttpStatusCode.Created, client.place(alice, orderRequest("meal-chicken-biryani" to 2)).status)
        val tooMany = client.place(bob, orderRequest("meal-chicken-biryani" to 2))
        assertEquals(HttpStatusCode.UnprocessableEntity, tooMany.status)
        assertEquals(ErrorCodes.OUT_OF_CAPACITY, tooMany.body<ApiError>().code)
        assertEquals(HttpStatusCode.Created, client.place(bob, orderRequest("meal-chicken-biryani" to 1)).status)
    }

    @Test
    fun `expired unpaid orders release capacity, and a late payment that no longer fits is refunded`() = apiTest { env, client ->
        env.setCapacity("meal-chicken-biryani", 1)
        val alice = client.login("9876543210")
        val bob = client.login("9876543211")

        val aliceOrder = client.place(alice, orderRequest("meal-chicken-biryani" to 1)).body<PlaceOrderResponse>()
        env.clock.setIst(TEST_DATE, 8, 50) // Alice's 15-minute hold has expired.
        val bobOrder = client.place(bob, orderRequest("meal-chicken-biryani" to 1)).body<PlaceOrderResponse>()
        client.gatewayCallback(env, bobOrder.order.payment.reference!!, true)

        // Alice finally pays, but the last meal is gone.
        client.gatewayCallback(env, aliceOrder.order.payment.reference!!, true)
        val late = client.order(alice, aliceOrder.order.id)
        assertEquals(OrderStatus.CANCELLED, late.status)
        assertEquals(PaymentStatus.VERIFIED, late.payment.status)
        assertEquals(RefundStatus.REQUESTED, late.payment.refundStatus)
        assertEquals(OrderStatus.CONFIRMED, client.order(bob, bobOrder.order.id).status)
    }

    @Test
    fun `unknown office is not serviceable`() = apiTest { _, client ->
        val token = client.login("9876543210")
        val res = client.place(token, orderRequest("meal-veg-thali" to 1).copy(officeId = "nowhere"))
        assertEquals(ErrorCodes.OFFICE_NOT_SERVICEABLE, res.body<ApiError>().code)
    }

    @Test
    fun `confirmed order can be cancelled before cutoff, which requests a refund`() = apiTest { env, client ->
        val token = client.login("9876543210")
        val placed = client.place(token, orderRequest("meal-veg-thali" to 1)).body<PlaceOrderResponse>()
        client.gatewayCallback(env, placed.order.payment.reference!!, true)
        val cancelled = client.postJson<Unit>("/api/orders/${placed.order.id}/cancel", token, null).body<Order>()
        assertEquals(OrderStatus.CANCEL_REQUESTED, cancelled.status)
        assertEquals(RefundStatus.REQUESTED, cancelled.payment.refundStatus)
    }

    @Test
    fun `confirmed order cannot be cancelled after cutoff`() = apiTest { env, client ->
        val token = client.login("9876543210")
        val placed = client.place(token, orderRequest("meal-veg-thali" to 1)).body<PlaceOrderResponse>()
        client.gatewayCallback(env, placed.order.payment.reference!!, true)
        env.clock.setIst(TEST_DATE, 10, 30)
        val res = client.postJson<Unit>("/api/orders/${placed.order.id}/cancel", token, null)
        assertEquals(ErrorCodes.CANNOT_CANCEL, res.body<ApiError>().code)
    }

    @Test
    fun `customers cannot see each other's orders`() = apiTest { _, client ->
        val alice = client.login("9876543210")
        val bob = client.login("9876543211")
        val placed = client.place(alice, orderRequest("meal-veg-thali" to 1)).body<PlaceOrderResponse>()
        assertEquals(HttpStatusCode.NotFound, client.get("/api/orders/${placed.order.id}") { bearerAuth(bob) }.status)
    }
}

suspend fun HttpResponse.bodyAsMap(): Map<String, String> = body()
