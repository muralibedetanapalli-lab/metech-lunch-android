package com.metech.lunch.backend.db

import com.metech.lunch.backend.DbConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import javax.sql.DataSource

class Db(val dataSource: DataSource) {
    val database: Database = Database.connect(dataSource)

    fun migrate() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    /** Runs [block] in one database transaction on the IO dispatcher. */
    suspend fun <T> tx(block: Transaction.() -> T): T =
        withContext(Dispatchers.IO) { transaction(database) { block() } }

    /** Blocking variant for startup code and tests. */
    fun <T> txBlocking(block: Transaction.() -> T): T = transaction(database) { block() }

    companion object {
        fun connect(config: DbConfig): Db {
            val hikari = HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                maximumPoolSize = 10
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
            }
            return Db(HikariDataSource(hikari))
        }
    }
}
