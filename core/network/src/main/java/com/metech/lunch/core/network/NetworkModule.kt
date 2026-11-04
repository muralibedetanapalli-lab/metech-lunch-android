package com.metech.lunch.core.network

import com.metech.lunch.core.model.AuthTokens
import com.metech.lunch.core.model.RefreshRequest
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/** Session storage the network layer needs; implemented by core:auth. */
interface TokenProvider {
    fun accessToken(): String?
    fun refreshToken(): String?
    /** Called from an OkHttp thread after a successful refresh. */
    fun onTokensRefreshed(tokens: AuthTokens)
    /** Called when the refresh token is rejected; the user must sign in again. */
    fun onSessionExpired()
}

private interface RefreshApi {
    @POST("auth/refresh")
    fun refresh(@Body body: RefreshRequest): Call<AuthTokens>
}

object NetworkModule {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun createApi(
        baseUrl: String,
        tokenProvider: TokenProvider,
        enableLogging: Boolean,
    ): LunchApi {
        val converter = json.asConverterFactory("application/json".toMediaType())
        val logging = HttpLoggingInterceptor().setLevel(
            if (enableLogging) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE,
        )

        val refreshApi = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(OkHttpClient.Builder().addInterceptor(logging).build())
            .addConverterFactory(converter)
            .build()
            .create(RefreshApi::class.java)

        val authInterceptor = Interceptor { chain ->
            val token = tokenProvider.accessToken()
            val request = if (token != null) {
                chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        // On 401, refresh once and retry with the new access token.
        val refreshLock = Any()
        val authenticator = Authenticator { _, response ->
            if (response.priorResponse != null) return@Authenticator null // already retried
            val sentToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            synchronized(refreshLock) {
                val current = tokenProvider.accessToken()
                val newToken = if (current != null && current != sentToken) {
                    current // another request refreshed meanwhile
                } else {
                    val refresh = tokenProvider.refreshToken() ?: return@Authenticator null
                    val result = runCatching { refreshApi.refresh(RefreshRequest(refresh)).execute() }.getOrNull()
                    val tokens = result?.body()
                    if (tokens == null) {
                        if (result != null && result.code() == 401) tokenProvider.onSessionExpired()
                        return@Authenticator null
                    }
                    tokenProvider.onTokensRefreshed(tokens)
                    tokens.accessToken
                }
                response.request.newBuilder().header("Authorization", "Bearer $newToken").build()
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(converter)
            .build()
            .create(LunchApi::class.java)
    }
}
