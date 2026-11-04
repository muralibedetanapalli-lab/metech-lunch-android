package com.metech.lunch.core.model

import kotlinx.serialization.Serializable

/** Error body returned by the backend for any non-2xx response. */
@Serializable
data class ApiError(val code: String, val message: String)

/** Stable error codes the apps can branch on. */
object ErrorCodes {
    const val INVALID_REQUEST = "INVALID_REQUEST"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val FORBIDDEN = "FORBIDDEN"
    const val NOT_FOUND = "NOT_FOUND"
    const val INVALID_OTP = "INVALID_OTP"
    const val TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS"
    const val ORDERING_CLOSED = "ORDERING_CLOSED"
    const val OFFICE_NOT_SERVICEABLE = "OFFICE_NOT_SERVICEABLE"
    const val ITEM_UNAVAILABLE = "ITEM_UNAVAILABLE"
    const val OUT_OF_CAPACITY = "OUT_OF_CAPACITY"
    const val CANNOT_CANCEL = "CANNOT_CANCEL"
    const val INVALID_STATE = "INVALID_STATE"
    const val PICKUP_COUNT_MISMATCH = "PICKUP_COUNT_MISMATCH"
    const val INVALID_HANDOVER_OTP = "INVALID_HANDOVER_OTP"
    const val INTERNAL = "INTERNAL"
}
