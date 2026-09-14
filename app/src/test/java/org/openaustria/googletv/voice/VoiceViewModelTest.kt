package org.openaustria.googletv.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceViewModelTest {

    private class FakeRecognizer(var available: Boolean = true) : VoiceRecognizer {
        private var listenerField: VoiceRecognizer.Listener? = null
        var listener: VoiceRecognizer.Listener?
            get() = listenerField
            set(value) { listenerField = value }
        var started = 0
        var stopped = 0
        var cancelled = 0

        override fun setListener(listener: VoiceRecognizer.Listener?) {
            this.listenerField = listener
        }

        override fun isAvailable() = available
        override fun start() { started++ }
        override fun stop() { stopped++ }
        override fun cancel() { cancelled++ }
        override fun destroy() = Unit
    }

    private val recognizer = FakeRecognizer()
    private val viewModel = VoiceViewModel(recognizer)

    private val overlay get() = viewModel.uiState.value.overlay

    @Test
    fun `initial state hides overlay and has no text`() {
        assertEquals(VoiceOverlay.Hidden, overlay)
        assertEquals("", viewModel.uiState.value.recognizedText)
    }

    @Test
    fun `recognized text ends up in state and closes overlay`() {
        viewModel.startListening()
        assertEquals(1, recognizer.started)
        assertEquals(VoiceOverlay.Listening(), overlay)

        viewModel.onReadyForSpeech()
        viewModel.onPartialResult("hallo")
        assertEquals(VoiceOverlay.Listening(partialText = "hallo", ready = true), overlay)

        viewModel.onEndOfSpeech()
        assertEquals(VoiceOverlay.Processing("hallo"), overlay)

        viewModel.onResult("hallo welt")
        assertEquals(VoiceOverlay.Hidden, overlay)
        assertEquals("hallo welt", viewModel.uiState.value.recognizedText)
    }

    @Test
    fun `unavailable recognizer shows error without starting`() {
        recognizer.available = false
        viewModel.startListening()
        assertEquals(VoiceOverlay.Error(VoiceError.NOT_AVAILABLE), overlay)
        assertEquals(0, recognizer.started)
    }

    @Test
    fun `blank result is reported as no match and keeps previous text`() {
        viewModel.startListening()
        viewModel.onResult("erstes")
        viewModel.startListening()
        viewModel.onResult("  ")
        assertEquals(VoiceOverlay.Error(VoiceError.NO_MATCH), overlay)
        assertEquals("erstes", viewModel.uiState.value.recognizedText)
    }

    @Test
    fun `dismiss cancels recognition and ignores late callbacks`() {
        viewModel.startListening()
        viewModel.dismissOverlay()
        assertEquals(1, recognizer.cancelled)
        assertEquals(VoiceOverlay.Hidden, overlay)

        viewModel.onResult("zu spät")
        viewModel.onError(VoiceError.NETWORK)
        assertEquals(VoiceOverlay.Hidden, overlay)
        assertEquals("", viewModel.uiState.value.recognizedText)
    }

    @Test
    fun `dismiss outside of recognition does not cancel`() {
        viewModel.onPermissionDenied(permanently = false)
        viewModel.dismissOverlay()
        assertEquals(0, recognizer.cancelled)
        assertEquals(VoiceOverlay.Hidden, overlay)
    }

    @Test
    fun `stop listening waits for result`() {
        viewModel.startListening()
        viewModel.onPartialResult("teil")
        viewModel.stopListening()
        assertEquals(1, recognizer.stopped)
        assertEquals(VoiceOverlay.Processing("teil"), overlay)
    }

    @Test
    fun `permission denial is shown in overlay`() {
        viewModel.onPermissionDenied(permanently = true)
        assertEquals(VoiceOverlay.PermissionDenied(permanently = true), overlay)
    }

    @Test
    fun `recognizer permission error maps to permission denied`() {
        viewModel.startListening()
        viewModel.onError(VoiceError.PERMISSION)
        assertEquals(VoiceOverlay.PermissionDenied(permanently = false), overlay)
    }

    @Test
    fun `sound level is clamped to orb range`() {
        viewModel.startListening()
        viewModel.onSoundLevel(-2f)
        assertEquals(0, (overlay as VoiceOverlay.Listening).soundLevel)
        viewModel.onSoundLevel(4.5f)
        assertEquals(45, (overlay as VoiceOverlay.Listening).soundLevel)
        viewModel.onSoundLevel(50f)
        assertEquals(100, (overlay as VoiceOverlay.Listening).soundLevel)
    }

    @Test
    fun `sound level outside listening is ignored`() {
        viewModel.onSoundLevel(5f)
        assertEquals(VoiceOverlay.Hidden, overlay)
        assertFalse(recognizer.started > 0)
        assertTrue(recognizer.listener === viewModel)
    }
}
