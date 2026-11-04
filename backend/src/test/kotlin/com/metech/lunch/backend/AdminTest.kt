package com.metech.lunch.backend

import com.metech.lunch.backend.admin.AdminBatch
import com.metech.lunch.backend.admin.AdminOrder
import com.metech.lunch.backend.admin.AdminUser
import com.metech.lunch.backend.admin.AssignBatchRequest
import com.metech.lunch.backend.admin.CountResult
import com.metech.lunch.backend.admin.CreateUserRequest
import com.metech.lunch.backend.admin.DailyMenuLine
import com.metech.lunch.backend.admin.DailyMenuView
import com.metech.lunch.backend.admin.DateRequest
import com.metech.lunch.backend.admin.OverviewView
import com.metech.lunch.backend.admin.PrepView
import com.metech.lunch.backend.admin.RefundDecision
import com.metech.lunch.backend.admin.ReportView
import com.metech.lunch.backend.admin.SaveDailyMenuRequest
import com.metech.lunch.backend.admin.UpdateUserRequest
import com.metech.lunch.backend.payments.MockGateway
import com.metech.lunch.backend.payments.MockWebhookBody
import com.metech.lunch.core.model.ApiError
import com.metech.lunch.core.model.BatchStatus
import com.metech.lunch.core.model.DeliveryBatch
import com.metech.lunch.core.model.ErrorCodes
import com.metech.lunch.core.model.Order
import com.metech.lunch.core.model.OrderStatus
import com.metech.lunch.core.model.PaymentStatus
import com.metech.lunch.core.model.PlaceOrderLine
import com.metech.lunch.core.model.PlaceOrderRequest
import com.metech.lunch.core.model.PlaceOrderResponse
import com.metech.lunch.core.model.RefundStatus
import com.metech.lunch.core.model.UserRole
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdminTest {
    private val ownerPhone = "+919999900000"
    private val kitchenPhone = "+919999900003"
    private val partnerPhone = "+919999900001"

    private suspend fun HttpClient.paidOrder(env: TestEnv, token: String, meal: String, qty: Int, office: String = "office-tower-a", dropPoint: String? = null): Order {
        val placed = postJson<Unit>(
            "/api/orders", token,
            PlaceOrderRequest(office, dropPoint, listOf(PlaceOrderLine(meal, qty)), UUID.randomUUID().toString()),
        ).body<PlaceOrderResponse>()
        val body = Json.encodeToString(MockWebhookBody.serializer(), MockWebhookBody(UUID.randomUUID().toString(), placed.order.payment.reference!!, "success"))
        post("/api/payments/webhook/mock") {
            header(MockGateway.SIGNATURE_HEADER, env.services.mockGateway.sign(body))
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return get("/api/orders/${placed.order.id}") { bearerAuth(token) }.body()
    }

    private suspend inline fun <reified T> HttpClient.putJson(path: String, token: String, body: Any) =
        put(path) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    @Test
    fun `kitchen staff cannot reach financial, people or settings endpoints`() = apiTest { _, client ->
        val kitchen = client.login(kitchenPhone, UserRole.KITCHEN_STAFF)
        val customer = client.login("9876543210")
        for (path in listOf("/api/admin/orders", "/api/admin/refunds", "/api/admin/users?role=customer", "/api/admin/reports", "/api/admin/audit", "/api/admin/offices")) {
            assertEquals(HttpStatusCode.Forbidden, client.get(path) { bearerAuth(kitchen) }.status, path)
        }
        assertEquals(HttpStatusCode.OK, client.get("/api/admin/prep") { bearerAuth(kitchen) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/admin/prep") { bearerAuth(customer) }.status)
        // Kitchen staff see the overview without sales.
        val overview = client.get("/api/admin/overview") { bearerAuth(kitchen) }.body<OverviewView>()
        assertEquals(null, overview.salesPaise)
    }

    @Test
    fun `deactivated staff are locked out`() = apiTest { env, client ->
        val owner = client.login(ownerPhone, UserRole.OWNER)
        val kitchen = client.login(kitchenPhone, UserRole.KITCHEN_STAFF)
        val kitchenId = env.userIdByPhone(kitchenPhone, UserRole.KITCHEN_STAFF)
        client.putJson<Unit>("/api/admin/users/$kitchenId", owner, UpdateUserRequest("Kitchen Lead", active = false))
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/admin/prep") { bearerAuth(kitchen) }.status)
    }

    @Test
    fun `kitchen day - prep totals, packing, batches, assignment, partner pickup`() = apiTest { env, client ->
        val owner = client.login(ownerPhone, UserRole.OWNER)
        val kitchen = client.login(kitchenPhone, UserRole.KITCHEN_STAFF)
        val alice = client.login("9876543210")
        val bob = client.login("9876543211")
        val carol = client.login("9876543212")
        client.paidOrder(env, alice, "meal-veg-thali", 2, dropPoint = "office-tower-a-dp1")
        client.paidOrder(env, bob, "meal-chicken-biryani", 1, dropPoint = "office-tower-a-dp1")
        client.paidOrder(env, carol, "meal-veg-thali", 1, office = "office-cyber")

        // Before the cutoff the list is not final.
        val early = client.postJson<Unit>("/api/admin/prep/start", kitchen, DateRequest(TEST_DATE.toString()))
        assertEquals(ErrorCodes.INVALID_STATE, early.body<ApiError>().code)

        env.clock.setIst(TEST_DATE, 10, 5)
        val prep = client.get("/api/admin/prep") { bearerAuth(kitchen) }.body<PrepView>()
        assertEquals(4, prep.totalMeals)
        assertEquals(3, prep.mealTotals.single { it.menuItemId == "meal-veg-thali" }.quantity)
        assertEquals(2, prep.groups.size)
        assertEquals(3, prep.groups.single { it.officeId == "office-tower-a" }.mealTotals.sumOf { it.quantity })

        val started = client.postJson<Unit>("/api/admin/prep/start", kitchen, DateRequest(TEST_DATE.toString())).body<CountResult>()
        assertEquals(3, started.updated)

        val batches = client.postJson<Unit>("/api/admin/batches/generate", kitchen, DateRequest(TEST_DATE.toString())).body<List<AdminBatch>>()
        assertEquals(2, batches.size)
        assertTrue(batches.all { it.status == BatchStatus.UNASSIGNED })
        // Generating again does not duplicate anything.
        assertEquals(2, client.postJson<Unit>("/api/admin/batches/generate", kitchen, DateRequest(TEST_DATE.toString())).body<List<AdminBatch>>().size)

        val towerA = batches.single { it.officeName == "Tech Park Tower A" }
        val partnerId = env.userIdByPhone(partnerPhone, UserRole.DELIVERY_PARTNER)
        val assigned = client.putJson<Unit>(
            "/api/admin/batches/${towerA.id}/assign", owner,
            AssignBatchRequest(partnerId, "Asha", "+919999911111", coordinatorOtp = true),
        ).body<AdminBatch>()
        assertEquals(BatchStatus.ASSIGNED, assigned.status)
        assertNotNull(assigned.coordinatorOtp)

        val partner = client.login(partnerPhone, UserRole.DELIVERY_PARTNER)
        val visible = client.get("/api/partner/batches") { bearerAuth(partner) }.body<List<DeliveryBatch>>()
        assertEquals(listOf(towerA.id), visible.map { it.id }) // the unassigned Cyber Towers batch stays hidden
        assertEquals(3, visible.single().totalMeals)
        val picked = client.postJson<Unit>("/api/partner/batches/${towerA.id}/pickup", partner, com.metech.lunch.core.model.PickupConfirmation(3))
        assertEquals(HttpStatusCode.OK, picked.status)

        // Reassigning after pickup is refused.
        val reassign = client.putJson<Unit>("/api/admin/batches/${towerA.id}/assign", owner, AssignBatchRequest(partnerId))
        assertEquals(ErrorCodes.INVALID_STATE, reassign.body<ApiError>().code)
    }

    @Test
    fun `refund approval cancels the order and marks the payment refunded`() = apiTest { env, client ->
        val owner = client.login(ownerPhone, UserRole.OWNER)
        val alice = client.login("9876543210")
        val order = client.paidOrder(env, alice, "meal-veg-thali", 1)
        client.postJson<Unit>("/api/orders/${order.id}/cancel", alice, null)

        val pending = client.get("/api/admin/refunds") { bearerAuth(owner) }.body<List<AdminOrder>>()
        assertEquals(listOf(order.id), pending.map { it.id })

        val approved = client.postJson<Unit>("/api/admin/refunds/${order.id}/approve", owner, RefundDecision("ok")).body<AdminOrder>()
        assertEquals(OrderStatus.CANCELLED, approved.status)
        assertEquals(PaymentStatus.REFUNDED, approved.paymentStatus)
        assertEquals(RefundStatus.COMPLETED, approved.refundStatus)
        assertEquals(0, client.get("/api/admin/refunds") { bearerAuth(owner) }.body<List<AdminOrder>>().size)

        val again = client.postJson<Unit>("/api/admin/refunds/${order.id}/approve", owner, RefundDecision())
        assertEquals(ErrorCodes.INVALID_STATE, again.body<ApiError>().code)
    }

    @Test
    fun `refund rejection restores a cancellation request to confirmed`() = apiTest { env, client ->
        val owner = client.login(ownerPhone, UserRole.OWNER)
        val alice = client.login("9876543210")
        val order = client.paidOrder(env, alice, "meal-veg-thali", 1)
        client.postJson<Unit>("/api/orders/${order.id}/cancel", alice, null)
        val rejected = client.postJson<Unit>("/api/admin/refunds/${order.id}/reject", owner, RefundDecision("past policy")).body<AdminOrder>()
        assertEquals(OrderStatus.CONFIRMED, rejected.status)
        assertEquals(RefundStatus.REJECTED, rejected.refundStatus)
        assertEquals(PaymentStatus.VERIFIED, rejected.paymentStatus)
    }

    @Test
    fun `daily menu protects sold meals`() = apiTest { env, client ->
        val owner = client.login(ownerPhone, UserRole.OWNER)
        val alice = client.login("9876543210")
        client.paidOrder(env, alice, "meal-veg-thali", 3)
        val menu = client.get("/api/admin/daily-menu") { bearerAuth(owner) }.body<DailyMenuView>()
        assertEquals(3, menu.published.single { it.menuItemId == "meal-veg-thali" }.reserved)

        fun lines(thaliCapacity: Int?, includeThali: Boolean = true) = SaveDailyMenuRequest(
            menu.published.filter { includeThali || it.menuItemId != "meal-veg-thali" }.map {
                DailyMenuLine(it.menuItemId, it.pricePaise, if (it.menuItemId == "meal-veg-thali") thaliCapacity else it.capacity, it.available)
            },
        )
        val tooLow = client.putJson<Unit>("/api/admin/daily-menu?date=$TEST_DATE", owner, lines(2))
        assertEquals(HttpStatusCode.BadRequest, tooLow.status)
        val removed = client.putJson<Unit>("/api/admin/daily-menu?date=$TEST_DATE", owner, lines(10, includeThali = false))
        assertEquals(HttpStatusCode.BadRequest, removed.status)
        val ok = client.putJson<Unit>("/api/admin/daily-menu?date=$TEST_DATE", owner, lines(3)).body<DailyMenuView>()
        assertEquals(3, ok.published.single { it.menuItemId == "meal-veg-thali" }.capacity)
    }

    @Test
    fun `owner manages people and new partners can sign in`() = apiTest { _, client ->
        val owner = client.login(ownerPhone, UserRole.OWNER)
        val created = client.postJson<Unit>("/api/admin/users", owner, CreateUserRequest("9000000001", "Suresh", UserRole.DELIVERY_PARTNER))
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals("+919000000001", created.body<AdminUser>().phone)
        val duplicate = client.postJson<Unit>("/api/admin/users", owner, CreateUserRequest("9000000001", "Suresh", UserRole.DELIVERY_PARTNER))
        assertEquals(ErrorCodes.INVALID_STATE, duplicate.body<ApiError>().code)
        val token = client.login("9000000001", UserRole.DELIVERY_PARTNER)
        assertEquals(HttpStatusCode.OK, client.get("/api/partner/batches") { bearerAuth(token) }.status)
    }

    @Test
    fun `report and CSV export reconcile with confirmed orders`() = apiTest { env, client ->
        val owner = client.login(ownerPhone, UserRole.OWNER)
        val alice = client.login("9876543210")
        val bob = client.login("9876543211")
        client.putJson<Unit>("/api/me", alice, com.metech.lunch.core.model.UpdateProfileRequest("=HYPERLINK(\"x\")", null, "office-tower-a", null))
        client.paidOrder(env, alice, "meal-veg-thali", 2)   // 240.00
        client.paidOrder(env, bob, "meal-curd-rice", 1)     //  80.00

        val report = client.get("/api/admin/reports?from=$TEST_DATE&to=$TEST_DATE") { bearerAuth(owner) }.body<ReportView>()
        assertEquals(32000, report.totalSalesPaise)
        assertEquals(3, report.days.single().meals)
        assertEquals(2, report.days.single().orders)

        val csv = client.get("/api/admin/exports/orders.csv?from=$TEST_DATE&to=$TEST_DATE") { bearerAuth(owner) }
        assertTrue(csv.headers["Content-Disposition"]!!.contains("orders-2026-11-04.csv"))
        val lines = csv.bodyAsText().trim().lines()
        assertEquals(3, lines.size) // header + 2 orders
        assertTrue(lines.any { "\"'=HYPERLINK" in it }, "formula-looking names are neutralised")
        assertTrue(lines.any { "\"240.00\"" in it })
    }
}
