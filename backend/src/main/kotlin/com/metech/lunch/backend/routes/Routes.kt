package com.metech.lunch.backend.routes

import com.metech.lunch.backend.AuthProviders
import com.metech.lunch.backend.Services
import com.metech.lunch.backend.common.ApiException
import com.metech.lunch.backend.common.badRequest
import com.metech.lunch.backend.common.notFound
import com.metech.lunch.backend.common.newId
import com.metech.lunch.backend.common.nowUtc
import com.metech.lunch.backend.common.rejected
import com.metech.lunch.backend.common.wireEnum
import com.metech.lunch.backend.db.DropPoints
import com.metech.lunch.backend.db.Offices
import com.metech.lunch.backend.db.Payments
import com.metech.lunch.backend.db.Users
import com.metech.lunch.backend.auth.toProfile
import com.metech.lunch.backend.payments.GatewayEvent
import com.metech.lunch.core.model.BatchStatus
import com.metech.lunch.core.model.DeliveryIssueReport
import com.metech.lunch.core.model.ErrorCodes
import com.metech.lunch.core.model.HandoverConfirmation
import com.metech.lunch.core.model.LocationUpdate
import com.metech.lunch.core.model.OtpRequest
import com.metech.lunch.core.model.OtpRequestResponse
import com.metech.lunch.core.model.OtpVerifyRequest
import com.metech.lunch.core.model.PickupConfirmation
import com.metech.lunch.core.model.PlaceOrderRequest
import com.metech.lunch.core.model.RefreshRequest
import com.metech.lunch.core.model.UpdateProfileRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.util.toMap
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

private val ApplicationCall.userId: String
    get() = principal<JWTPrincipal>()!!.payload.subject

private fun ApplicationCall.pathParam(name: String): String =
    parameters[name] ?: badRequest("Missing $name")

fun Route.authRoutes(s: Services) {
    route("/auth") {
        post("/otp/request") {
            val body = call.receive<OtpRequest>()
            // Always 200 + JSON; older app builds ignore the body.
            call.respond(OtpRequestResponse(displayCode = s.auth.requestOtp(body.phone, body.role)))
        }
        post("/otp/verify") {
            val body = call.receive<OtpVerifyRequest>()
            call.respond(s.auth.verifyOtp(body.phone, body.otp, body.role))
        }
        post("/refresh") {
            call.respond(s.auth.refresh(call.receive<RefreshRequest>().refreshToken))
        }
    }
    authenticate(AuthProviders.ANY) {
        get("/me") { call.respond(s.auth.profile(call.userId)) }
    }
}

fun Route.customerRoutes(s: Services) {
    authenticate(AuthProviders.CUSTOMER) {
        put("/me") {
            val body = call.receive<UpdateProfileRequest>()
            if (body.name.isBlank() || body.name.length > 100) badRequest("Enter your name")
            val profile = s.db.tx {
                body.defaultOfficeId?.let { officeId ->
                    Offices.selectAll().where { (Offices.id eq officeId) and (Offices.active eq true) }.singleOrNull()
                        ?: rejected(ErrorCodes.OFFICE_NOT_SERVICEABLE, "This office is not serviceable")
                }
                body.defaultDropPointId?.let { dpId ->
                    DropPoints.selectAll().where { (DropPoints.id eq dpId) and (DropPoints.officeId eq (body.defaultOfficeId ?: "")) }
                        .singleOrNull() ?: badRequest("The drop point does not belong to the office")
                }
                Users.update({ Users.id eq call.userId }) {
                    it[name] = body.name.trim()
                    it[email] = body.email?.trim()?.ifEmpty { null }
                    it[defaultOfficeId] = body.defaultOfficeId
                    it[defaultDropPointId] = body.defaultDropPointId
                }
                Users.selectAll().where { Users.id eq call.userId }.single().toProfile()
            }
            call.respond(profile)
        }
        get("/offices") { call.respond(s.menu.offices()) }
        get("/menu/today") {
            val officeId = call.request.queryParameters["officeId"] ?: badRequest("officeId is required")
            call.respond(s.menu.todaysMenu(officeId))
        }
        route("/orders") {
            post {
                call.respond(HttpStatusCode.Created, s.orders.placeOrder(call.userId, call.receive<PlaceOrderRequest>()))
            }
            get { call.respond(s.orders.myOrders(call.userId)) }
            get("/{id}") { call.respond(s.orders.order(call.userId, call.pathParam("id"))) }
            post("/{id}/cancel") { call.respond(s.orders.requestCancellation(call.userId, call.pathParam("id"))) }
        }
    }
}

fun Route.partnerRoutes(s: Services) {
    authenticate(AuthProviders.PARTNER) {
        // GPS fixes while on a delivery; the reply says whether to keep sharing.
        post("/partner/location") { call.respond(s.delivery.recordLocation(call.userId, call.receive<LocationUpdate>())) }
        route("/partner/batches") {
            get {
                val status = call.request.queryParameters["status"]?.let {
                    runCatching { wireEnum<BatchStatus>(it) }.getOrNull() ?: badRequest("Unknown status")
                }
                call.respond(s.delivery.assignedBatches(call.userId, status))
            }
            get("/{id}") { call.respond(s.delivery.batch(call.userId, call.pathParam("id"))) }
            post("/{id}/pickup") {
                val body = call.receive<PickupConfirmation>()
                call.respond(s.delivery.confirmPickup(call.userId, call.pathParam("id"), body.confirmedMealCount))
            }
            post("/{id}/handover") {
                call.respond(s.delivery.confirmHandover(call.userId, call.pathParam("id"), call.receive<HandoverConfirmation>()))
            }
            post("/{id}/issue") {
                call.respond(s.delivery.reportIssue(call.userId, call.pathParam("id"), call.receive<DeliveryIssueReport>()))
            }
        }
    }
}

fun Route.paymentRoutes(s: Services) {
    route("/payments") {
        // Server-to-server callback from the gateway. Signature checked before anything else.
        post("/webhook/{gateway}") {
            val gateway = s.payments.gateways[call.pathParam("gateway")] ?: notFound("Gateway")
            val body = call.receiveText()
            val headers = call.request.headers.toMap().mapKeys { it.key.lowercase() }.mapValues { it.value.first() }
            val event = gateway.parseWebhook(headers, body)
                ?: throw ApiException(HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED, "Invalid signature")
            val outcome = s.payments.handleEvent(gateway.name, event)
            call.respond(mapOf("result" to outcome.name.lowercase()))
        }

        if (s.config.mockPaymentsEnabled) {
            // Dev-only stand-in for the gateway's hosted checkout page.
            get("/mock/checkout/{ref}") {
                val ref = call.pathParam("ref")
                val amount = s.db.tx {
                    Payments.selectAll().where { Payments.gatewayRef eq ref }.singleOrNull()?.get(Payments.amountPaise)
                } ?: notFound("Payment")
                call.respondText(mockCheckoutPage(ref, amount), ContentType.Text.Html)
            }
            post("/mock/checkout/{ref}/{result}") {
                val ref = call.pathParam("ref")
                val success = call.pathParam("result") == "success"
                val outcome = s.payments.handleEvent(
                    s.mockGateway.name,
                    GatewayEvent(eventId = "mock_evt_" + newId(), gatewayRef = ref, success = success, payload = "{\"source\":\"mock_checkout\",\"at\":\"${s.clock.nowUtc()}\"}"),
                )
                call.respondText(
                    page("Payment ${if (success) "successful" else "failed"}", "<p>Result: ${outcome.name.lowercase()}</p><p>You can return to the app.</p>"),
                    ContentType.Text.Html,
                )
            }
        }
    }
}

private fun mockCheckoutPage(ref: String, amountPaise: Long): String {
    val amount = "%d.%02d".format(amountPaise / 100, amountPaise % 100)
    return page(
        "Mock payment",
        """
        <p>Test checkout &mdash; no real money moves.</p>
        <p class="amount">&#8377;$amount</p>
        <form method="post" action="$ref/success"><button class="pay">Pay</button></form>
        <form method="post" action="$ref/failure"><button class="fail">Fail payment</button></form>
        """.trimIndent(),
    )
}

private fun page(title: String, body: String) = """
    <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
    <title>$title</title>
    <style>body{font-family:system-ui,sans-serif;max-width:420px;margin:40px auto;padding:0 16px}
    .amount{font-size:2rem;font-weight:600}button{width:100%;padding:14px;margin:6px 0;font-size:1rem;border:0;border-radius:8px}
    .pay{background:#2e7d32;color:#fff}.fail{background:#eee}</style></head>
    <body><h1>$title</h1>$body</body></html>
""".trimIndent()
