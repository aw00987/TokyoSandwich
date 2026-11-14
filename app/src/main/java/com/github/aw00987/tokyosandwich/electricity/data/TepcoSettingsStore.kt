package com.github.aw00987.tokyosandwich.electricity.data

import android.content.Context
import com.github.aw00987.tokyosandwich.core.data.encryptedDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/**
 * The TEPCO settings saved on the device. No password is saved: the user logs in on TEPCO's page whenever the saved
 * token has expired. The contract choice, the client ID (sent as `x-kcx-tracking-id`/`X-API-Request-Id`) and the
 * synced data are in the settings.
 */
@Serializable
data class TepcoSettings(
    /** The access token of the last login, kept until it expires or is rejected; never renewed. */
    val accessToken: String? = null,
    /** When [accessToken] expires, in epoch milliseconds. */
    val tokenExpiresAt: Long = 0,
    /** The account's email from the last login's ID token, or null when unknown. */
    val email: String? = null,
) {
    /** The saved token, or null when there is none. */
    val token: TepcoToken? get() = accessToken?.let { TepcoToken(it, tokenExpiresAt) }
}

/** Saves [TepcoSettings] encrypted with the Android Keystore key (`electricity_data.enc`). */
class TepcoSettingsStore(context: Context) {
    private val store =
        encryptedDataStore(
            context,
            "electricity_data.enc",
            TepcoSettings.serializer(),
            TepcoSettings()
        )

    val data: Flow<TepcoSettings> = store.data

    suspend fun current(): TepcoSettings = store.data.first()

    /** Saves a login: its token and, when the ID token had one, the account's email. */
    suspend fun saveLogin(token: TepcoToken, email: String?) {
        store.updateData {
            it.copy(
                accessToken = token.accessToken,
                tokenExpiresAt = token.expiresAt,
                email = email ?: it.email
            )
        }
    }

    suspend fun clearToken() {
        store.updateData { it.copy(accessToken = null, tokenExpiresAt = 0) }
    }

    /** Forgets the login and the email. */
    suspend fun clear() {
        store.updateData { TepcoSettings() }
    }
}
