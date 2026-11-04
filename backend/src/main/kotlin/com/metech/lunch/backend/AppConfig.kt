package com.metech.lunch.backend

import java.time.Duration

data class DbConfig(val url: String, val user: String, val password: String)

data class AppConfig(
    val production: Boolean,
    val port: Int,
    /** Public base URL of this server, used to build payment checkout links. */
    val publicBaseUrl: String,
    val db: DbConfig,
    val jwtSecret: String,
    val jwtIssuer: String = "metech-lunch",
    val accessTokenTtl: Duration = Duration.ofHours(1),
    val refreshTokenTtl: Duration = Duration.ofDays(30),
    val otpTtl: Duration = Duration.ofMinutes(5),
    val otpMaxAttempts: Int = 5,
    val otpMaxPerHour: Int = 5,
    /** Dev/test only: every OTP is this value, and nothing is sent. */
    val devFixedOtp: String? = null,
    /** How long an unpaid order holds its meals before the capacity is released. */
    val paymentHold: Duration = Duration.ofMinutes(15),
    val mockPaymentsEnabled: Boolean = true,
    val mockWebhookSecret: String = "dev-mock-webhook-secret",
    val seedDemoData: Boolean = false,
    val corsOrigins: List<String> = emptyList(),
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            val production = env["APP_ENV"] == "production"
            fun required(key: String, devDefault: String): String =
                env[key] ?: if (production) error("$key must be set in production") else devDefault

            val port = env["PORT"]?.toInt() ?: 8080
            return AppConfig(
                production = production,
                port = port,
                publicBaseUrl = env["PUBLIC_BASE_URL"] ?: "http://localhost:$port",
                db = DbConfig(
                    url = required("DATABASE_URL", "jdbc:postgresql://localhost:5433/metech_lunch"),
                    user = required("DATABASE_USER", "metech"),
                    password = required("DATABASE_PASSWORD", "metech"),
                ),
                jwtSecret = required("JWT_SECRET", "dev-only-jwt-secret-change-me"),
                devFixedOtp = if (production) null else env["DEV_FIXED_OTP"] ?: "123456",
                mockPaymentsEnabled = !production && env["MOCK_PAYMENTS"] != "false",
                mockWebhookSecret = env["MOCK_WEBHOOK_SECRET"] ?: "dev-mock-webhook-secret",
                seedDemoData = !production && env["SEED_DEMO_DATA"] != "false",
                corsOrigins = env["CORS_ORIGINS"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: if (production) emptyList() else listOf("http://localhost:5173"),
            )
        }
    }
}
