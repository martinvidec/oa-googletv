package org.openaustria.googletv.hermes

/**
 * Kanal zum Hermes-Agent. Hält das [ChatViewModel] frei von Netzwerk-Code, damit es ohne Gerät
 * testbar ist. Implementierung: [HermesGatewayClient].
 */
interface HermesClient {

    /**
     * Schickt den Verlauf an den Agent; die letzte Nachricht in [history] ist die neue
     * Nutzer-Nachricht. Wirft keine Netzwerk-Exceptions, sondern liefert [HermesResult.Failure].
     */
    suspend fun send(settings: HermesSettings, history: List<ChatMessage>): HermesResult
}

sealed interface HermesResult {
    data class Success(val reply: String) : HermesResult
    data class Failure(val error: HermesError) : HermesResult
}

/** Fehlerklassen, auf die die UI mit unterschiedlichen Meldungen und Aktionen reagiert. */
enum class HermesError {
    /** Kein gültiger Endpoint eingetragen. */
    NOT_CONFIGURED,

    /** Das Gerät hat keine Netzwerkverbindung. */
    OFFLINE,

    /** Netzwerk da, aber das Gateway ist nicht erreichbar (falsche Adresse, Gateway aus). */
    UNREACHABLE,

    /** Verbindungsaufbau oder Antwort hat zu lange gedauert. */
    TIMEOUT,

    /** HTTP 401/403: Token fehlt oder ist ungültig. */
    UNAUTHORIZED,

    /** Das Token enthält Zeichen, die in einem HTTP-Header nicht erlaubt sind (Zeilenumbruch, Umlaute …). */
    INVALID_TOKEN,

    /** Sonstiger HTTP-Fehlerstatus des Gateways. */
    SERVER,

    /** Antwort ohne lesbaren Agent-Text. */
    INVALID_RESPONSE,
}
