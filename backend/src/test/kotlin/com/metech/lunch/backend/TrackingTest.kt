package com.metech.lunch.backend

import com.metech.lunch.backend.admin.OrderTracking
import com.metech.lunch.backend.payments.MockGateway
import com.metech.lunch.backend.payments.MockWebhookBody
import com.metech.lunch.core.model.HandoverConfirmation
import com.metech.lunch.core.model.LocationAck
import com.metech.lunch.core.model.LocationUpdate
import com.metech.lunch.core.model.Order
import com.metech.lunch.core.model.PickupConfirmation
import com.metech.lunch.core.model.PlaceOrderLine
import com.metech.lunch.core.model.PlaceOrderRequest
import com.metech.lunch.core.model.PlaceOrderResponse
import com.metech.lunch.core.model.UserRole
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackingTest {

    private val partnerPhone = "+919999900001"
    private val ownerPhone = "+919999900000"

    @Test
    fun `owner sees the partner's live location only while the order is on the road`() = apiTest { env, client ->
        val customer = client.login("9876543210")
        val order = client.paidOrder(env, customer, 1)
        val partnerId = env.userIdByPhone(partnerPhone, UserRole.DELIVERY_PARTNER)
        val batchId = env.createBatch(partnerId, "office-tower-a", listOf(order.id))
        val partner = client.login(partnerPhone, UserRole.DELIVERY_PARTNER)
        val owner = client.login(ownerPhone, UserRole.OWNER)

        suspend fun send(lat: Double, lng: Double) =
            client.postJson<Unit>("/api/partner/location", partner, LocationUpdate(lat, lng, accuracyMeters = 8f)).body<LocationAck>()
        suspend fun tracking() =
            client.get("/api/admin/orders/${order.id}/tracking") { bearerAuth(owner) }.body<OrderTracking>()

        // Before pickup nothing is stored and the app is told not to share.
        assertFalse(send(17.44, 78.38).tracking)
        assertFalse(tracking().live)

        client.postJson<Unit>("/api/partner/batches/$batchId/pickup", partner, PickupConfirmation(1))
        env.clock.now = env.clock.now.plusSeconds(30)
        assertTrue(send(17.4400, 78.3800).tracking)
        env.clock.now = env.clock.now.plusSeconds(30)
        assertTrue(send(17.4450, 78.3790).tracking)

        val live = tracking()
        assertTrue(live.live)
        assertEquals("Ravi (partner)", live.batch?.partnerName)
        assertEquals(2, live.trail.size)
        assertEquals(17.4450, live.location?.latitude)
        assertEquals(17.4400, live.trail.first().latitude)

        // Junk coordinates are refused.
        assertEquals(HttpStatusCode.BadRequest, client.postJson<Unit>("/api/partner/location", partner, LocationUpdate(123.0, 78.0)).status)

        // After the handover the map goes away and the app is told to stop.
        client.postJson<Unit>("/api/partner/batches/$batchId/handover", partner, HandoverConfirmation(listOf(order.id), order.deliveryOtp))
        assertFalse(send(17.45, 78.37).tracking)
        val done = tracking()
        assertFalse(done.live)
        assertNull(done.location)
    }

    @Test
    fun `customers and partners cannot read tracking`() = apiTest { env, client ->
        val customer = client.login("9876543210")
        val order = client.paidOrder(env, customer, 1)
        val partner = client.login(partnerPhone, UserRole.DELIVERY_PARTNER)
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/admin/orders/${order.id}/tracking") { bearerAuth(customer) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/admin/orders/${order.id}/tracking") { bearerAuth(partner) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.postJson<Unit>("/api/partner/location", customer, LocationUpdate(17.4, 78.3)).status)
    }

    private suspend fun HttpClient.paidOrder(env: TestEnv, token: String, qty: Int): Order {
        val placed = postJson<Unit>(
            "/api/orders", token,
            PlaceOrderRequest("office-tower-a", null, listOf(PlaceOrderLine("meal-veg-thali", qty)), UUID.randomUUID().toString()),
        ).body<PlaceOrderResponse>()
        val body = Json.encodeToString(MockWebhookBody.serializer(), MockWebhookBody(UUID.randomUUID().toString(), placed.order.payment.reference!!, "success"))
        post("/api/payments/webhook/mock") {
            header(MockGateway.SIGNATURE_HEADER, env.services.mockGateway.sign(body))
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return get("/api/orders/${placed.order.id}") { bearerAuth(token) }.body()
    }
}
