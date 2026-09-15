package org.openaustria.googletv.hermes

/** Chat-Verlauf mit dem Hermes-Agent, beobachtet von der [org.openaustria.googletv.MainActivity]. */
data class ChatUiState(
    /** Chronologisch; die neueste Nachricht steht am Ende. */
    val messages: List<ChatMessage> = emptyList(),
    /** Fehler der letzten fehlgeschlagenen Anfrage; `null`, solange keine Meldung offen ist. */
    val error: HermesError? = null,
    /**
     * Zählt jede neue Fehlermeldung hoch. Die UI setzt den Fokus bei jeder neuen Meldung auf deren
     * Aktion — auch wenn derselbe Fehler direkt noch einmal auftritt und [error] gleich bleibt.
     */
    val errorId: Long = 0,
    /**
     * Zuletzt hinzugekommene Nachricht: neue oder erneut gesendete Nachricht, sonst die letzte Antwort.
     * Ziel des Autoscrolls; `null`, solange der Verlauf leer ist.
     */
    val newestMessageId: Long? = null,
) {
    /** Mindestens eine Nachricht wartet noch auf die Antwort des Agents. */
    val isSending: Boolean
        get() = messages.any { it.status == MessageStatus.PENDING }
}

data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val text: String,
    val status: MessageStatus = MessageStatus.SENT,
)

enum class ChatRole {
    USER,
    AGENT,
}

enum class MessageStatus {
    /** Nutzer-Nachricht, deren Antwort noch aussteht. */
    PENDING,
    SENT,

    /** Nutzer-Nachricht, deren Anfrage fehlgeschlagen ist; kann erneut gesendet werden. */
    FAILED,
}
