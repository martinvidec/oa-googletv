package org.openaustria.googletv.voice

/** Gesamter UI-Zustand der Spracheingabe, beobachtet von der [org.openaustria.googletv.MainActivity]. */
data class VoiceUiState(
    val overlay: VoiceOverlay = VoiceOverlay.Hidden,
    /** Zuletzt erkannter Text; leer, solange noch nichts erkannt wurde. */
    val recognizedText: String = "",
)

/** Zustand des Voice-Overlays. */
sealed interface VoiceOverlay {

    data object Hidden : VoiceOverlay

    /** Aufnahme läuft. [ready] wird `true`, sobald der Dienst Sprache annimmt. */
    data class Listening(
        val partialText: String = "",
        val soundLevel: Int = 0,
        val ready: Boolean = false,
    ) : VoiceOverlay

    /** Aufnahme beendet, das Ergebnis steht noch aus. */
    data class Processing(val partialText: String) : VoiceOverlay

    data class Error(val error: VoiceError) : VoiceOverlay

    /** Mikrofonzugriff abgelehnt; bei [permanently] hilft nur noch der Weg über die Einstellungen. */
    data class PermissionDenied(val permanently: Boolean) : VoiceOverlay
}
