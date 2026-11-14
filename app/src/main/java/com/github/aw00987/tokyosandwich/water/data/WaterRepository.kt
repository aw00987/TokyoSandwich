package com.github.aw00987.tokyosandwich.water.data

import com.github.aw00987.tokyosandwich.core.Texts
import com.github.aw00987.tokyosandwich.core.data.ProviderRepository
import com.github.aw00987.tokyosandwich.core.data.SyncError
import com.github.aw00987.tokyosandwich.core.data.UsageData
import com.github.aw00987.tokyosandwich.core.data.UsageStore
import com.github.aw00987.tokyosandwich.core.isJwtExpired
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.coroutines.cancellation.CancellationException

/**
 * The Tokyo Waterworks account and its data. The login ID and password are saved (encrypted, [SessionStore]), and a
 * sync logs in by itself when the JWT is missing or expired.
 *
 * A sync fetches the contract's whole billing history and turns it into calendar-month records ([waterRecords]).
 */
class WaterRepository(
    private val api: WaterApi,
    private val session: SessionStore,
    store: UsageStore,
    private val texts: Texts,
    scope: CoroutineScope,
    clock: () -> Long = System::currentTimeMillis,
) : ProviderRepository(store, scope, clock) {
    private val loginMutex = Mutex()

    /** The configured login ID, or null when no credentials are saved. */
    val loginId: Flow<String?> = session.loginId

    override val connected: Flow<Boolean> = loginId.map { it != null }

    /**
     * Saves the credentials (a null [password] keeps the saved one) and syncs. Data of a previously saved, different
     * account is deleted first.
     */
    fun login(loginId: String, password: String?): Job = replaceJob {
        sync {
            val pass = password ?: session.credentials()?.password ?: throw NoCredentialsException()
            if (session.saveCredentials(Credentials(loginId.trim(), pass))) forgetAccount()
        }
    }

    /** Deletes the credentials, the session and all saved data, cancelling a running sync. */
    override fun logout(): Job = replaceJob {
        finish()
        session.clearCredentials()
        forgetAccount()
    }

    override fun syncError(e: Exception): SyncError = when (e) {
        is NoCredentialsException -> SyncError.NoCredentials
        is LoginFailedException -> SyncError.LoginFailed(e.code)
        is NoWaterContractException -> SyncError.NoContract
        else -> SyncError.Failed(e.message)
    }

    /**
     * Fetches all data of the selected contract (the first contract unless another one is chosen in settings): the
     * contract list, the full billing history (the service returns every period it keeps; there is no date range), the
     * latest reading and every period's detail. The session is checked first, so a failed login stops here.
     */
    override suspend fun fetch(): UsageData {
        validToken()
        val contracts = call<List<ContractDto>>("user/usagedata").map { it.toDomain() }
        val contract = chooseContract(contracts, store.selectedContract())
            ?: throw NoWaterContractException()
        val history = call<List<UsedDataDto>>("user/useddata/${contract.key}")
        val latest =
            call<MeterDataDto>("user/meterdata/${contract.key}", mapOf("jisseki_id" to "latest"))
        val periods = loadPeriods(contract.key, history, latest)
        val infos = contracts.mapIndexed { index, it -> it.toInfo(texts, isDefault = index == 0) }
        return UsageData(
            contract = infos[contracts.indexOf(contract)],
            contracts = infos,
            records = waterRecords(periods, texts),
        )
    }

    /**
     * Each history item only has billing months and totals; its reading dates and the fixed/volume split come from
     * `/user/meterdata?jisseki_id=<remark2>`. Items whose detail cannot be loaded fall back to estimated dates.
     */
    private suspend fun loadPeriods(
        contractKey: String,
        history: List<UsedDataDto>,
        latest: MeterDataDto
    ): List<WaterPeriod> {
        val limit = Semaphore(DETAIL_CONCURRENCY)
        val periods = coroutineScope {
            history.map { item ->
                async {
                    val detail = when {
                        latest.seiDateSt1.trim()
                            .isNotEmpty() && latest.seiDateSt1.trim() == item.seiDateStart.trim() -> latest

                        item.remark2.isBlank() -> null
                        else -> limit.withPermit { loadDetail(contractKey, item.remark2.trim()) }
                    }
                    detail?.toPeriod() ?: item.toEstimatedPeriod()
                }
            }.awaitAll().filterNotNull()
        }
        return resolveOverlaps(periods)
    }

    private suspend fun loadDetail(contractKey: String, recordId: String): MeterDataDto? = try {
        call<MeterDataDto>("user/meterdata/$contractKey", mapOf("jisseki_id" to recordId))
    } catch (e: CancellationException) {
        throw e
    } catch (e: NoCredentialsException) {
        throw e
    } catch (e: LoginFailedException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Calls the API with a valid token (re-logging in once if it is rejected) and saves the refreshed token. */
    private suspend inline fun <reified T> call(
        path: String,
        query: Map<String, String> = emptyMap()
    ): T {
        val envelope = try {
            api.get(path, validToken(), query)
        } catch (_: SessionExpiredException) {
            api.get(path, validToken(forceLogin = true), query)
        }
        envelope.token?.takeIf { it.isNotBlank() }?.let { session.saveToken(it) }
        return WaterApi.json.decodeFromJsonElement(envelope.data)
    }

    /**
     * A usable JWT: the saved one unless it is missing or expired, otherwise a new one from logging in with the saved
     * credentials. Throws [NoCredentialsException] or [LoginFailedException].
     */
    private suspend fun validToken(forceLogin: Boolean = false): String = loginMutex.withLock {
        if (!forceLogin) {
            session.token()?.takeUnless { isJwtExpired(it, clock() / 1000) }
                ?.let { return it }
        }
        session.clearToken()
        val credentials = session.credentials() ?: throw NoCredentialsException()
        api.login(credentials.loginId, credentials.password).also { session.saveToken(it) }
    }

    private companion object {
        const val DETAIL_CONCURRENCY = 4
    }
}

private class NoWaterContractException : Exception("No water contract")
