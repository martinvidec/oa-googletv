package org.openaustria.googletv.hermes

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * [HermesClient] für die OpenAI-kompatible API des Hermes-Gateways
 * (`POST <endpoint>/v1/chat/completions`, `Authorization: Bearer <token>`). Kein eigenes Backend (D6):
 * Die App spricht direkt mit dem bestehenden Gateway.
 *
 * Die API ist zustandslos, deshalb geht bei jeder Nachricht der bisherige Verlauf mit.
 */
class HermesGatewayClient(
    private val transport: HttpTransport,
    private val isOnline: () -> Boolean = { true },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HermesClient {

    override suspend fun send(settings: HermesSettings, history: List<ChatMessage>): HermesResult {
        if (!settings.isConfigured) return HermesResult.Failure(HermesError.NOT_CONFIGURED)
        // Vor dem Request prüfen: HttpURLConnection lehnt so einen Header mit einer
        // IllegalArgumentException ab, die sonst wie ein Adressproblem aussähe.
        if (!HermesSettings.isValidToken(settings.token)) return HermesResult.Failure(HermesError.INVALID_TOKEN)
        if (!isOnline()) return HermesResult.Failure(HermesError.OFFLINE)

        return withContext(dispatcher) {
            try {
                val response = transport.post(
                    url = HermesSettings.chatCompletionsUrl(settings.endpoint),
                    headers = headers(settings),
                    body = requestBody(history),
                )
                toResult(response)
            } catch (e: SocketTimeoutException) {
                HermesResult.Failure(HermesError.TIMEOUT)
            } catch (e: IOException) {
                HermesResult.Failure(HermesError.UNREACHABLE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                // Letzter Fallback: HttpURLConnection meldet manche Verbindungsprobleme (z. B. einen
                // ungültigen Port) als IllegalArgumentException oder IllegalStateException statt als
                // IOException. Die App darf daran nicht abstürzen.
                HermesResult.Failure(HermesError.UNREACHABLE)
            }
        }
    }

    private fun headers(settings: HermesSettings): Map<String, String> =
        if (settings.token.isBlank()) emptyMap()
        else mapOf("Authorization" to "Bearer ${settings.token.trim()}")

    internal companion object {
        const val MODEL = "hermes-agent"

        fun requestBody(history: List<ChatMessage>): String {
            val messages = JSONArray()
            history.forEach { message ->
                messages.put(
                    JSONObject()
                        .put("role", if (message.role == ChatRole.USER) "user" else "assistant")
                        .put("content", message.text)
                )
            }
            return JSONObject()
                .put("model", MODEL)
                .put("stream", false)
                .put("messages", messages)
                .toString()
        }

        fun toResult(response: HttpResponse): HermesResult = when (response.code) {
            in 200..299 -> parseReply(response.body)
                ?.let { HermesResult.Success(it) }
                ?: HermesResult.Failure(HermesError.INVALID_RESPONSE)
            401, 403 -> HermesResult.Failure(HermesError.UNAUTHORIZED)
            else -> HermesResult.Failure(HermesError.SERVER)
        }

        /** Text aus `choices[0].message.content`; `null`, wenn er fehlt oder leer ist. */
        fun parseReply(body: String): String? = try {
            val message = JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
            // opt statt optString: optString liefert für JSON-null den Text "null".
            (message.opt("content") as? String)?.trim()?.ifEmpty { null }
        } catch (e: JSONException) {
            null
        }
    }
}
