package org.openaustria.googletv

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.leanback.widget.SpeechOrbView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.openaustria.googletv.voice.VoiceError
import org.openaustria.googletv.voice.VoiceOverlay
import org.openaustria.googletv.voice.VoiceUiState
import org.openaustria.googletv.voice.VoiceViewModel

/**
 * Einstiegspunkt der TV-App, gestartet über den LEANBACK_LAUNCHER.
 *
 * Reine UI-Schicht: rendert [VoiceUiState], leitet Fernbedienungs-Eingaben an das
 * [VoiceViewModel] weiter und fragt die Mikrofon-Berechtigung an. Bedienung ausschließlich
 * per D-Pad: Jeder Overlay-Zustand setzt den Fokus auf sein primäres Element.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: VoiceViewModel by viewModels { VoiceViewModel.Factory }

    private lateinit var mainContent: ViewGroup
    private lateinit var voiceButton: Button
    private lateinit var recognizedText: TextView
    private lateinit var voiceOverlay: View
    private lateinit var voiceOrb: SpeechOrbView
    private lateinit var voiceStatus: TextView
    private lateinit var voiceTranscript: TextView
    private lateinit var voiceAction: Button
    private lateinit var voiceClose: Button

    /** Zuletzt gerenderter Overlay-Zustand; Fokus wird nur bei einem Zustandswechsel neu gesetzt. */
    private var renderedOverlay: VoiceOverlay? = null

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.startListening()
            } else {
                // Nach endgültiger Ablehnung zeigt das System keinen Dialog mehr und keine Begründung.
                val permanently = !ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.RECORD_AUDIO
                )
                viewModel.onPermissionDenied(permanently)
            }
        }

    private val closeOverlayOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.dismissOverlay()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainContent = findViewById(R.id.main_content)
        voiceButton = findViewById(R.id.voice_button)
        recognizedText = findViewById(R.id.recognized_text)
        voiceOverlay = findViewById(R.id.voice_overlay)
        voiceOrb = findViewById(R.id.voice_orb)
        voiceStatus = findViewById(R.id.voice_status)
        voiceTranscript = findViewById(R.id.voice_transcript)
        voiceAction = findViewById(R.id.voice_action)
        voiceClose = findViewById(R.id.voice_close)

        voiceButton.setOnClickListener { onVoiceRequested() }
        voiceOrb.setOnOrbClickedListener { onOverlayAction() }
        voiceAction.setOnClickListener { onOverlayAction() }
        voiceClose.setOnClickListener { viewModel.dismissOverlay() }
        onBackPressedDispatcher.addCallback(this, closeOverlayOnBack)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Im Hintergrund nicht weiter aufnehmen; beim Zurückkehren startet der Nutzer neu.
        viewModel.dismissOverlay()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Such-Taste der Fernbedienung öffnet die Spracheingabe direkt.
        if (keyCode == KeyEvent.KEYCODE_SEARCH && viewModel.uiState.value.overlay is VoiceOverlay.Hidden) {
            onVoiceRequested()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun onVoiceRequested() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startListening()
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** Primäraktion des Overlays: Orb und Aktions-Button verhalten sich gleich. */
    private fun onOverlayAction() {
        when (val overlay = viewModel.uiState.value.overlay) {
            is VoiceOverlay.Listening -> viewModel.stopListening()
            is VoiceOverlay.Error ->
                if (overlay.error != VoiceError.NOT_AVAILABLE) onVoiceRequested()
            is VoiceOverlay.PermissionDenied ->
                if (overlay.permanently) openAppSettings() else onVoiceRequested()
            VoiceOverlay.Hidden, is VoiceOverlay.Processing -> Unit
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Manche TV-Oberflächen haben keine App-Detailseite; dann die allgemeinen Einstellungen.
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun render(state: VoiceUiState) {
        recognizedText.text = state.recognizedText.ifEmpty { getString(R.string.voice_result_empty) }

        val overlay = state.overlay
        val visible = overlay !is VoiceOverlay.Hidden
        voiceOverlay.isVisible = visible
        // Solange das Overlay offen ist, darf der D-Pad-Fokus nicht in den Hintergrund wandern.
        mainContent.descendantFocusability =
            if (visible) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_AFTER_DESCENDANTS
        closeOverlayOnBack.isEnabled = visible

        when (overlay) {
            VoiceOverlay.Hidden -> voiceOrb.showNotListening()
            is VoiceOverlay.Listening -> {
                voiceOrb.showListening()
                voiceOrb.setSoundLevel(overlay.soundLevel)
                voiceStatus.setText(if (overlay.ready) R.string.voice_listening else R.string.voice_preparing)
                showTranscript(overlay.partialText)
                showAction(R.string.voice_action_done)
            }
            is VoiceOverlay.Processing -> {
                voiceOrb.showNotListening()
                voiceStatus.setText(R.string.voice_processing)
                showTranscript(overlay.partialText)
                showAction(null)
            }
            is VoiceOverlay.Error -> {
                voiceOrb.showNotListening()
                voiceStatus.setText(errorMessage(overlay.error))
                showTranscript("")
                showAction(if (overlay.error == VoiceError.NOT_AVAILABLE) null else R.string.voice_action_retry)
            }
            is VoiceOverlay.PermissionDenied -> {
                voiceOrb.showNotListening()
                voiceStatus.setText(
                    if (overlay.permanently) R.string.voice_permission_denied_permanently
                    else R.string.voice_permission_denied
                )
                showTranscript("")
                showAction(
                    if (overlay.permanently) R.string.voice_action_settings
                    else R.string.voice_action_allow
                )
            }
        }

        val previous = renderedOverlay
        renderedOverlay = overlay
        if (previous == null || previous::class != overlay::class) moveFocus(overlay)
    }

    private fun moveFocus(overlay: VoiceOverlay) {
        val target = when (overlay) {
            VoiceOverlay.Hidden -> voiceButton
            is VoiceOverlay.Listening -> voiceOrb
            else -> if (voiceAction.isVisible) voiceAction else voiceClose
        }
        target.requestFocus()
    }

    private fun showTranscript(text: String) {
        voiceTranscript.text = text
        voiceTranscript.isVisible = text.isNotEmpty()
    }

    private fun showAction(label: Int?) {
        voiceAction.isVisible = label != null
        if (label != null) voiceAction.setText(label)
    }

    private fun errorMessage(error: VoiceError): Int = when (error) {
        VoiceError.NO_MATCH -> R.string.voice_error_no_match
        VoiceError.NETWORK -> R.string.voice_error_network
        VoiceError.AUDIO -> R.string.voice_error_audio
        VoiceError.BUSY -> R.string.voice_error_busy
        VoiceError.PERMISSION -> R.string.voice_permission_denied
        VoiceError.NOT_AVAILABLE -> R.string.voice_error_not_available
        VoiceError.OTHER -> R.string.voice_error_other
    }
}
