package org.openaustria.googletv.hermes

/** Chat-Verlauf mit dem Hermes-Agent, beobachtet von der [org.openaustria.googletv.MainActivity]. */
data class ChatUiState(
    /** Chronologisch; die neueste Nachricht steht am Ende. */
    val messages: List<ChatMessage> = emptyList(),
    /** Fehler der letzten fehlgeschlagenen Anfrage; `null`, solange keine Meldung offen ist. */
    val error: HermesError? = null,
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
