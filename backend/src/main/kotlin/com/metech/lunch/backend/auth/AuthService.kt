package com.metech.lunch.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.metech.lunch.backend.AppConfig
import com.metech.lunch.backend.common.ApiException
import com.metech.lunch.backend.common.badRequest
import com.metech.lunch.backend.common.constantTimeEquals
import com.metech.lunch.backend.common.newId
import com.metech.lunch.backend.common.nowUtc
import com.metech.lunch.backend.common.randomDigits
import com.metech.lunch.backend.common.randomToken
import com.metech.lunch.backend.common.sha256
import com.metech.lunch.backend.common.unauthorized
import com.metech.lunch.backend.common.wire
import com.metech.lunch.backend.common.wireEnum
import com.metech.lunch.backend.db.Db
import com.metech.lunch.backend.db.OtpCodes
import com.metech.lunch.backend.db.RefreshTokens
import com.metech.lunch.backend.db.Users
import com.metech.lunch.core.model.AuthTokens
import com.metech.lunch.core.model.ErrorCodes
import com.metech.lunch.core.model.UserProfile
import com.metech.lunch.core.model.UserRole
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.Date

/** Delivers OTP codes. Swap the dev implementation for the agreed SMS provider. */
fun interface OtpSender {
    fun send(phone: String, code: String)
}

class LoggingOtpSender : OtpSender {
    private val log = LoggerFactory.getLogger(LoggingOtpSender::class.java)
    override fun send(phone: String, code: String) {
        log.info("DEV OTP for {}: {}", phone, code)
    }
}

class AuthService(
    private val config: AppConfig,
    private val db: Db,
    private val clock: Clock,
    private val otpSender: OtpSender,
) {
    val jwtAlgorithm: Algorithm = Algorithm.HMAC256(config.jwtSecret)
    val jwtAudience = "metech-lunch-api"

    /** Creates a code for [rawPhone]. Returns it only when the server shows codes on screen. */
    suspend fun requestOtp(rawPhone: String, role: UserRole): String? {
        val phone = normalizePhone(rawPhone)
        val now = clock.nowUtc()
        val code = db.tx {
            val recent = OtpCodes.selectAll()
                .where { (OtpCodes.phone eq phone) and (OtpCodes.role eq role.wire()) and (OtpCodes.createdAt greater now.minusHours(1)) }
                .count()
            if (recent >= config.otpMaxPerHour) {
                throw ApiException(HttpStatusCode.TooManyRequests, ErrorCodes.TOO_MANY_REQUESTS, "Too many OTP requests. Try again later.")
            }
            // Staff and partner accounts are created by the owner. For unknown numbers we
            // answer the same way but send nothing, so the endpoint does not reveal accounts.
            if (role != UserRole.CUSTOMER && findActiveUser(phone, role) == null) {
                // With codes shown on screen, hiding which numbers exist protects nothing; say so plainly.
                if (config.showOtpOnScreen) {
                    throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "This number isn't registered as ${roleName(role)}.")
                }
                return@tx null
            }

            val code = config.devFixedOtp ?: randomDigits(6)
            OtpCodes.insert {
                it[OtpCodes.phone] = phone
                it[OtpCodes.role] = role.wire()
                it[codeHash] = hashOtp(phone, code)
                it[expiresAt] = now.plus(config.otpTtl)
                it[attempts] = 0
                it[consumed] = false
                it[createdAt] = now
            }
            code
        }
        if (code == null) return null
        if (config.showOtpOnScreen) return code
        if (config.devFixedOtp == null) otpSender.send(phone, code)
        return null
    }

    private fun roleName(role: UserRole) = when (role) {
        UserRole.OWNER -> "an owner"
        UserRole.KITCHEN_STAFF -> "kitchen staff"
        UserRole.DELIVERY_PARTNER -> "a delivery partner"
        UserRole.CUSTOMER -> "a customer"
    }

    suspend fun verifyOtp(rawPhone: String, otp: String, role: UserRole): AuthTokens {
        val phone = normalizePhone(rawPhone)
        val now = clock.nowUtc()
        val result = db.tx {
            val row = OtpCodes.selectAll()
                .where { (OtpCodes.phone eq phone) and (OtpCodes.role eq role.wire()) and (OtpCodes.consumed eq false) }
                .orderBy(OtpCodes.createdAt, SortOrder.DESC)
                .limit(1)
                .forUpdate()
                .singleOrNull()
            if (row == null || row[OtpCodes.expiresAt] < now || row[OtpCodes.attempts] >= config.otpMaxAttempts) {
                return@tx null
            }
            if (!constantTimeEquals(row[OtpCodes.codeHash], hashOtp(phone, otp.trim()))) {
                OtpCodes.update({ OtpCodes.id eq row[OtpCodes.id] }) { it[attempts] = row[OtpCodes.attempts] + 1 }
                return@tx null
            }
            OtpCodes.update({ OtpCodes.id eq row[OtpCodes.id] }) { it[consumed] = true }

            val userId = findActiveUser(phone, role)?.get(Users.id)
                ?: if (role == UserRole.CUSTOMER) createCustomer(phone) else return@tx null
            userId to issueRefreshToken(userId)
        } ?: throw ApiException(HttpStatusCode.Unauthorized, ErrorCodes.INVALID_OTP, "The OTP is incorrect or has expired")

        val (userId, refresh) = result
        return AuthTokens(accessToken = accessToken(userId, role), refreshToken = refresh)
    }

    /** Rotates the refresh token: the old one is revoked and a new pair is issued. */
    suspend fun refresh(refreshToken: String): AuthTokens {
        val now = clock.nowUtc()
        val (userId, role, newRefresh) = db.tx {
            val row = RefreshTokens.selectAll()
                .where { RefreshTokens.tokenHash eq sha256(refreshToken) }
                .forUpdate()
                .singleOrNull()
            if (row == null || row[RefreshTokens.revoked] || row[RefreshTokens.expiresAt] < now) return@tx null
            val user = Users.selectAll().where { (Users.id eq row[RefreshTokens.userId]) and (Users.active eq true) }
                .singleOrNull() ?: return@tx null
            RefreshTokens.update({ RefreshTokens.tokenHash eq row[RefreshTokens.tokenHash] }) { it[revoked] = true }
            Triple(user[Users.id], wireEnum<UserRole>(user[Users.role]), issueRefreshToken(user[Users.id]))
        } ?: unauthorized(message = "Session expired. Please sign in again.")
        return AuthTokens(accessToken(userId, role), newRefresh)
    }

    suspend fun profile(userId: String): UserProfile = db.tx {
        Users.selectAll().where { Users.id eq userId }.single().toProfile()
    }

    fun accessToken(userId: String, role: UserRole): String =
        JWT.create()
            .withIssuer(config.jwtIssuer)
            .withAudience(jwtAudience)
            .withSubject(userId)
            .withClaim("role", role.wire())
            // JWT expiry uses wall-clock time: the verifier checks against the system clock.
            .withExpiresAt(Date(System.currentTimeMillis() + config.accessTokenTtl.toMillis()))
            .sign(jwtAlgorithm)

    private fun findActiveUser(phone: String, role: UserRole): ResultRow? =
        Users.selectAll()
            .where { (Users.phone eq phone) and (Users.role eq role.wire()) and (Users.active eq true) }
            .singleOrNull()

    private fun createCustomer(phone: String): String {
        val id = newId()
        Users.insert {
            it[Users.id] = id
            it[Users.phone] = phone
            it[role] = UserRole.CUSTOMER.wire()
            it[name] = ""
            it[active] = true
            it[createdAt] = clock.nowUtc()
        }
        return id
    }

    private fun issueRefreshToken(userId: String): String {
        val token = randomToken()
        val now = clock.nowUtc()
        RefreshTokens.insert {
            it[tokenHash] = sha256(token)
            it[RefreshTokens.userId] = userId
            it[expiresAt] = now.plus(config.refreshTokenTtl)
            it[revoked] = false
            it[createdAt] = now
        }
        return token
    }

    private fun hashOtp(phone: String, code: String) = sha256("$phone:$code:${config.jwtSecret}")

    companion object {
        private val PHONE = Regex("^\\+?[0-9]{10,15}$")

        fun normalizePhone(raw: String): String {
            val cleaned = raw.replace(Regex("[\\s()-]"), "")
            if (!PHONE.matches(cleaned)) badRequest("Enter a valid phone number")
            // Bare 10-digit numbers are treated as Indian mobile numbers.
            return if (cleaned.length == 10 && !cleaned.startsWith("+")) "+91$cleaned"
            else if (cleaned.startsWith("+")) cleaned else "+$cleaned"
        }
    }
}

fun ResultRow.toProfile() = UserProfile(
    id = this[Users.id],
    name = this[Users.name],
    phone = this[Users.phone],
    email = this[Users.email],
    role = wireEnum(this[Users.role]),
    defaultOfficeId = this[Users.defaultOfficeId],
    defaultDropPointId = this[Users.defaultDropPointId],
)
