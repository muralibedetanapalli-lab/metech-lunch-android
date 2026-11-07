package com.metech.lunch.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigTest {

    @Test
    fun `remote database URLs get TLS, local ones and explicit sslmode are left alone`() {
        assertEquals(
            "jdbc:postgresql://x.postgres.database.azure.com:5432/db?sslmode=require",
            DbConfig.withTls("jdbc:postgresql://x.postgres.database.azure.com:5432/db"),
        )
        assertEquals(
            "jdbc:postgresql://x.postgres.database.azure.com:5432/db?ApplicationName=a&sslmode=require",
            DbConfig.withTls("jdbc:postgresql://x.postgres.database.azure.com:5432/db?ApplicationName=a"),
        )
        assertEquals(
            "jdbc:postgresql://x.example.com/db?sslmode=verify-full",
            DbConfig.withTls("jdbc:postgresql://x.example.com/db?sslmode=verify-full"),
        )
        assertEquals("jdbc:postgresql://localhost:5433/db", DbConfig.withTls("jdbc:postgresql://localhost:5433/db"))
    }

    @Test
    fun `env file is parsed and real environment variables win`() {
        val file = EnvFile.parse(
            listOf("# comment", "", "DATABASE_USER=fromfile", "DATABASE_PASSWORD=\"p@ss=word\"", "export PORT=9000"),
        )
        assertEquals("p@ss=word", file["DATABASE_PASSWORD"])
        assertEquals("9000", file["PORT"])
        val merged = file + mapOf("DATABASE_USER" to "fromenv")
        assertEquals("fromenv", AppConfig.fromEnv(merged).db.user)
    }

    @Test
    fun `demo data is opt-in and the password never appears in logs`() {
        assertFalse(AppConfig.fromEnv(emptyMap()).seedDemoData)
        assertTrue(AppConfig.fromEnv(mapOf("SEED_DEMO_DATA" to "true")).seedDemoData)
        val db = DbConfig("jdbc:postgresql://h/db", "u", "secret")
        assertFalse("secret" in db.toString())
        assertEquals("h", db.host)
    }

    @Test
    fun `code request limit defaults to 10 and can be changed`() {
        assertEquals(10, AppConfig.fromEnv(emptyMap()).otpMaxPerHour)
        assertEquals(25, AppConfig.fromEnv(mapOf("OTP_MAX_PER_HOUR" to "25")).otpMaxPerHour)
        assertEquals(10, AppConfig.fromEnv(mapOf("OTP_MAX_PER_HOUR" to "lots")).otpMaxPerHour)
    }
}
