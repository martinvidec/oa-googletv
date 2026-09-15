package org.openaustria.googletv.hermes

import java.net.URI
import java.net.URISyntaxException

/** Verbindungsdaten zum Hermes-Gateway, eingetragen im Settings-Screen. */
data class HermesSettings(
    /** Basis-URL des Gateways, z. B. `http://192.168.1.10:8642`. */
    val endpoint: String = "",
    /** Bearer-Token des Gateways; leer, wenn das Gateway keinen Schlüssel verlangt. */
    val token: String = "",
) {

    val isConfigured: Boolean
        get() = validateEndpoint(endpoint) == EndpointValidation.VALID

    companion object {

        fun validateEndpoint(raw: String): EndpointValidation {
            val value = raw.trim()
            if (value.isEmpty()) return EndpointValidation.EMPTY
            val uri = try {
                URI(value)
            } catch (e: URISyntaxException) {
                return EndpointValidation.INVALID
            }
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return EndpointValidation.INVALID
            if (uri.host.isNullOrEmpty()) return EndpointValidation.INVALID
            return EndpointValidation.VALID
        }

        /**
         * Das Token geht als HTTP-Header raus und darf deshalb nur druckbares ASCII enthalten; leer ist
         * erlaubt. Umlaute oder ein mitkopierter Zeilenumbruch würden den Request sonst scheitern lassen.
         */
        fun isValidToken(raw: String): Boolean = raw.trim().all { it in ' '..'~' }

        /**
         * Chat-Completions-URL des Gateways. Akzeptiert die Basis-URL, eine URL mit `/v1` oder die
         * vollständige URL — so funktioniert auch eine aus der Gateway-Doku kopierte Adresse.
         */
        fun chatCompletionsUrl(endpoint: String): String {
            val base = endpoint.trim().trimEnd('/')
            return when {
                base.endsWith("/chat/completions") -> base
                base.endsWith("/v1") -> "$base/chat/completions"
                else -> "$base/v1/chat/completions"
            }
        }
    }
}

enum class EndpointValidation {
    VALID,
    EMPTY,
    INVALID,
}
