package com.metech.lunch.core.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.metech.lunch.core.model.AuthTokens
import com.metech.lunch.core.network.TokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/**
 * Persists auth tokens.
 * TODO: move tokens to Keystore-backed encrypted storage before launch
 * (the scope requires secure secret handling).
 */
class SessionStore(context: Context, scope: CoroutineScope) : TokenProvider {

    private val dataStore = context.applicationContext.sessionDataStore

    val tokens: StateFlow<AuthTokens?> = dataStore.data
        .map { prefs ->
            val access = prefs[ACCESS_TOKEN]
            val refresh = prefs[REFRESH_TOKEN]
            if (access != null && refresh != null) AuthTokens(access, refresh) else null
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Reads storage directly; use at startup before [tokens] has emitted. */
    suspend fun hasSession(): Boolean = dataStore.data.first()[ACCESS_TOKEN] != null

    suspend fun save(tokens: AuthTokens) {
        dataStore.edit {
            it[ACCESS_TOKEN] = tokens.accessToken
            it[REFRESH_TOKEN] = tokens.refreshToken
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    // ---- TokenProvider (called from OkHttp threads, never the main thread) ----

    override fun accessToken(): String? = tokens.value?.accessToken
        ?: runBlocking { dataStore.data.first()[ACCESS_TOKEN] }

    override fun refreshToken(): String? = runBlocking { dataStore.data.first()[REFRESH_TOKEN] }

    override fun onTokensRefreshed(tokens: AuthTokens) = runBlocking { save(tokens) }

    override fun onSessionExpired() = runBlocking { clear() }

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }
}
