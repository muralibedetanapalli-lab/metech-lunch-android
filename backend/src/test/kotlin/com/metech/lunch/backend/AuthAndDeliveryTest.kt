package com.metech.lunch.backend

import com.metech.lunch.backend.common.nowUtc
import com.metech.lunch.backend.db.Users
import com.metech.lunch.backend.payments.MockGateway
import org.jetbrains.exposed.sql.insert
import com.metech.lunch.backend.payments.MockWebhookBody
import com.metech.lunch.core.model.ApiError
import com.metech.lunch.core.model.AuthTokens
import com.metech.lunch.core.model.BatchStatus
import com.metech.lunch.core.model.DeliveryBatch
import com.metech.lunch.core.model.DeliveryIssueReport
import com.metech.lunch.core.model.ErrorCodes
import com.metech.lunch.core.model.HandoverConfirmation
import com.metech.lunch.core.model.Order
import com.metech.lunch.core.model.OrderStatus
import com.metech.lunch.core.model.OtpRequest
import com.metech.lunch.core.model.OtpVerifyRequest
import com.metech.lunch.core.model.PickupConfirmation
import com.metech.lunch.core.model.PlaceOrderLine
import com.metech.lunch.core.model.PlaceOrderRequest
import com.metech.lunch.core.model.PlaceOrderResponse
import com.metech.lunch.core.model.RefreshRequest
import com.metech.lunch.core.model.UserProfile
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
import kotlin.test.assertNotEquals

class AuthAndDeliveryTest {

    private val partnerPhone = "+919999900001"

    @Test
    fun `wrong OTP is rejected`() = apiTest { _, client ->
        client.postJson<Unit>("/api/auth/otp/request", null, OtpRequest("9876543210", UserRole.CUSTOMER))
        val res = client.postJson<Unit>("/api/auth/otp/verify", null, OtpVerifyRequest("9876543210", "000000", UserRole.CUSTOMER))
        assertEquals(HttpStatusCode.Unauthorized, res.status)
        assertEquals(ErrorCodes.INVALID_OTP, res.body<ApiError>().code)
    }

    @Test
    fun `unknown number cannot sign in as a delivery partner`() = apiTest { _, client ->
        client.postJson<Unit>("/api/auth/otp/request", null, OtpRequest("9876543210", UserRole.DELIVERY_PARTNER))
        val res = client.postJson<Unit>("/api/auth/otp/verify", null, OtpVerifyRequest("9876543210", "123456", UserRole.DELIVERY_PARTNER))
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `refresh rotates the token and the old one stops working`() = apiTest { _, client ->
        client.postJson<Unit>("/api/auth/otp/request", null, OtpRequest("9876543210", UserRole.CUSTOMER))
        val tokens = client.postJson<Unit>("/api/auth/otp/verify", null, OtpVerifyRequest("9876543210", "123456", UserRole.CUSTOMER))
            .body<AuthTokens>()
        val rotated = client.postJson<Unit>("/api/auth/refresh", null, RefreshRequest(tokens.refreshToken)).body<AuthTokens>()
        assertNotEquals(tokens.refreshToken, rotated.refreshToken)
        val reuse = client.postJson<Unit>("/api/auth/refresh", null, RefreshRequest(tokens.refreshToken))
        assertEquals(HttpStatusCode.Unauthorized, reuse.status)
        val me = client.get("/api/me") { bearerAuth(rotated.accessToken) }.body<UserProfile>()
        assertEquals("+919876543210", me.phone)
    }

    @Test
    fun `roles cannot use each other's endpoints`() = apiTest { _, client ->
        val customer = client.login("9876543210")
        val partner = client.login(partnerPhone, UserRole.DELIVERY_PARTNER)
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/partner/batches") { bearerAuth(customer) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/orders") { bearerAuth(partner) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/orders").status)
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

    @Test
    fun `partner completes a batch with pickup count and handover OTPs`() = apiTest { env, client ->
        val alice = client.login("9876543210")
        val bob = client.login("9876543211")
        val a = client.paidOrder(env, alice, 2)
        val b = client.paidOrder(env, bob, 1)
        val partnerId = env.userIdByPhone(partnerPhone, UserRole.DELIVERY_PARTNER)
        val batchId = env.createBatch(partnerId, "office-tower-a", listOf(a.id, b.id))
        val partner = client.login(partnerPhone, UserRole.DELIVERY_PARTNER)

        val batches = client.get("/api/partner/batches") { bearerAuth(partner) }.body<List<DeliveryBatch>>()
        assertEquals(3, batches.single().totalMeals)

        val wrongCount = client.postJson<Unit>("/api/partner/batches/$batchId/pickup", partner, PickupConfirmation(2))
        assertEquals(ErrorCodes.PICKUP_COUNT_MISMATCH, wrongCount.body<ApiError>().code)
        val picked = client.postJson<Unit>("/api/partner/batches/$batchId/pickup", partner, PickupConfirmation(3)).body<DeliveryBatch>()
        assertEquals(BatchStatus.PICKED_UP, picked.status)

        val badOtp = client.postJson<Unit>("/api/partner/batches/$batchId/handover", partner, HandoverConfirmation(listOf(a.id), if (a.deliveryOtp == "0000") "1111" else "0000"))
        assertEquals(ErrorCodes.INVALID_HANDOVER_OTP, badOtp.body<ApiError>().code)

        val afterA = client.postJson<Unit>("/api/partner/batches/$batchId/handover", partner, HandoverConfirmation(listOf(a.id), a.deliveryOtp))
            .body<DeliveryBatch>()
        assertEquals(BatchStatus.IN_TRANSIT, afterA.status)

        // Bob is away: partial handover stays visible for follow-up.
        val afterIssue = client.postJson<Unit>("/api/partner/batches/$batchId/issue", partner, DeliveryIssueReport(listOf(b.id), "Customer not at desk", partial = false))
            .body<DeliveryBatch>()
        assertEquals(BatchStatus.PARTIALLY_DELIVERED, afterIssue.status)
        assertEquals(OrderStatus.DELIVERED, client.get("/api/orders/${a.id}") { bearerAuth(alice) }.body<Order>().status)
        assertEquals(OrderStatus.DELIVERY_FAILED, client.get("/api/orders/${b.id}") { bearerAuth(bob) }.body<Order>().status)
    }

    @Test
    fun `coordinator OTP hands over the whole batch`() = apiTest { env, client ->
        val alice = client.login("9876543210")
        val bob = client.login("9876543211")
        val a = client.paidOrder(env, alice, 1)
        val b = client.paidOrder(env, bob, 1)
        val partnerId = env.userIdByPhone(partnerPhone, UserRole.DELIVERY_PARTNER)
        val batchId = env.createBatch(partnerId, "office-tower-a", listOf(a.id, b.id), coordinatorOtp = "4321")
        val partner = client.login(partnerPhone, UserRole.DELIVERY_PARTNER)

        client.postJson<Unit>("/api/partner/batches/$batchId/pickup", partner, PickupConfirmation(2))
        val done = client.postJson<Unit>("/api/partner/batches/$batchId/handover", partner, HandoverConfirmation(listOf(a.id, b.id), "4321"))
            .body<DeliveryBatch>()
        assertEquals(BatchStatus.DELIVERED, done.status)
    }

    @Test
    fun `partner cannot see another partner's batch`() = apiTest { env, client ->
        val alice = client.login("9876543210")
        val a = client.paidOrder(env, alice, 1)
        val partnerId = env.userIdByPhone(partnerPhone, UserRole.DELIVERY_PARTNER)
        env.db.txBlocking {
            Users.insert {
                it[id] = UUID.randomUUID().toString()
                it[phone] = "+919999900002"
                it[role] = "delivery_partner"
                it[name] = "Other"
                it[active] = true
                it[createdAt] = env.clock.nowUtc()
            }
        }
        val batchId = env.createBatch(partnerId, "office-tower-a", listOf(a.id))
        val other = client.login("+919999900002", UserRole.DELIVERY_PARTNER)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/partner/batches/$batchId") { bearerAuth(other) }.status)
        assertEquals(0, client.get("/api/partner/batches") { bearerAuth(other) }.body<List<DeliveryBatch>>().size)
    }
}
