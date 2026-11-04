package com.metech.lunch.backend

import com.metech.lunch.backend.db.Db
import com.metech.lunch.backend.dev.DemoSeed
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Runs the API on an embedded Postgres with demo data, no Docker needed:
 *   ./gradlew :backend:runDev
 * Data is kept in backend/build/dev-postgres between runs.
 */
fun main() {
    val dataDir = Paths.get("build", "dev-postgres").toAbsolutePath()
    Files.createDirectories(dataDir)
    val pg = EmbeddedPostgres.builder()
        .setDataDirectory(dataDir)
        .setCleanDataDirectory(false)
        .setPort(54329)
        .start()
    val config = AppConfig.fromEnv().copy(db = DbConfig(pg.getJdbcUrl("postgres", "postgres"), "postgres", "postgres"))
    val db = Db(pg.postgresDatabase).also { it.migrate() }

    // DEV_PRETEND_TIME=09:00 shifts the server clock so it is 09:00 in the kitchen's
    // timezone now, which lets you demo ordering after the real cutoff.
    val clock = System.getenv("DEV_PRETEND_TIME")?.let { pretend ->
        val zone = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.now(zone)
        val target = now.with(LocalTime.parse(pretend))
        Clock.offset(Clock.systemUTC(), Duration.between(now, target))
    } ?: Clock.systemUTC()

    val services = Services(config, db, clock)
    DemoSeed(db, services.clock).run()
    println("Dev API on http://localhost:${config.port}/api  (OTP for every number: ${config.devFixedOtp})")
    println("Kitchen time now: ${clock.instant().atZone(ZoneId.of("Asia/Kolkata"))}")
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(services) }.start(wait = true)
}
