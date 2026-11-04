package com.metech.lunch.backend

import com.metech.lunch.backend.common.newId
import com.metech.lunch.backend.common.nowUtc
import com.metech.lunch.backend.common.wire
import com.metech.lunch.backend.db.BatchOrders
import com.metech.lunch.backend.db.DailyMenuItems
import com.metech.lunch.backend.db.Db
import com.metech.lunch.backend.db.DeliveryBatches
import com.metech.lunch.backend.db.Users
import com.metech.lunch.backend.dev.DemoSeed
import com.metech.lunch.core.model.AuthTokens
import com.metech.lunch.core.model.BatchStatus
import com.metech.lunch.core.model.OtpRequest
import com.metech.lunch.core.model.OtpVerifyRequest
import com.metech.lunch.core.model.UserRole
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** A clock tests can move. */
class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
    fun setIst(date: LocalDate, hour: Int, minute: Int = 0) {
        now = ZonedDateTime.of(date, java.time.LocalTime.of(hour, minute), IST).toInstant()
    }
}

val IST: ZoneId = ZoneId.of("Asia/Kolkata")
val TEST_DATE: LocalDate = LocalDate.of(2026, 11, 4)

object TestDb {
    /** One embedded Postgres for the whole test run; schema migrated once. */
    val db: Db by lazy {
        val pg = EmbeddedPostgres.builder().start()
        Db(pg.postgresDatabase).also { it.migrate() }
    }

    fun reset() {
        db.txBlocking {
            exec(
                """TRUNCATE audit_log, delivery_issues, batch_orders, delivery_batches, payment_events, payments,
                   order_lines, orders, daily_menu_items, menu_items, refresh_tokens, otp_codes, users,
                   drop_points, offices, kitchens RESTART IDENTITY CASCADE""",
            )
        }
    }
}

val testConfig = AppConfig(
    production = false,
    port = 0,
    publicBaseUrl = "http://localhost",
    db = DbConfig("unused", "unused", "unused"),
    jwtSecret = "test-secret",
    devFixedOtp = "123456",
    mockWebhookSecret = "test-webhook-secret",
)

class TestEnv(val clock: MutableClock, val services: Services) {
    val db get() = services.db

    fun setCapacity(menuItemId: String, capacity: Int?) = db.txBlocking {
        DailyMenuItems.update({
            (DailyMenuItems.menuItemId eq menuItemId) and (DailyMenuItems.menuDate eq clock.instant().atZone(IST).toLocalDate())
        }) { it[DailyMenuItems.capacity] = capacity }
    }

    fun userIdByPhone(phone: String, role: UserRole): String = db.txBlocking {
        Users.selectAll().where { (Users.phone eq phone) and (Users.role eq role.wire()) }.single()[Users.id]
    }

    /** Creates a batch for the given orders and assigns it to [partnerId]. */
    fun createBatch(partnerId: String, officeId: String, orderIds: List<String>, coordinatorOtp: String? = null): String =
        db.txBlocking {
            val id = newId()
            DeliveryBatches.insert {
                it[DeliveryBatches.id] = id
                it[kitchenId] = DemoSeed.KITCHEN_ID
                it[deliveryDate] = clock.instant().atZone(IST).toLocalDate()
                it[DeliveryBatches.officeId] = officeId
                it[DeliveryBatches.partnerId] = partnerId
                it[status] = BatchStatus.ASSIGNED.wire()
                it[coordinatorName] = "Asha"
                it[coordinatorPhone] = "+919999911111"
                it[DeliveryBatches.coordinatorOtp] = coordinatorOtp
                it[createdAt] = clock.nowUtc()
                it[updatedAt] = clock.nowUtc()
            }
            orderIds.forEach { orderId ->
                BatchOrders.insert {
                    it[batchId] = id
                    it[BatchOrders.orderId] = orderId
                }
            }
            id
        }
}

/** Runs [block] against a fresh database seeded with demo data, at 08:30 IST on [TEST_DATE]. */
fun apiTest(block: suspend ApplicationTestBuilder.(env: TestEnv, client: HttpClient) -> Unit) {
    TestDb.reset()
    val clock = MutableClock(Instant.EPOCH).apply { setIst(TEST_DATE, 8, 30) }
    val services = Services(testConfig, TestDb.db, clock)
    DemoSeed(TestDb.db, clock).run()
    val env = TestEnv(clock, services)
    testApplication {
        application { module(services) }
        val client = createClient { install(ContentNegotiation) { json(apiJson) } }
        block(env, client)
    }
}

suspend fun HttpClient.login(phone: String, role: UserRole = UserRole.CUSTOMER): String {
    post("/api/auth/otp/request") {
        contentType(ContentType.Application.Json)
        setBody(OtpRequest(phone, role))
    }
    return post("/api/auth/otp/verify") {
        contentType(ContentType.Application.Json)
        setBody(OtpVerifyRequest(phone, "123456", role))
    }.body<AuthTokens>().accessToken
}

suspend inline fun <reified T> HttpClient.postJson(path: String, token: String?, body: Any?) =
    post(path) {
        if (token != null) bearerAuth(token)
        contentType(ContentType.Application.Json)
        if (body != null) setBody(body)
    }
