package org.openaustria.googletv.hermes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Chat-Verlauf mit dem Hermes-Agent. Nachrichten erscheinen sofort im Verlauf; die Anfragen laufen
 * nacheinander, damit jede den vollständigen Verlauf samt vorheriger Antwort mitschickt.
 *
 * Alle Aufrufe laufen auf dem Main-Thread. [scope] ist nur für Tests austauschbar.
 */
class ChatViewModel(
    private val client: HermesClient,
    private val settingsStore: HermesSettingsStore,
    scope: CoroutineScope? = null,
) : ViewModel() {

    private val scope = scope ?: viewModelScope

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val requestLock = Mutex()
    private var nextId = 1L

    /** Hängt [text] als Nutzer-Nachricht an den Verlauf und schickt sie an den Agent. */
    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val message = ChatMessage(nextId++, ChatRole.USER, trimmed, MessageStatus.PENDING)
        _uiState.value = _uiState.value.let { it.copy(messages = it.messages + message, error = null) }
        deliver(message.id)
    }

    /** Sendet die zuletzt fehlgeschlagene Nachricht erneut, ohne sie doppelt in den Verlauf zu legen. */
    fun retry() {
        val state = _uiState.value
        val failed = state.messages.lastOrNull { it.status == MessageStatus.FAILED } ?: return
        _uiState.value = state.copy(
            messages = state.messages.withStatus(failed.id, MessageStatus.PENDING),
            error = null,
        )
        deliver(failed.id)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun deliver(messageId: Long) {
        scope.launch {
            requestLock.withLock {
                val history = historyFor(messageId) ?: return@withLock
                val result = client.send(settingsStore.load(), history)
                onResult(messageId, result)
            }
        }
    }

    /**
     * Verlauf bis einschließlich [messageId]. Fehlgeschlagene und noch wartende Nachrichten davor
     * bleiben draußen — der Agent hat sie nie beantwortet.
     */
    private fun historyFor(messageId: Long): List<ChatMessage>? {
        val messages = _uiState.value.messages
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return null
        return messages.subList(0, index + 1)
            .filter { it.id == messageId || it.status == MessageStatus.SENT }
            .takeLast(MAX_HISTORY)
    }

    private fun onResult(messageId: Long, result: HermesResult) {
        val state = _uiState.value
        val index = state.messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        _uiState.value = when (result) {
            is HermesResult.Success -> {
                val messages = state.messages.withStatus(messageId, MessageStatus.SENT).toMutableList()
                // Direkt hinter die zugehörige Frage: Inzwischen gesprochene Nachrichten stehen schon darunter.
                messages.add(index + 1, ChatMessage(nextId++, ChatRole.AGENT, result.reply))
                state.copy(messages = messages)
            }
            is HermesResult.Failure -> state.copy(
                messages = state.messages.withStatus(messageId, MessageStatus.FAILED),
                error = result.error,
            )
        }
    }

    private fun List<ChatMessage>.withStatus(id: Long, status: MessageStatus): List<ChatMessage> =
        map { if (it.id == id) it.copy(status = status) else it }

    companion object {
        /** Obergrenze für den mitgeschickten Verlauf, damit Anfragen bei langen Sitzungen klein bleiben. */
        const val MAX_HISTORY = 20

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val network = AndroidNetworkMonitor(application)
                ChatViewModel(
                    client = HermesGatewayClient(UrlConnectionTransport(), network::isOnline),
                    settingsStore = SharedPreferencesSettingsStore(application),
                )
            }
        }
    }
}
