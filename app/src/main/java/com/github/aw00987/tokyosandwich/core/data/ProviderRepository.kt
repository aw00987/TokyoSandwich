package com.github.aw00987.tokyosandwich.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Why the last sync failed. The saved data is unchanged in every case. */
sealed interface SyncError {
    /** No account is saved, so there is nothing to log in with. */
    data object NoCredentials : SyncError

    /** The session has ended and the app cannot log in by itself: the user has to log in again. */
    data object LoginRequired : SyncError

    /** The login failed; [code] is the provider's reason and [detail] its own text, when it gave one. */
    data class LoginFailed(val code: String, val detail: String? = null) : SyncError

    /** The account has no contract of this utility. */
    data object NoContract : SyncError

    /** The service is busy; trying again later helps. */
    data object Busy : SyncError

    /** Network, service or processing error. */
    data class Failed(val message: String?) : SyncError
}

/**
 * True when the user has to log in (again) on the provider's settings page before a sync can work: no account is
 * saved, the session has ended, or the service refused the login.
 */
val SyncError.needsLogin: Boolean
    get() = this == SyncError.NoCredentials || this == SyncError.LoginRequired || this is SyncError.LoginFailed

/** [running] while a sync is in progress; [error] is the last sync's failure until it is dismissed. */
data class SyncStatus(val running: Boolean = false, val error: SyncError? = null)

/**
 * What every provider's repository has in common. The saved data ([usage]) is the app's source of truth: reading it
 * never touches the network. The network is used only by the sync operations, which run in the app-wide [scope] (so
 * they finish even when the screen that started them goes away) and never run at the same time.
 *
 * A provider adds its own account handling and implements [fetch]: everything from the API's answers to a finished
 * [UsageData]. The result must not depend on what was saved before. A sync saves it in one write: its months replace
 * the saved ones of the same month, the saved months it did not return stay, and the rest replaces what was saved. A
 * failed sync keeps the old data.
 */
abstract class ProviderRepository(
    /** The provider's saved data and settings (the contract choice, the client ID). */
    protected val store: UsageStore,
    protected val scope: CoroutineScope,
    protected val clock: () -> Long = System::currentTimeMillis,
) {
    private val jobLock = Any()
    private var job: Job? = null

    /** The saved data of the selected contract, or null when none is saved. */
    val usage: Flow<UsageData?> = store.data.map { it.data }.distinctUntilChanged()

    /** When the saved data was fetched, in epoch milliseconds, or null when none is saved. */
    val syncedAt: Flow<Long?> =
        store.data.map { saved -> saved.syncedAt.takeIf { saved.data != null } }
            .distinctUntilChanged()

    /** False while the user has not set the provider up (no account saved, or never logged in). */
    abstract val connected: Flow<Boolean>

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Downloads everything again. If a sync is already running, returns that one instead of starting another. */
    fun refresh(): Job = synchronized(jobLock) {
        job?.takeIf { it.isActive } ?: scope.launch { sync() }.also { job = it }
    }

    /**
     * Selects the contract to show (null = the provider's default), deletes the previous one's data and syncs. A sync
     * that cannot run (e.g. TEPCO without a valid token) ends with its error, and the next one fetches the new contract.
     */
    fun selectContract(id: String?): Job = replaceJob {
        sync {
            store.selectContract(id)
            store.clear()
        }
    }

    /** Deletes the account and all saved data. Sends no request. */
    abstract fun logout(): Job

    fun dismissError() = _status.update { it.copy(error = null) }

    /** Starts [block] after cancelling (and waiting for) the running operation. */
    protected fun replaceJob(block: suspend () -> Unit): Job = synchronized(jobLock) {
        val previous = job
        scope.launch {
            previous?.cancelAndJoin()
            block()
        }.also { job = it }
    }

    /** Runs [prepare] and then a sync: [fetch], and one write that merges its result into the saved data. */
    protected suspend fun sync(prepare: suspend () -> Unit = {}) {
        _status.value = SyncStatus(running = true)
        val error = try {
            prepare()
            store.save(fetch(), clock())
            null
        } catch (e: CancellationException) {
            throw e // replaced by another operation, which sets the status itself
        } catch (e: Exception) {
            syncError(e)
        }
        _status.value = SyncStatus(error = error)
    }

    /** Ends with [error] (or with none) without syncing. */
    protected fun finish(error: SyncError? = null) {
        _status.value = SyncStatus(error = error)
    }

    protected suspend fun clearData() = store.clear()

    /** Deletes the data and the contract choice: the account is gone or another one. */
    protected suspend fun forgetAccount() = store.forgetAccount()

    /** Logs in if needed, calls the API and processes the answers into the data to save. */
    protected abstract suspend fun fetch(): UsageData

    /** What a failure of [fetch] (or of a sync's preparation) means to the user. */
    protected abstract fun syncError(e: Exception): SyncError
}
