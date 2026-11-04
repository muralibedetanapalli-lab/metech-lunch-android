package com.metech.lunch.core.network

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Supplies the current access token; implemented by core:auth. */
fun interface TokenProvider {
    fun accessToken(): String?
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
        val authInterceptor = Interceptor { chain ->
            val token = tokenProvider.accessToken()
            val request = if (token != null) {
                chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .apply {
                if (enableLogging) {
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                }
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LunchApi::class.java)
    }
}
