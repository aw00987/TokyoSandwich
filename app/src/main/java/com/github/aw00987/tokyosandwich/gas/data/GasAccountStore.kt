package com.github.aw00987.tokyosandwich.gas.data

import android.content.Context
import com.github.aw00987.tokyosandwich.core.data.encryptedDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/**
 * The user's Tokyo Gas account as saved on the device: the login ID and password for the app's automatic login, the
 * session cookies of the last login. The contract choice and the synced data are in the settings.
 */
@Serializable
data class GasAccount(
    /** myTOKYOGAS login ID (email or initial ID), or null when no account is saved. */
    val loginId: String? = null,
    val password: String? = null,
    /** The `members.tokyo-gas.co.jp` cookies of the last login (a `Cookie` header), or null when there is none. */
    val cookies: String? = null,
)

/** Saves [GasAccount] encrypted with the Android Keystore key (`gas_data.enc`). */
class GasAccountStore(context: Context) {
    private val store =
        encryptedDataStore(context, "gas_data.enc", GasAccount.serializer(), GasAccount())

    val data: Flow<GasAccount> = store.data

    suspend fun current(): GasAccount = store.data.first()

    /**
     * Saves the account; a null [password] keeps the saved one. A different login ID is another account: returns true
     * in that case, so the caller deletes that account's data and contract choice.
     */
    suspend fun saveCredentials(loginId: String, password: String?): Boolean {
        var changed = false
        store.updateData { old ->
            changed = old.loginId != loginId
            val base = if (changed) GasAccount() else old
            base.copy(
                loginId = loginId,
                password = password ?: old.password.takeUnless { changed },
                cookies = null, // the next sync logs in with these credentials
            )
        }
        return changed
    }

    /** Saves the session cookies (a `Cookie` header), or deletes them when null. */
    suspend fun saveCookies(cookies: String?) {
        store.updateData { it.copy(cookies = cookies) }
    }

    /** Deletes the account and its session. */
    suspend fun clear() {
        store.updateData { GasAccount() }
    }
}
