package com.metech.lunch.backend

import java.time.Duration

data class DbConfig(val url: String, val user: String, val password: String) {
    /** Host part of the JDBC URL, for log messages (never includes credentials). */
    val host: String get() = url.substringAfter("//").substringBefore("/").substringBefore("?")

    override fun toString() = "DbConfig(url=$url, user=$user, password=***)"

    companion object {
        private val LOCAL_HOSTS = listOf("localhost", "127.0.0.1", "[::1]")

        /**
         * Hosted databases (Azure, AWS...) must use TLS. Adds `sslmode=require` to a remote
         * URL that does not set an sslmode, so the password never travels in clear text.
         */
        fun withTls(url: String): String {
            if ("sslmode=" in url) return url
            val host = url.substringAfter("//").substringBefore("/").substringBefore(":")
            if (LOCAL_HOSTS.any { host == it }) return url
            return url + (if ("?" in url) "&" else "?") + "sslmode=require"
        }
    }
}

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
    /** Code requests allowed per phone number and role in any rolling hour. */
    val otpMaxPerHour: Int = 10,
    /** Dev/test only: every OTP is this value, and nothing is sent. */
    val devFixedOtp: String? = null,
    /**
     * Pilot only (no SMS provider yet): every request gets a fresh random code, returned to the
     * caller so the sign-in screen can show it. Anyone who knows a number can then sign in as it.
     */
    val showOtpOnScreen: Boolean = false,
    /** How long an unpaid order holds its meals before the capacity is released. */
    val paymentHold: Duration = Duration.ofMinutes(15),
    val mockPaymentsEnabled: Boolean = true,
    val mockWebhookSecret: String = "dev-mock-webhook-secret",
    val seedDemoData: Boolean = false,
    val corsOrigins: List<String> = emptyList(),
    /** Built dashboard (dashboard/dist) to serve at `/`, so the dashboard and API share one address. */
    val dashboardDir: String? = null,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = EnvFile.load()): AppConfig {
            val production = env["APP_ENV"] == "production"
            fun required(key: String, devDefault: String): String =
                env[key] ?: if (production) error("$key must be set in production") else devDefault

            val port = env["PORT"]?.toInt() ?: 8080
            return AppConfig(
                production = production,
                port = port,
                publicBaseUrl = env["PUBLIC_BASE_URL"] ?: "http://localhost:$port",
                db = DbConfig(
                    url = DbConfig.withTls(required("DATABASE_URL", "jdbc:postgresql://localhost:5433/metech_lunch")),
                    user = required("DATABASE_USER", "metech"),
                    password = required("DATABASE_PASSWORD", "metech"),
                ),
                jwtSecret = required("JWT_SECRET", "dev-only-jwt-secret-change-me"),
                devFixedOtp = if (production || env["SHOW_OTP_ON_SCREEN"] == "true") null else env["DEV_FIXED_OTP"] ?: "123456",
                showOtpOnScreen = !production && env["SHOW_OTP_ON_SCREEN"] == "true",
                otpMaxPerHour = env["OTP_MAX_PER_HOUR"]?.toIntOrNull()?.coerceIn(1, 100) ?: 10,
                mockPaymentsEnabled = !production && env["MOCK_PAYMENTS"] != "false",
                mockWebhookSecret = env["MOCK_WEBHOOK_SECRET"] ?: "dev-mock-webhook-secret",
                // Opt-in only: never put demo accounts into a real database by accident.
                // (`./gradlew :backend:runDev` seeds its own embedded database regardless.)
                seedDemoData = !production && env["SEED_DEMO_DATA"] == "true",
                corsOrigins = env["CORS_ORIGINS"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: if (production) emptyList() else listOf("http://localhost:5173"),
                dashboardDir = env["DASHBOARD_DIR"]?.takeIf { it.isNotBlank() },
            )
        }
    }
}
