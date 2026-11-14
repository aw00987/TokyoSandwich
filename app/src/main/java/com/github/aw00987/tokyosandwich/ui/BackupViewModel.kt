package com.github.aw00987.tokyosandwich.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aw00987.tokyosandwich.core.data.BackupException
import com.github.aw00987.tokyosandwich.core.data.BackupProblem
import com.github.aw00987.tokyosandwich.core.data.BackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** What an export or import did, to tell the user once. */
sealed interface BackupEvent {
    data object Exported : BackupEvent

    data object Imported : BackupEvent

    /** Nothing was changed: the file could not be read or written ([problem] null), or is not an export. */
    data class Failed(val problem: BackupProblem?) : BackupEvent
}

/** Writes an export to, and imports one from, a file the user picked (a document `Uri`). */
class BackupViewModel(
    private val backup: BackupService,
    private val resolver: ContentResolver,
) : ViewModel() {
    private val _running = MutableStateFlow(false)

    /** True while an export or import runs; the buttons wait meanwhile. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events: Flow<BackupEvent> = _events.receiveAsFlow()

    fun export(uri: Uri) = run(BackupEvent.Exported) {
        val text = backup.export()
        withContext(Dispatchers.IO) {
            resolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) }
                ?: error("cannot open $uri")
        }
    }

    fun import(uri: Uri) = run(BackupEvent.Imported) {
        val text = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("cannot open $uri")
        }
        backup.import(text)
    }

    private fun run(done: BackupEvent, block: suspend () -> Unit) {
        if (_running.value) return
        _running.value = true
        viewModelScope.launch {
            val event = try {
                block()
                done
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                BackupEvent.Failed(e.problem)
            } catch (_: Exception) {
                BackupEvent.Failed(null)
            } finally {
                _running.value = false
            }
            _events.send(event)
        }
    }
}
