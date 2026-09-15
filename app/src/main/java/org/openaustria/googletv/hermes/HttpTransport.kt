package org.openaustria.googletv.hermes

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(val code: Int, val body: String)

/** Blockierender HTTP-POST; austauschbar, damit [HermesGatewayClient] ohne Netzwerk testbar ist. */
fun interface HttpTransport {
    @Throws(IOException::class)
    fun post(url: String, headers: Map<String, String>, body: String): HttpResponse
}

/** [HttpTransport] über [HttpURLConnection] — keine zusätzliche Bibliothek nötig. */
class UrlConnectionTransport(
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS,
) : HttpTransport {

    override fun post(url: String, headers: Map<String, String>, body: String): HttpResponse {
        val connection = URL(url).openConnection() as? HttpURLConnection
            ?: throw IOException("Keine HTTP-URL: $url")
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResponse(code, text)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000

        /** Der Agent führt ggf. Tools aus, bevor er antwortet — daher großzügig. */
        const val READ_TIMEOUT_MS = 90_000
    }
}
