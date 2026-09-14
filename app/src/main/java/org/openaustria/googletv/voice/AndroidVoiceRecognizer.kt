package org.openaustria.googletv.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** [VoiceRecognizer] über den systemweiten [SpeechRecognizer] (auf Google TV: Google-Spracherkennung). */
class AndroidVoiceRecognizer(context: Context) : VoiceRecognizer {

    private val context = context.applicationContext
    private var listener: VoiceRecognizer.Listener? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val callbacks = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listener?.onReadyForSpeech()
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            listener?.onSoundLevel(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            listener?.onEndOfSpeech()
        }

        override fun onError(error: Int) {
            listener?.onError(mapError(error))
        }

        override fun onResults(results: Bundle?) {
            listener?.onResult(firstMatch(results))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = firstMatch(partialResults)
            if (text.isNotEmpty()) listener?.onPartialResult(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    override fun setListener(listener: VoiceRecognizer.Listener?) {
        this.listener = listener
    }

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun start() {
        val recognizer = speechRecognizer
            ?: SpeechRecognizer.createSpeechRecognizer(context).also {
                it.setRecognitionListener(callbacks)
                speechRecognizer = it
            }
        // Eine eventuell noch laufende Sitzung beenden, sonst meldet der Dienst ERROR_RECOGNIZER_BUSY.
        recognizer.cancel()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        recognizer.startListening(intent)
    }

    override fun stop() {
        speechRecognizer?.stopListening()
    }

    override fun cancel() {
        speechRecognizer?.cancel()
    }

    override fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        listener = null
    }

    private fun firstMatch(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

    private fun mapError(error: Int): VoiceError = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.NO_MATCH
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        ERROR_SERVER_DISCONNECTED -> VoiceError.NETWORK
        SpeechRecognizer.ERROR_AUDIO -> VoiceError.AUDIO
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceError.BUSY
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.PERMISSION
        else -> VoiceError.OTHER
    }

    private companion object {
        /** `SpeechRecognizer.ERROR_SERVER_DISCONNECTED`, erst ab API 31 als Konstante vorhanden. */
        const val ERROR_SERVER_DISCONNECTED = 11
    }
}
