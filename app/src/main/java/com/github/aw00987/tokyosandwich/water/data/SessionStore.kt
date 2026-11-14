package com.github.aw00987.tokyosandwich.water.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.aw00987.tokyosandwich.core.KeystoreCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Base64

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/** Login credentials for the official Tokyo Waterworks web app. */
data class Credentials(val loginId: String, val password: String)

/**
 * Persists the Tokyo Waterworks login credentials and JWT (the contract choice is in the settings), each value encrypted with an Android Keystore key.
 * The data never leaves the device (backups are disabled in the manifest).
 */
class SessionStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("water_token")
    private val loginIdKey = stringPreferencesKey("water_login_id")
    private val passwordKey = stringPreferencesKey("water_password")

    /** The saved login ID (email), or null if no credentials are saved. */
    val loginId: Flow<String?> = context.sessionDataStore.data
        .map { prefs -> prefs[loginIdKey]?.let(::decrypt)?.takeIf { prefs[passwordKey] != null } }
        .distinctUntilChanged()

    suspend fun credentials(): Credentials? {
        val prefs = context.sessionDataStore.data.first()
        val loginId = prefs[loginIdKey]?.let(::decrypt) ?: return null
        val password = prefs[passwordKey]?.let(::decrypt) ?: return null
        return Credentials(loginId, password)
    }

    /**
     * Saves new credentials and drops the token issued for the previous ones. Returns true if they belong to a different
     * account than the saved ones (whose contract choice the caller then drops).
     */
    suspend fun saveCredentials(credentials: Credentials): Boolean {
        var accountChanged = false
        context.sessionDataStore.edit {
            val sameAccount =
                it[loginIdKey]?.let(::decrypt).equals(credentials.loginId, ignoreCase = true)
            it[loginIdKey] = encrypt(credentials.loginId)
            it[passwordKey] = encrypt(credentials.password)
            it.remove(tokenKey)
            accountChanged = !sameAccount
        }
        return accountChanged
    }

    suspend fun clearCredentials() {
        context.sessionDataStore.edit {
            it.remove(loginIdKey)
            it.remove(passwordKey)
            it.remove(tokenKey)
        }
    }

    suspend fun token(): String? =
        context.sessionDataStore.data.first()[tokenKey]?.let(::decrypt)

    suspend fun saveToken(token: String) {
        context.sessionDataStore.edit { it[tokenKey] = encrypt(token) }
    }

    suspend fun clearToken() {
        context.sessionDataStore.edit { it.remove(tokenKey) }
    }

    private fun encrypt(plain: String): String =
        Base64.getEncoder().encodeToString(KeystoreCipher.encrypt(plain.toByteArray()))

    /** Returns null if the data cannot be decrypted (e.g. the key was invalidated). */
    private fun decrypt(encoded: String): String? =
        runCatching {
            String(
                KeystoreCipher.decrypt(
                    Base64.getDecoder().decode(encoded)
                )
            )
        }.getOrNull()
}
