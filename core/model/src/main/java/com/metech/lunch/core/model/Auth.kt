package com.metech.lunch.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The login/OTP provider is still to be confirmed with the client; this assumes phone + OTP.

@Serializable
data class OtpRequest(val phone: String, val role: UserRole)

@Serializable
data class OtpVerifyRequest(val phone: String, val otp: String, val role: UserRole)

@Serializable
data class AuthTokens(val accessToken: String, val refreshToken: String)

@Serializable
enum class UserRole {
    @SerialName("customer") CUSTOMER,
    @SerialName("delivery_partner") DELIVERY_PARTNER,
}

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val role: UserRole,
    val defaultOfficeId: String? = null,
    val defaultDropPointId: String? = null,
)
