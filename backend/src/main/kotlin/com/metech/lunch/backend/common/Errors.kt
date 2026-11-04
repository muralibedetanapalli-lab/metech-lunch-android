package com.metech.lunch.backend.common

import com.metech.lunch.core.model.ErrorCodes
import io.ktor.http.HttpStatusCode

/** Thrown by services; converted to an ApiError response by StatusPages. */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message)

fun badRequest(message: String): Nothing =
    throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST, message)

fun notFound(what: String): Nothing =
    throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "$what not found")

/** A business rule rejected the request (cutoff, capacity, wrong state...). */
fun rejected(code: String, message: String): Nothing =
    throw ApiException(HttpStatusCode.UnprocessableEntity, code, message)

fun unauthorized(code: String = ErrorCodes.UNAUTHORIZED, message: String = "Not signed in"): Nothing =
    throw ApiException(HttpStatusCode.Unauthorized, code, message)
