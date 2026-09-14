package org.openaustria.googletv.voice

/**
 * Abstraktion der Spracherkennung. Hält das [VoiceViewModel] frei von Android-Framework-Klassen,
 * damit es ohne Gerät testbar ist. Implementierung: [AndroidVoiceRecognizer].
 *
 * Alle Aufrufe und Callbacks laufen auf dem Main-Thread.
 */
interface VoiceRecognizer {

    /** Callbacks einer laufenden Erkennung. */
    interface Listener {
        fun onReadyForSpeech()
        fun onSoundLevel(rmsDb: Float)
        fun onPartialResult(text: String)
        fun onEndOfSpeech()
        fun onResult(text: String)
        fun onError(error: VoiceError)
    }

    fun setListener(listener: Listener?)

    /** `false`, wenn auf dem Gerät kein Spracherkennungsdienst installiert ist. */
    fun isAvailable(): Boolean

    fun start()

    /** Beendet die Aufnahme; das bisher Gesprochene wird noch erkannt. */
    fun stop()

    /** Bricht ab; es folgen keine Ergebnisse mehr. */
    fun cancel()

    fun destroy()
}

/** Fehlerklassen, auf die die UI unterschiedlich reagiert. */
enum class VoiceError {
    NO_MATCH,
    NETWORK,
    AUDIO,
    BUSY,
    PERMISSION,
    NOT_AVAILABLE,
    OTHER,
}
