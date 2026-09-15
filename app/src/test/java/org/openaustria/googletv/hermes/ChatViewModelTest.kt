package org.openaustria.googletv.hermes

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelTest {

    /** Antwortet erst, wenn der Test [respond] aufruft — so lassen sich laufende Anfragen prüfen. */
    private class FakeClient : HermesClient {
        val requests = mutableListOf<List<ChatMessage>>()
        val settings = mutableListOf<HermesSettings>()
        private val pending = ArrayDeque<CompletableDeferred<HermesResult>>()

        override suspend fun send(settings: HermesSettings, history: List<ChatMessage>): HermesResult {
            this.settings += settings
            requests += history
            val deferred = CompletableDeferred<HermesResult>()
            pending.addLast(deferred)
            return deferred.await()
        }

        fun respond(result: HermesResult) {
            pending.removeFirst().complete(result)
        }
    }

    private class FakeStore(var settings: HermesSettings) : HermesSettingsStore {
        override fun load() = settings
        override fun save(settings: HermesSettings) {
            this.settings = settings
        }
    }

    private val client = FakeClient()
    private val store = FakeStore(HermesSettings(endpoint = "http://hermes.local:8642", token = "geheim"))
    private val viewModel = ChatViewModel(client, store, CoroutineScope(Dispatchers.Unconfined))

    private val state get() = viewModel.uiState.value
    private val texts get() = state.messages.map { it.text }

    @Test
    fun `initial state is empty`() {
        assertTrue(state.messages.isEmpty())
        assertNull(state.error)
        assertFalse(state.isSending)
    }

    @Test
    fun `sent message appears immediately and reply follows`() {
        viewModel.send("  Wie spät ist es? ")
        assertEquals(listOf("Wie spät ist es?"), texts)
        assertEquals(MessageStatus.PENDING, state.messages.single().status)
        assertTrue(state.isSending)
        assertEquals(store.settings, client.settings.single())

        client.respond(HermesResult.Success("Es ist 20 Uhr."))
        assertEquals(listOf("Wie spät ist es?", "Es ist 20 Uhr."), texts)
        assertEquals(listOf(ChatRole.USER, ChatRole.AGENT), state.messages.map { it.role })
        assertTrue(state.messages.all { it.status == MessageStatus.SENT })
        assertFalse(state.isSending)
    }

    @Test
    fun `blank text is ignored`() {
        viewModel.send("   ")
        assertTrue(state.messages.isEmpty())
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `follow-up request carries previous conversation`() {
        viewModel.send("Wie spät ist es?")
        client.respond(HermesResult.Success("Es ist 20 Uhr."))
        viewModel.send("Und das Wetter?")

        assertEquals(listOf("Wie spät ist es?", "Es ist 20 Uhr.", "Und das Wetter?"), client.requests[1].map { it.text })
    }

    @Test
    fun `messages sent while waiting are queued and replies stay next to their question`() {
        viewModel.send("eins")
        viewModel.send("zwei")
        assertEquals(1, client.requests.size)
        assertEquals(listOf("eins", "zwei"), texts)

        client.respond(HermesResult.Success("antwort eins"))
        assertEquals(listOf("eins", "antwort eins", "zwei"), texts)
        assertEquals(listOf("eins", "antwort eins", "zwei"), client.requests[1].map { it.text })
        assertTrue(state.isSending)

        client.respond(HermesResult.Success("antwort zwei"))
        assertEquals(listOf("eins", "antwort eins", "zwei", "antwort zwei"), texts)
        assertFalse(state.isSending)
    }

    @Test
    fun `failure marks message and exposes error`() {
        viewModel.send("Hallo")
        client.respond(HermesResult.Failure(HermesError.TIMEOUT))

        assertEquals(HermesError.TIMEOUT, state.error)
        assertEquals(MessageStatus.FAILED, state.messages.single().status)
        assertFalse(state.isSending)
    }

    @Test
    fun `retry resends failed message without duplicating it`() {
        viewModel.send("Hallo")
        client.respond(HermesResult.Failure(HermesError.OFFLINE))

        viewModel.retry()
        assertNull(state.error)
        assertEquals(MessageStatus.PENDING, state.messages.single().status)
        assertEquals(listOf("Hallo"), client.requests[1].map { it.text })

        client.respond(HermesResult.Success("Hallo zurück"))
        assertEquals(listOf("Hallo", "Hallo zurück"), texts)
        assertTrue(state.messages.all { it.status == MessageStatus.SENT })
    }

    @Test
    fun `retry without failed message does nothing`() {
        viewModel.retry()
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `retry of an older message moves it to the end so the reply lands at the autoscroll target`() {
        viewModel.send("A")
        client.respond(HermesResult.Failure(HermesError.UNREACHABLE))
        viewModel.send("B")
        client.respond(HermesResult.Success("antwort B"))

        viewModel.retry()
        assertEquals(listOf("B", "antwort B", "A"), texts)
        assertEquals(listOf("B", "antwort B", "A"), client.requests.last().map { it.text })
        assertEquals(state.messages.last().id, state.newestMessageId)

        client.respond(HermesResult.Success("antwort A"))
        assertEquals(listOf("B", "antwort B", "A", "antwort A"), texts)
        assertEquals(state.messages.last().id, state.newestMessageId)
    }

    @Test
    fun `every failed message can be retried by id`() {
        viewModel.send("eins")
        client.respond(HermesResult.Failure(HermesError.TIMEOUT))
        viewModel.send("zwei")
        client.respond(HermesResult.Failure(HermesError.TIMEOUT))

        viewModel.retry(state.messages.first().id)
        assertEquals(listOf("zwei", "eins"), texts)
        assertEquals(listOf(MessageStatus.FAILED, MessageStatus.PENDING), state.messages.map { it.status })
        assertEquals(listOf("eins"), client.requests.last().map { it.text })
    }

    @Test
    fun `retry by id ignores messages that did not fail`() {
        viewModel.send("eins")
        client.respond(HermesResult.Success("ok"))
        client.requests.clear()

        viewModel.retry(state.messages.first().id)
        assertTrue(client.requests.isEmpty())
        assertEquals(listOf("eins", "ok"), texts)
    }

    @Test
    fun `retrying the same message again is a new autoscroll target`() {
        viewModel.send("Hallo")
        client.respond(HermesResult.Failure(HermesError.TIMEOUT))
        viewModel.retry()
        val firstRetry = state.newestMessageId
        client.respond(HermesResult.Failure(HermesError.TIMEOUT))
        viewModel.retry()

        assertNotEquals(firstRetry, state.newestMessageId)
        assertEquals(state.messages.single().id, state.newestMessageId)
    }

    @Test
    fun `autoscroll target follows the latest added message`() {
        assertNull(state.newestMessageId)
        viewModel.send("eins")
        assertEquals(state.messages[0].id, state.newestMessageId)
        viewModel.send("zwei")
        assertEquals(state.messages[1].id, state.newestMessageId)

        client.respond(HermesResult.Success("antwort eins"))
        assertEquals("antwort eins", state.messages.single { it.id == state.newestMessageId }.text)
        client.respond(HermesResult.Success("antwort zwei"))
        assertEquals(state.messages.last().id, state.newestMessageId)
    }

    @Test
    fun `same error twice in a row is a new error event`() {
        viewModel.send("eins")
        client.respond(HermesResult.Failure(HermesError.NOT_CONFIGURED))
        val first = state.errorId
        viewModel.send("zwei")
        client.respond(HermesResult.Failure(HermesError.NOT_CONFIGURED))

        assertEquals(HermesError.NOT_CONFIGURED, state.error)
        assertNotEquals(first, state.errorId)
    }

    @Test
    fun `failed messages are left out of later history`() {
        viewModel.send("verloren")
        client.respond(HermesResult.Failure(HermesError.UNREACHABLE))
        viewModel.send("neu")

        assertEquals(listOf("neu"), client.requests[1].map { it.text })
        assertEquals(listOf("verloren", "neu"), texts)
    }

    @Test
    fun `new message and dismiss clear the error`() {
        viewModel.send("eins")
        client.respond(HermesResult.Failure(HermesError.UNAUTHORIZED))
        viewModel.dismissError()
        assertNull(state.error)

        client.requests.clear()
        viewModel.retry()
        client.respond(HermesResult.Failure(HermesError.SERVER))
        assertEquals(HermesError.SERVER, state.error)
        viewModel.send("zwei")
        assertNull(state.error)
    }

    @Test
    fun `settings are read for every request`() {
        viewModel.send("eins")
        client.respond(HermesResult.Success("ok"))
        store.settings = HermesSettings(endpoint = "http://anderer.host:8642", token = "neu")
        viewModel.send("zwei")

        assertEquals("http://anderer.host:8642", client.settings[1].endpoint)
    }

    @Test
    fun `history is limited to the most recent messages`() {
        repeat(ChatViewModel.MAX_HISTORY) { i ->
            viewModel.send("frage $i")
            client.respond(HermesResult.Success("antwort $i"))
        }
        viewModel.send("letzte")

        val history = client.requests.last()
        assertEquals(ChatViewModel.MAX_HISTORY, history.size)
        assertEquals("letzte", history.last().text)
    }
}
