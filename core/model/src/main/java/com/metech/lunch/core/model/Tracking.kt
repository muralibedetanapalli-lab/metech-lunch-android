package com.metech.lunch.core.model

import kotlinx.serialization.Serializable

/** One GPS fix from the delivery partner's phone. */
@Serializable
data class LocationUpdate(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val speedMps: Float? = null,
    val headingDegrees: Float? = null,
    /** ISO date-time the phone took the fix; the server time is used if missing or implausible. */
    val recordedAt: String? = null,
)

/** [tracking] is false once the partner has nothing on the road, so the app can stop sharing. */
@Serializable
data class LocationAck(val tracking: Boolean)
