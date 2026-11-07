package com.metech.lunch.core.auth

import com.metech.lunch.core.model.OtpRequest
import com.metech.lunch.core.model.OtpVerifyRequest
import com.metech.lunch.core.model.UserRole
import com.metech.lunch.core.network.ApiResult
import com.metech.lunch.core.network.LunchApi
import com.metech.lunch.core.network.apiCall

/** Phone + OTP login for one app role. */
class AuthRepository(
    private val api: LunchApi,
    private val session: SessionStore,
    private val role: UserRole,
) {
    suspend fun isLoggedIn(): Boolean = session.hasSession()

    /** Success value is the code to show on screen, when the server shows codes (pilot without SMS). */
    suspend fun requestOtp(phone: String): ApiResult<String?> =
        apiCall { api.requestOtp(OtpRequest(phone = phone, role = role)).displayCode }

    suspend fun verifyOtp(phone: String, otp: String): ApiResult<Unit> =
        apiCall {
            val tokens = api.verifyOtp(OtpVerifyRequest(phone = phone, otp = otp, role = role))
            session.save(tokens)
        }

    suspend fun logout() = session.clear()
}
