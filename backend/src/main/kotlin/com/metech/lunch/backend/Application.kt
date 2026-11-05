package com.metech.lunch.backend

import com.auth0.jwt.JWT
import com.metech.lunch.backend.admin.CatalogAdmin
import com.metech.lunch.backend.admin.OperationsAdmin
import com.metech.lunch.backend.admin.OwnerAdmin
import com.metech.lunch.backend.auth.AuthService
import com.metech.lunch.backend.routes.adminRoutes
import com.metech.lunch.backend.auth.LoggingOtpSender
import com.metech.lunch.backend.auth.OtpSender
import com.metech.lunch.backend.common.ApiException
import com.metech.lunch.backend.common.wire
import com.metech.lunch.backend.db.Db
import com.metech.lunch.backend.delivery.DeliveryService
import com.metech.lunch.backend.dev.DemoSeed
import com.metech.lunch.backend.menu.MenuService
import com.metech.lunch.backend.orders.OrderService
import com.metech.lunch.backend.payments.MockGateway
import com.metech.lunch.backend.payments.PaymentService
import com.metech.lunch.backend.routes.authRoutes
import com.metech.lunch.backend.routes.customerRoutes
import com.metech.lunch.backend.routes.partnerRoutes
import com.metech.lunch.backend.routes.paymentRoutes
import com.metech.lunch.core.model.ApiError
import com.metech.lunch.core.model.ErrorCodes
import com.metech.lunch.core.model.UserRole
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Clock

/** Everything the HTTP layer needs, built once at startup (or per test). */
class Services(
    val config: AppConfig,
    val db: Db,
    val clock: Clock = Clock.systemUTC(),
    otpSender: OtpSender = LoggingOtpSender(),
) {
    val mockGateway = MockGateway(config.publicBaseUrl, config.mockWebhookSecret)
    // TODO: add the real gateway once the client chooses one; mock stays for dev/tests.
    val gateway = mockGateway
    val auth = AuthService(config, db, clock, otpSender)
    val menu = MenuService(config, db, clock)
    val payments = PaymentService(db, clock, menu, mapOf(mockGateway.name to mockGateway))
    val orders = OrderService(db, clock, menu, gateway)
    val delivery = DeliveryService(db, clock)
    val catalog = CatalogAdmin(db, clock, menu)
    val ops = OperationsAdmin(db, clock, menu)
    val owner = OwnerAdmin(db, clock, menu, ops, payments.gateways)
}

/**
 * `run`            starts the API (migrating the database first).
 * `run --args=migrate` only applies pending migrations and exits; use it for a first
 * connection check or as a release step before starting new servers.
 */
fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("startup")
    val config = AppConfig.fromEnv()
    log.info("Connecting to PostgreSQL at {} as {}", config.db.host, config.db.user)
    val db = try {
        Db.connect(config.db).also { it.migrate() }
    } catch (e: Exception) {
        log.error(
            "Could not use the database at {}: {}\n" +
                "Check DATABASE_URL / DATABASE_USER / DATABASE_PASSWORD. For Azure, also check that the server's " +
                "firewall allows this machine's IP and that the database exists.",
            config.db.host, rootMessage(e),
        )
        kotlin.system.exitProcess(1)
    }
    if (args.firstOrNull() == "migrate") {
        log.info("Database is up to date.")
        kotlin.system.exitProcess(0)
    }
    val services = Services(config, db)
    if (config.seedDemoData) DemoSeed(db, services.clock).run()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(services) }.start(wait = true)
}

private fun rootMessage(e: Throwable): String {
    var cause: Throwable = e
    while (cause.cause != null && cause.cause !== cause) cause = cause.cause!!
    val kind = when (cause) {
        is java.net.UnknownHostException -> "unknown host"
        is java.net.ConnectException, is java.net.SocketTimeoutException -> "cannot connect (firewall or wrong port?)"
        else -> cause.javaClass.simpleName
    }
    return "$kind: ${cause.message}"
}

object AuthProviders {
    const val CUSTOMER = "customer"
    const val PARTNER = "partner"
    const val STAFF = "staff"
    const val OWNER = "owner"
    const val ANY = "any"
}

val apiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

fun Application.module(services: Services) {
    val log = LoggerFactory.getLogger("api")
    val config = services.config

    install(ContentNegotiation) { json(apiJson) }
    install(CallLogging)

    if (config.corsOrigins.isNotEmpty()) {
        install(CORS) {
            config.corsOrigins.forEach { origin ->
                val (scheme, host) = origin.split("://", limit = 2).let { it[0] to it[1] }
                allowHost(host, schemes = listOf(scheme))
            }
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Patch)
            allowMethod(HttpMethod.Delete)
        }
    }

    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(e.status, ApiError(e.code, e.message)) }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError(ErrorCodes.INVALID_REQUEST, "The request body is invalid"))
        }
        exception<Throwable> { call, e ->
            log.error("Unhandled error", e)
            call.respond(HttpStatusCode.InternalServerError, ApiError(ErrorCodes.INTERNAL, "Something went wrong"))
        }
        status(HttpStatusCode.Unauthorized) { call, status ->
            call.respond(status, ApiError(ErrorCodes.UNAUTHORIZED, "Not signed in"))
        }
    }

    install(Authentication) {
        fun jwtFor(name: String, roles: Set<UserRole>) = jwt(name) {
            realm = "metech-lunch"
            verifier(
                JWT.require(services.auth.jwtAlgorithm)
                    .withIssuer(config.jwtIssuer)
                    .withAudience(services.auth.jwtAudience)
                    .build(),
            )
            validate { cred ->
                val role = cred.payload.getClaim("role").asString()
                if (cred.payload.subject != null && roles.any { it.wire() == role }) JWTPrincipal(cred.payload) else null
            }
            // A valid token with the wrong role is a permissions problem, not a login problem.
            challenge { _, _ ->
                val token = call.request.headers[HttpHeaders.Authorization]
                if (token != null && roles.isNotEmpty() && hasValidTokenForAnotherRole(token, services, config)) {
                    call.respond(HttpStatusCode.Forbidden, ApiError(ErrorCodes.FORBIDDEN, "You do not have access to this"))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, ApiError(ErrorCodes.UNAUTHORIZED, "Not signed in"))
                }
            }
        }
        jwtFor(AuthProviders.CUSTOMER, setOf(UserRole.CUSTOMER))
        jwtFor(AuthProviders.PARTNER, setOf(UserRole.DELIVERY_PARTNER))
        jwtFor(AuthProviders.STAFF, setOf(UserRole.OWNER, UserRole.KITCHEN_STAFF))
        jwtFor(AuthProviders.OWNER, setOf(UserRole.OWNER))
        jwtFor(AuthProviders.ANY, UserRole.entries.toSet())
    }

    routing {
        route("/api") {
            get("/health") { call.respond(mapOf("status" to "ok")) }
            authRoutes(services)
            customerRoutes(services)
            partnerRoutes(services)
            paymentRoutes(services)
            adminRoutes(services)
            // Unknown API paths stay JSON 404s instead of falling through to the dashboard.
            route("{...}") {
                handle { call.respond(HttpStatusCode.NotFound, ApiError(ErrorCodes.NOT_FOUND, "Not found")) }
            }
        }
        config.dashboardDir?.let { dir ->
            // Single-page app: any path that isn't a file gets index.html, and the dashboard routes it.
            staticFiles("/", File(dir)) { default("index.html") }
        }
    }
}

private fun hasValidTokenForAnotherRole(header: String, services: Services, config: AppConfig): Boolean =
    runCatching {
        JWT.require(services.auth.jwtAlgorithm).withIssuer(config.jwtIssuer).withAudience(services.auth.jwtAudience)
            .build().verify(header.removePrefix("Bearer ").trim())
    }.isSuccess
