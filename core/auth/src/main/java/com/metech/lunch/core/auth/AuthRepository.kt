package com.metech.lunch.core.auth

import com.metech.lunch.core.model.OtpRequest
import com.metech.lunch.core.model.OtpVerifyRequest
import com.metech.lunch.core.model.UserRole
import com.metech.lunch.core.network.LunchApi

/** Phone + OTP login for a given app role. The provider is still to be confirmed with the client. */
class AuthRepository(
    private val api: LunchApi,
    private val session: SessionStore,
    private val role: UserRole,
) {
    suspend fun isLoggedIn(): Boolean = session.hasSession()

    suspend fun requestOtp(phone: String): Result<Unit> = runCatching {
        api.requestOtp(OtpRequest(phone = phone, role = role))
    }

    suspend fun verifyOtp(phone: String, otp: String): Result<Unit> = runCatching {
        val tokens = api.verifyOtp(OtpVerifyRequest(phone = phone, otp = otp, role = role))
        session.save(tokens)
    }

    suspend fun logout() = session.clear()
}
