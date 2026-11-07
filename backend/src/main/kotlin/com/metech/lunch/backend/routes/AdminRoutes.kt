package com.metech.lunch.backend.routes

import com.metech.lunch.backend.AuthProviders
import com.metech.lunch.backend.Services
import com.metech.lunch.backend.admin.AssignBatchRequest
import com.metech.lunch.backend.admin.CreateUserRequest
import com.metech.lunch.backend.admin.DateRequest
import com.metech.lunch.backend.admin.RefundDecision
import com.metech.lunch.backend.admin.SaveDailyMenuRequest
import com.metech.lunch.backend.admin.SaveDropPointRequest
import com.metech.lunch.backend.admin.SaveMenuItemRequest
import com.metech.lunch.backend.admin.SaveOfficeRequest
import com.metech.lunch.backend.admin.Staff
import com.metech.lunch.backend.admin.UpdateKitchenRequest
import com.metech.lunch.backend.admin.UpdateUserRequest
import com.metech.lunch.backend.admin.parseDate
import com.metech.lunch.backend.common.ApiException
import com.metech.lunch.backend.common.badRequest
import com.metech.lunch.backend.common.wireEnum
import com.metech.lunch.backend.db.Users
import com.metech.lunch.core.model.ErrorCodes
import com.metech.lunch.core.model.OrderStatus
import com.metech.lunch.core.model.UserRole
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

/** Resolves the signed-in staff member and their kitchen. Deactivated accounts are refused. */
private suspend fun ApplicationCall.staff(s: Services): Staff {
    val userId = principal<JWTPrincipal>()!!.payload.subject
    val row = s.db.tx { Users.selectAll().where { (Users.id eq userId) and (Users.active eq true) }.singleOrNull() }
        ?: throw ApiException(HttpStatusCode.Forbidden, ErrorCodes.FORBIDDEN, "This account is not active")
    val kitchenId = row[Users.kitchenId]
        ?: throw ApiException(HttpStatusCode.Forbidden, ErrorCodes.FORBIDDEN, "This account is not linked to a kitchen")
    return Staff(userId, row[Users.role] == "owner", kitchenId)
}

private fun ApplicationCall.date(name: String = "date") = parseDate(request.queryParameters[name])

private fun ApplicationCall.id(name: String = "id") = parameters[name] ?: badRequest("Missing $name")

fun Route.adminRoutes(s: Services) {
    route("/admin") {
        // Owner and kitchen staff: day-to-day kitchen work.
        authenticate(AuthProviders.STAFF) {
            get("/kitchen") { call.respond(s.catalog.kitchen(call.staff(s))) }
            get("/overview") { call.respond(s.ops.overview(call.staff(s), call.date())) }

            get("/menu-items") { call.respond(s.catalog.menuItems(call.staff(s))) }
            get("/daily-menu") { call.respond(s.catalog.dailyMenu(call.staff(s), call.date())) }
            put("/daily-menu") {
                val date = call.date() ?: badRequest("date is required")
                call.respond(s.catalog.saveDailyMenu(call.staff(s), date, call.receive<SaveDailyMenuRequest>()))
            }

            get("/prep") { call.respond(s.ops.prep(call.staff(s), call.date())) }
            post("/prep/start") {
                val date = parseDate(call.receive<DateRequest>().date)!!
                call.respond(s.ops.startPreparation(call.staff(s), date))
            }

            get("/batches") { call.respond(s.ops.batches(call.staff(s), call.date())) }
            post("/batches/generate") {
                val date = parseDate(call.receive<DateRequest>().date)!!
                call.respond(s.ops.generateBatches(call.staff(s), date))
            }
            put("/batches/{id}/assign") {
                call.respond(s.ops.assignBatch(call.staff(s), call.id(), call.receive<AssignBatchRequest>()))
            }
            get("/issues") { call.respond(s.ops.issues(call.staff(s), call.date())) }
            // Live delivery state of one order: partner, latest GPS fix and trail since pickup.
            get("/orders/{id}/tracking") { call.respond(s.tracking.orderTracking(call.staff(s), call.id())) }
            get("/partners") { call.respond(s.owner.users(call.staff(s), UserRole.DELIVERY_PARTNER).filter { it.active }) }
        }

        // Owner only: money, people, settings and records.
        authenticate(AuthProviders.OWNER) {
            put("/kitchen") { call.respond(s.catalog.updateKitchen(call.staff(s), call.receive<UpdateKitchenRequest>())) }

            route("/offices") {
                get { call.respond(s.catalog.offices(call.staff(s))) }
                post { call.respond(s.catalog.createOffice(call.staff(s), call.receive<SaveOfficeRequest>())) }
                put("/{id}") { call.respond(s.catalog.updateOffice(call.staff(s), call.id(), call.receive<SaveOfficeRequest>())) }
                post("/{id}/drop-points") {
                    call.respond(s.catalog.createDropPoint(call.staff(s), call.id(), call.receive<SaveDropPointRequest>()))
                }
            }
            put("/drop-points/{id}") {
                call.respond(s.catalog.updateDropPoint(call.staff(s), call.id(), call.receive<SaveDropPointRequest>()))
            }

            post("/menu-items") { call.respond(s.catalog.createMenuItem(call.staff(s), call.receive<SaveMenuItemRequest>())) }
            put("/menu-items/{id}") { call.respond(s.catalog.updateMenuItem(call.staff(s), call.id(), call.receive<SaveMenuItemRequest>())) }

            get("/orders") {
                val status = call.request.queryParameters["status"]?.let {
                    runCatching { wireEnum<OrderStatus>(it) }.getOrNull() ?: badRequest("Unknown status")
                }
                call.respond(s.ops.orders(call.staff(s), call.date(), status, call.request.queryParameters["officeId"]))
            }
            post("/orders/{id}/refund") { call.respond(s.owner.requestRefund(call.staff(s), call.id())) }

            get("/refunds") {
                call.respond(s.owner.refunds(call.staff(s), includeClosed = call.request.queryParameters["all"] == "true"))
            }
            post("/refunds/{id}/approve") {
                call.respond(s.owner.approveRefund(call.staff(s), call.id(), call.receive<RefundDecision>().note))
            }
            post("/refunds/{id}/reject") {
                call.respond(s.owner.rejectRefund(call.staff(s), call.id(), call.receive<RefundDecision>().note))
            }

            route("/users") {
                get {
                    val role = call.request.queryParameters["role"]?.let {
                        runCatching { wireEnum<UserRole>(it) }.getOrNull() ?: badRequest("Unknown role")
                    } ?: badRequest("role is required")
                    call.respond(s.owner.users(call.staff(s), role))
                }
                post { call.respond(HttpStatusCode.Created, s.owner.createUser(call.staff(s), call.receive<CreateUserRequest>())) }
                put("/{id}") { call.respond(s.owner.updateUser(call.staff(s), call.id(), call.receive<UpdateUserRequest>())) }
            }

            get("/reports") { call.respond(s.owner.report(call.staff(s), call.date("from"), call.date("to"))) }
            get("/exports/orders.csv") {
                val from = call.date("from")
                val to = call.date("to")
                val csv = s.owner.ordersCsv(call.staff(s), from, to)
                val name = "orders-${from ?: "today"}${if (to != null && to != from) "-to-$to" else ""}.csv"
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, name).toString(),
                )
                call.respondText(csv, ContentType.Text.CSV)
            }
            get("/audit") {
                call.respond(s.owner.audit(call.staff(s), call.request.queryParameters["limit"]?.toIntOrNull() ?: 200))
            }
        }
    }
}
