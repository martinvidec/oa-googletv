package org.openaustria.googletv.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Zustandsmaschine der Spracheingabe. Die Laufzeit-Berechtigung prüft die UI, bevor sie
 * [startListening] aufruft — das Anfragen braucht eine Activity.
 */
class VoiceViewModel(
    private val recognizer: VoiceRecognizer,
) : ViewModel(), VoiceRecognizer.Listener {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    init {
        recognizer.setListener(this)
    }

    /** Startet die Erkennung; Voraussetzung ist eine erteilte RECORD_AUDIO-Berechtigung. */
    fun startListening() {
        if (!recognizer.isAvailable()) {
            setOverlay(VoiceOverlay.Error(VoiceError.NOT_AVAILABLE))
            return
        }
        setOverlay(VoiceOverlay.Listening())
        recognizer.start()
    }

    /** Nutzer beendet die Aufnahme selbst; das Gesprochene wird noch ausgewertet. */
    fun stopListening() {
        val overlay = _uiState.value.overlay as? VoiceOverlay.Listening ?: return
        setOverlay(VoiceOverlay.Processing(overlay.partialText))
        recognizer.stop()
    }

    fun onPermissionDenied(permanently: Boolean) {
        setOverlay(VoiceOverlay.PermissionDenied(permanently))
    }

    /** Schließt das Overlay (Zurück-Taste, „Schließen", App verlässt den Vordergrund). */
    fun dismissOverlay() {
        if (isRecognizing()) recognizer.cancel()
        setOverlay(VoiceOverlay.Hidden)
    }

    override fun onReadyForSpeech() {
        updateListening { it.copy(ready = true) }
    }

    override fun onSoundLevel(rmsDb: Float) {
        // rmsdB liegt grob zwischen -2 und 10; SpeechOrbView erwartet 0..100.
        val level = (rmsDb * 10).toInt().coerceIn(0, 100)
        updateListening { it.copy(soundLevel = level) }
    }

    override fun onPartialResult(text: String) {
        when (val overlay = _uiState.value.overlay) {
            is VoiceOverlay.Listening -> setOverlay(overlay.copy(partialText = text))
            is VoiceOverlay.Processing -> setOverlay(overlay.copy(partialText = text))
            else -> Unit
        }
    }

    override fun onEndOfSpeech() {
        val overlay = _uiState.value.overlay as? VoiceOverlay.Listening ?: return
        setOverlay(VoiceOverlay.Processing(overlay.partialText))
    }

    override fun onResult(text: String) {
        if (!isRecognizing()) return
        if (text.isBlank()) {
            setOverlay(VoiceOverlay.Error(VoiceError.NO_MATCH))
        } else {
            _uiState.value = VoiceUiState(overlay = VoiceOverlay.Hidden, recognizedText = text)
        }
    }

    override fun onError(error: VoiceError) {
        if (!isRecognizing()) return
        // PERMISSION kommt hier vom Erkennungsdienst: Die App selbst hat RECORD_AUDIO bereits
        // (sonst wäre start() nie aufgerufen worden). Erneut anfragen würde nur denselben Fehler
        // wiederholen, deshalb als eigener Fehler statt als PermissionDenied.
        setOverlay(VoiceOverlay.Error(error))
    }

    override fun onCleared() {
        recognizer.destroy()
    }

    /** Callbacks nach einem Abbruch (Overlay schon zu) dürfen den Zustand nicht mehr ändern. */
    private fun isRecognizing(): Boolean = when (_uiState.value.overlay) {
        is VoiceOverlay.Listening, is VoiceOverlay.Processing -> true
        else -> false
    }

    private fun updateListening(transform: (VoiceOverlay.Listening) -> VoiceOverlay.Listening) {
        val overlay = _uiState.value.overlay as? VoiceOverlay.Listening ?: return
        setOverlay(transform(overlay))
    }

    private fun setOverlay(overlay: VoiceOverlay) {
        _uiState.update { it.copy(overlay = overlay) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                VoiceViewModel(AndroidVoiceRecognizer(application))
            }
        }
    }
}
