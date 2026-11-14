package com.github.aw00987.tokyosandwich.electricity.data

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
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException

/**
 * The TEPCO account and its data. The app does not log in by itself: the user logs in on TEPCO's own page, and the
 * app saves that login's token (valid for 30 minutes, encrypted, never renewed) and the account's email. A sync uses
 * the saved token; once it has expired or is rejected, the sync ends with [SyncError.LoginRequired]. No password is
 * stored.
 *
 * A sync fetches the billed months the service returns (the latest 25) and turns them into billing-month records
 * ([tepcoRecords]).
 */
class ElectricityRepository(
    private val api: TepcoApi,
    private val settings: TepcoSettingsStore,
    store: UsageStore,
    private val texts: Texts,
    scope: CoroutineScope,
    clock: () -> Long = System::currentTimeMillis,
) : ProviderRepository(store, scope, clock) {
    /** There is no saved account: TEPCO counts as set up once data is saved. */
    override val connected: Flow<Boolean> = usage.map { it != null }

    /** The account's email from the last login, or null when unknown. */
    val email: Flow<String?> = settings.data.map { it.email }.distinctUntilChanged()

    /** Redeems the code from TEPCO's login page and syncs. */
    fun login(code: String, codeVerifier: String): Job = replaceJob {
        sync {
            settings.clearToken()
            val login = try {
                api.exchangeCode(code, codeVerifier, clock())
            } catch (e: TepcoLoginFailedException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw TepcoLoginFailedException(e.message ?: "network")
            }
            settings.saveLogin(login.token, login.email)
        }
    }

    /** The login page itself reported an error (e.g. the user cancelled, or the callback did not match). */
    fun loginFailed(code: String) = finish(SyncError.LoginFailed(code))

    /** Deletes the login, the email, the contract choice and all saved data. Sends no request. */
    override fun logout(): Job = replaceJob {
        finish()
        settings.clear()
        forgetAccount()
    }

    override fun syncError(e: Exception): SyncError = when (e) {
        is TepcoSessionExpiredException -> SyncError.LoginRequired

        is TepcoLoginFailedException -> SyncError.LoginFailed(e.code)
        is NoElectricContractException -> SyncError.NoContract
        is TepcoBusyException -> SyncError.Busy
        else -> SyncError.Failed(e.message)
    }

    /**
     * Read-only, one request at a time: contracts (C1), the period in progress (B1) and its actual usage so far (B2),
     * the previous billing month's period dates (B2 for `month - 1`, optional) and the billed months (B3).
     */
    override suspend fun fetch(): UsageData {
        val accessToken =
            settings.current().token?.takeUnless { it.isExpired(clock()) }?.accessToken
                ?: throw TepcoSessionExpiredException()
        return try {
            fetchWith(accessToken)
        } catch (e: TepcoSessionExpiredException) {
            settings.clearToken() // no renewal: the user logs in again when they want newer data
            throw e
        }
    }

    private suspend fun fetchWith(accessToken: String): UsageData {
        val clientId = store.clientId()
        suspend fun get(path: String, query: Map<String, String>) =
            api.get(path, accessToken, clientId, query)

        val list = TepcoApi.json.decodeFromString<ContractListDto>(
            get("contract/list", mapOf("readOffset" to "0"))
        )
        val contracts = list.electricContracts
        val contract = chooseContract(contracts, store.selectedContract())
            ?: throw NoElectricContractException()
        val common = mapOf(
            "contractClass" to contract.contractClass,
            "contractNum" to contract.contractNum,
            "readOffset" to list.readOffset,
        )
        val meter =
            TepcoApi.json.decodeFromString<BillingMeterDto>(get("billing/meter", common)).meterInfo
        val current = TepcoApi.json.decodeFromString<BillingMonthDto>(
            get("billing/month", common + ("month" to meter.month))
        ).toPeriod(meter)
        // Only for the dates of the latest billed period (B3 has none), so its daily average is known.
        val previousMonth = current?.billingMonth?.minusMonths(1)
        val previousDates = previousMonth?.let { month ->
            runCatching {
                TepcoApi.json.decodeFromString<BillingMonthDto>(
                    get("billing/month", common + ("month" to month.format(MONTH)))
                ).periodDates()
            }.getOrElse { if (it is CancellationException || it is TepcoSessionExpiredException) throw it else null }
        }
        val history = TepcoApi.json.decodeFromString<MonthHistoryDto>(
            get("billing/month-history", common + ("term" to "25"))
        )
        val months = fillPeriodDates(
            history.toMonths(),
            current,
            known = if (previousMonth != null && previousDates != null) {
                mapOf(previousMonth to previousDates)
            } else emptyMap(),
        )
        return UsageData(
            contract = contract.toDomain().toInfo(texts),
            contracts = contracts.map { it.toDomain().toInfo(texts) },
            records = tepcoRecords(months, texts),
            soFar = current?.toSoFar(),
        )
    }

    private companion object {
        val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMM")
    }
}

private class NoElectricContractException : Exception("No electricity contract")
