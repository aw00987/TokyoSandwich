package com.github.aw00987.tokyosandwich.gas.data

import com.github.aw00987.tokyosandwich.core.Texts
import com.github.aw00987.tokyosandwich.core.data.ProviderRepository
import com.github.aw00987.tokyosandwich.core.data.SyncError
import com.github.aw00987.tokyosandwich.core.data.UsageData
import com.github.aw00987.tokyosandwich.core.data.UsageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The myTOKYOGAS account and its data. The login ID, password and session cookies are saved (encrypted,
 * [GasAccountStore]). A sync uses the saved session, and logs in by itself when there is none or the service rejects
 * it ([GasLogin], Tokyo Gas's login page in a hidden WebView).
 *
 * A sync fetches every bill of the contract and turns it into billing-month records ([gasRecords]).
 */
class GasRepository(
    private val api: TokyoGasApi,
    private val account: GasAccountStore,
    store: UsageStore,
    private val webLogin: GasLogin,
    private val texts: Texts,
    scope: CoroutineScope,
    clock: () -> Long = System::currentTimeMillis,
) : ProviderRepository(store, scope, clock) {
    /** The saved login ID, or null when no account is saved. */
    val loginId: Flow<String?> = account.data.map { it.loginId }.distinctUntilChanged()

    override val connected: Flow<Boolean> = loginId.map { it != null }

    /**
     * Saves the account (a null [password] keeps the saved one) and syncs with a fresh login. Data of a previously
     * saved, different account is deleted first.
     */
    fun login(loginId: String, password: String?): Job = replaceJob {
        sync { if (account.saveCredentials(loginId, password)) forgetAccount() }
    }

    /** Deletes the account, its session and all saved data. Sends no request. */
    override fun logout(): Job = replaceJob {
        finish()
        account.clear()
        forgetAccount()
    }

    override fun syncError(e: Exception): SyncError = when (e) {
        is NoGasCredentialsException -> SyncError.NoCredentials
        is GasLoginFailedException -> SyncError.LoginFailed(e.reason.name, e.detail)
        is NoGasContractException -> SyncError.NoContract
        is GasBusyException -> SyncError.Busy
        else -> SyncError.Failed(e.message)
    }

    /**
     * Fetches with the saved session; when there is none, or the service rejects it, logs in and tries once more. A
     * session that a fresh login just gave and that is rejected anyway is a failed login. The session is saved as the
     * service last renewed it (the app itself never renews it).
     */
    override suspend fun fetch(): UsageData {
        GasSession.fromCookieHeader(account.current().cookies)?.let { saved ->
            try {
                return fetchUsage(saved)
            } catch (_: GasSessionExpiredException) {
                account.saveCookies(null)
            }
        }
        val fresh = logIn()
        return try {
            fetchUsage(fresh)
        } catch (_: GasSessionExpiredException) {
            account.saveCookies(null)
            throw GasLoginFailedException(GasLoginFailure.Rejected, TokyoGasApi.UNAUTHENTICATED)
        }
    }

    private suspend fun logIn(): GasSession {
        val saved = account.current()
        val loginId = saved.loginId
        val password = saved.password
        if (loginId.isNullOrEmpty() || password.isNullOrEmpty()) throw NoGasCredentialsException()
        val cookies = webLogin.login(loginId, password)
        return (GasSession.fromCookieHeader(cookies)
            ?: throw GasLoginFailedException(GasLoginFailure.PageNotRecognised, "no_session"))
            .also { account.saveCookies(it.header()) }
    }

    /** Read-only, one request at a time: the contracts (G1), then every billing period of the chosen one (G2). */
    private suspend fun fetchUsage(session: GasSession): UsageData {
        val contracts = TokyoGasApi.json.decodeFromJsonElement(
            CustomerNumbersDto.serializer(),
            api.query(session, "CustomerNumbers", CUSTOMER_NUMBERS_QUERY),
        ).gasContracts
        val contract = chooseContract(contracts, store.selectedContract()?.toIntOrNull())
            ?: throw NoGasContractException()
        val charge = TokyoGasApi.json.decodeFromJsonElement(
            GasChargeDto.serializer(),
            api.query(
                session,
                "GasElectricityCharge",
                GAS_CHARGE_QUERY,
                JsonObject(mapOf("contractIndexNumber" to JsonPrimitive(contract.index))),
            ),
        )
        account.saveCookies(session.header())
        return UsageData(
            contract = contract.toInfo(texts),
            contracts = contracts.map { it.toInfo(texts) },
            records = gasRecords(charge.toPeriods(), texts),
        )
    }
}

private class NoGasContractException : Exception("No gas contract")

private class NoGasCredentialsException : Exception("No Tokyo Gas account saved")
