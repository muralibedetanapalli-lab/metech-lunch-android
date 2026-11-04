package com.metech.lunch.backend.common

import com.metech.lunch.backend.db.AuditLog
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import org.jetbrains.exposed.sql.insert
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

fun Clock.nowUtc(): OffsetDateTime = instant().atOffset(ZoneOffset.UTC)

private val secureRandom = SecureRandom()

/** Random numeric code, e.g. for OTPs. */
fun randomDigits(length: Int): String =
    (1..length).joinToString("") { secureRandom.nextInt(10).toString() }

/** Random URL-safe token for refresh tokens and gateway references. */
fun randomToken(bytes: Int = 32): String {
    val buf = ByteArray(bytes).also { secureRandom.nextBytes(it) }
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
}

fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

/** The enum's wire name (its @SerialName), used for DB storage too. */
inline fun <reified T : Enum<T>> T.wire(): String =
    Json.encodeToJsonElement(serializer<T>(), this).jsonPrimitive.content

inline fun <reified T : Enum<T>> wireEnum(value: String): T =
    Json.decodeFromJsonElement(serializer<T>(), JsonPrimitive(value))

private val stringList = ListSerializer(String.serializer())

fun encodeStringList(values: List<String>): String = Json.encodeToString(stringList, values)

fun decodeStringList(json: String): List<String> = Json.decodeFromString(stringList, json)

/** Records a key administrative or order action. Call inside a transaction. */
fun audit(clock: Clock, actorId: String?, action: String, entity: String, entityId: String, details: String? = null) {
    AuditLog.insert {
        it[AuditLog.actorId] = actorId
        it[AuditLog.action] = action
        it[AuditLog.entity] = entity
        it[AuditLog.entityId] = entityId
        it[AuditLog.details] = details
        it[AuditLog.createdAt] = clock.nowUtc()
    }
}
