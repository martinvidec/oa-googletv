package org.openaustria.googletv.hermes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class HermesGatewayClientTest {

    private class FakeTransport(var respond: () -> HttpResponse = { HttpResponse(200, reply("Hallo!")) }) : HttpTransport {
        var calls = 0
        var url: String? = null
        var headers: Map<String, String> = emptyMap()
        var body: String? = null

        override fun post(url: String, headers: Map<String, String>, body: String): HttpResponse {
            calls++
            this.url = url
            this.headers = headers
            this.body = body
            return respond()
        }
    }

    private val transport = FakeTransport()
    private var online = true
    private val client = HermesGatewayClient(transport, { online }, Dispatchers.Unconfined)

    private val settings = HermesSettings(endpoint = "http://hermes.local:8642", token = "geheim")
    private val history = listOf(
        ChatMessage(1, ChatRole.USER, "Wie spät ist es?"),
        ChatMessage(2, ChatRole.AGENT, "Es ist 20 Uhr."),
        ChatMessage(3, ChatRole.USER, "Und das Wetter?", MessageStatus.PENDING),
    )

    private fun send(settings: HermesSettings = this.settings) = runBlocking { client.send(settings, history) }

    @Test
    fun `sends history as openai chat request with bearer token`() {
        assertEquals(HermesResult.Success("Hallo!"), send())

        assertEquals("http://hermes.local:8642/v1/chat/completions", transport.url)
        assertEquals("Bearer geheim", transport.headers["Authorization"])

        val body = JSONObject(transport.body!!)
        assertEquals(HermesGatewayClient.MODEL, body.getString("model"))
        assertFalse(body.getBoolean("stream"))
        val messages = body.getJSONArray("messages")
        assertEquals(3, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        assertEquals("assistant", messages.getJSONObject(1).getString("role"))
        assertEquals("Es ist 20 Uhr.", messages.getJSONObject(1).getString("content"))
        assertEquals("Und das Wetter?", messages.getJSONObject(2).getString("content"))
    }

    @Test
    fun `blank token sends no authorization header`() {
        send(settings.copy(token = " "))
        assertNull(transport.headers["Authorization"])
    }

    @Test
    fun `missing endpoint fails without request`() {
        assertEquals(HermesResult.Failure(HermesError.NOT_CONFIGURED), send(HermesSettings()))
        assertEquals(0, transport.calls)
    }

    @Test
    fun `offline device fails without request`() {
        online = false
        assertEquals(HermesResult.Failure(HermesError.OFFLINE), send())
        assertEquals(0, transport.calls)
    }

    @Test
    fun `timeout is reported as timeout`() {
        transport.respond = { throw SocketTimeoutException("read timed out") }
        assertEquals(HermesResult.Failure(HermesError.TIMEOUT), send())
    }

    @Test
    fun `connection problems are reported as unreachable`() {
        transport.respond = { throw UnknownHostException("hermes.local") }
        assertEquals(HermesResult.Failure(HermesError.UNREACHABLE), send())
        transport.respond = { throw IOException("connection refused") }
        assertEquals(HermesResult.Failure(HermesError.UNREACHABLE), send())
    }

    @Test
    fun `401 and 403 are reported as unauthorized`() {
        transport.respond = { HttpResponse(401, """{"error":"invalid api key"}""") }
        assertEquals(HermesResult.Failure(HermesError.UNAUTHORIZED), send())
        transport.respond = { HttpResponse(403, "") }
        assertEquals(HermesResult.Failure(HermesError.UNAUTHORIZED), send())
    }

    @Test
    fun `other http errors are reported as server error`() {
        transport.respond = { HttpResponse(502, "Bad Gateway") }
        assertEquals(HermesResult.Failure(HermesError.SERVER), send())
    }

    @Test
    fun `unreadable or empty replies are invalid`() {
        transport.respond = { HttpResponse(200, "<html>kein json</html>") }
        assertEquals(HermesResult.Failure(HermesError.INVALID_RESPONSE), send())
        transport.respond = { HttpResponse(200, """{"choices":[]}""") }
        assertEquals(HermesResult.Failure(HermesError.INVALID_RESPONSE), send())
        transport.respond = { HttpResponse(200, """{"choices":[{"message":{"role":"assistant","content":null}}]}""") }
        assertEquals(HermesResult.Failure(HermesError.INVALID_RESPONSE), send())
        transport.respond = { HttpResponse(200, reply("   ")) }
        assertEquals(HermesResult.Failure(HermesError.INVALID_RESPONSE), send())
    }

    @Test
    fun `reply text is trimmed`() {
        transport.respond = { HttpResponse(200, reply("\n Sonnig, 22 Grad. \n")) }
        assertEquals(HermesResult.Success("Sonnig, 22 Grad."), send())
    }

    private companion object {
        fun reply(content: String): String = JSONObject()
            .put("choices", org.json.JSONArray().put(
                JSONObject().put("message", JSONObject().put("role", "assistant").put("content", content))
            ))
            .toString()
    }
}
