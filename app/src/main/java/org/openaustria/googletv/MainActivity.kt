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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.openaustria.googletv.hermes.ChatAdapter
import org.openaustria.googletv.hermes.ChatUiState
import org.openaustria.googletv.hermes.ChatViewModel
import org.openaustria.googletv.hermes.HermesError
import org.openaustria.googletv.voice.PermissionDenialTracker
import org.openaustria.googletv.voice.VoiceError
import org.openaustria.googletv.voice.VoiceOverlay
import org.openaustria.googletv.voice.VoiceUiState
import org.openaustria.googletv.voice.VoiceViewModel

/**
 * Einstiegspunkt der TV-App, gestartet über den LEANBACK_LAUNCHER.
 *
 * Reine UI-Schicht: rendert [VoiceUiState] und [ChatUiState], leitet Fernbedienungs-Eingaben an die
 * ViewModels weiter und fragt die Mikrofon-Berechtigung an. Jedes erkannte Sprach-Ergebnis geht als
 * Nachricht an Hermes. Bedienung ausschließlich per D-Pad: Jeder Overlay-Zustand und jede
 * Fehlermeldung setzt den Fokus auf ihr primäres Element.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: VoiceViewModel by viewModels { VoiceViewModel.Factory }
    private val chatViewModel: ChatViewModel by viewModels { ChatViewModel.Factory }

    private lateinit var mainContent: ViewGroup
    private lateinit var voiceButton: Button
    private lateinit var voiceOverlay: View
    private lateinit var voiceOrb: SpeechOrbView
    private lateinit var voiceStatus: TextView
    private lateinit var voiceTranscript: TextView
    private lateinit var voiceAction: Button
    private lateinit var voiceClose: Button

    private lateinit var chatList: RecyclerView
    private lateinit var chatEmpty: TextView
    private lateinit var chatStatus: TextView
    private lateinit var chatErrorBar: View
    private lateinit var chatErrorText: TextView
    private lateinit var chatErrorRetry: Button
    private lateinit var chatErrorSettings: Button

    private val chatAdapter = ChatAdapter()

    /** Zuletzt gerenderter Overlay-Zustand; Fokus wird nur bei einem Zustandswechsel neu gesetzt. */
    private var renderedOverlay: VoiceOverlay? = null

    /** Anzahl gerenderter Nachrichten; Autoscroll nur, wenn eine neue hinzukommt. */
    private var renderedMessageCount = 0

    /** Zuletzt gerenderter Chat-Fehler; Fokus wird nur bei einer neuen Meldung gesetzt. */
    private var renderedChatError: HermesError? = null

    private val permissionTracker = PermissionDenialTracker()

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                permissionTracker.onGranted()
                viewModel.startListening()
            } else {
                viewModel.onPermissionDenied(permissionTracker.onDenied(shouldShowAudioRationale()))
            }
        }

    private val closeOverlayOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.dismissOverlay()
        }
    }

    private val dismissChatErrorOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            chatViewModel.dismissError()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        savedInstanceState?.let {
            permissionTracker.rationaleBeforeRequest = it.getBoolean(KEY_RATIONALE_BEFORE_REQUEST)
            permissionTracker.deniedWithoutRationale = it.getBoolean(KEY_DENIED_WITHOUT_RATIONALE)
        }

        mainContent = findViewById(R.id.main_content)
        voiceButton = findViewById(R.id.voice_button)
        voiceOverlay = findViewById(R.id.voice_overlay)
        voiceOrb = findViewById(R.id.voice_orb)
        voiceStatus = findViewById(R.id.voice_status)
        voiceTranscript = findViewById(R.id.voice_transcript)
        voiceAction = findViewById(R.id.voice_action)
        voiceClose = findViewById(R.id.voice_close)
        chatList = findViewById(R.id.chat_list)
        chatEmpty = findViewById(R.id.chat_empty)
        chatStatus = findViewById(R.id.chat_status)
        chatErrorBar = findViewById(R.id.chat_error_bar)
        chatErrorText = findViewById(R.id.chat_error_text)
        chatErrorRetry = findViewById(R.id.chat_error_retry)
        chatErrorSettings = findViewById(R.id.chat_error_settings)

        chatList.layoutManager = LinearLayoutManager(this)
        chatList.adapter = chatAdapter

        voiceButton.setOnClickListener { onVoiceRequested() }
        findViewById<Button>(R.id.settings_button).setOnClickListener { openHermesSettings() }
        voiceOrb.setOnOrbClickedListener { onOverlayAction() }
        voiceAction.setOnClickListener { onOverlayAction() }
        voiceClose.setOnClickListener { viewModel.dismissOverlay() }
        chatErrorRetry.setOnClickListener { chatViewModel.retry() }
        chatErrorSettings.setOnClickListener { openHermesSettings() }
        findViewById<Button>(R.id.chat_error_dismiss).setOnClickListener { chatViewModel.dismissError() }
        // Zuletzt registrierte Callbacks gewinnen: Ein offenes Overlay schließt vor der Fehlermeldung.
        onBackPressedDispatcher.addCallback(this, dismissChatErrorOnBack)
        onBackPressedDispatcher.addCallback(this, closeOverlayOnBack)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it) } }
                launch { viewModel.results.collect { chatViewModel.send(it) } }
                launch { chatViewModel.uiState.collect { renderChat(it) } }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Der Permission-Dialog überlebt eine Neuerstellung der Activity, das Ergebnis kommt dann hier an.
        outState.putBoolean(KEY_RATIONALE_BEFORE_REQUEST, permissionTracker.rationaleBeforeRequest)
        outState.putBoolean(KEY_DENIED_WITHOUT_RATIONALE, permissionTracker.deniedWithoutRationale)
    }

    override fun onStop() {
        super.onStop()
        // Im Hintergrund nicht weiter aufnehmen; beim Zurückkehren startet der Nutzer neu.
        viewModel.dismissOverlay()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Such-Taste vor Views und System abfangen: Die Default-Behandlung startet sonst beim Loslassen
        // die Systemsuche, die Activity geht in onStop und das offene Overlay schließt sich.
        if (event.keyCode == KeyEvent.KEYCODE_SEARCH) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
                viewModel.uiState.value.overlay is VoiceOverlay.Hidden
            ) {
                onVoiceRequested()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun onVoiceRequested() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startListening()
        } else {
            permissionTracker.onRequest(shouldShowAudioRationale())
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun shouldShowAudioRationale(): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)

    /** Primäraktion des Overlays: Orb und Aktions-Button verhalten sich gleich. */
    private fun onOverlayAction() {
        when (val overlay = viewModel.uiState.value.overlay) {
            is VoiceOverlay.Listening -> viewModel.stopListening()
            is VoiceOverlay.Error -> when (overlay.error) {
                VoiceError.NOT_AVAILABLE -> Unit
                // Die App hat die Berechtigung schon; erneut anfragen oder starten liefe in denselben Fehler.
                VoiceError.PERMISSION -> openSystemSettings()
                else -> onVoiceRequested()
            }
            is VoiceOverlay.PermissionDenied ->
                if (overlay.permanently) openAppSettings() else onVoiceRequested()
            VoiceOverlay.Hidden, is VoiceOverlay.Processing -> Unit
        }
    }

    private fun openHermesSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Manche TV-Oberflächen haben keine App-Detailseite; dann die allgemeinen Einstellungen.
            openSystemSettings()
        }
    }

    private fun openSystemSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            // Ohne Einstellungs-App bleibt nur „Schließen" als Ausweg.
        }
    }

    private fun render(state: VoiceUiState) {
        val overlay = state.overlay
        val visible = overlay !is VoiceOverlay.Hidden
        voiceOverlay.isVisible = visible
        // Solange das Overlay offen ist, darf der D-Pad-Fokus nicht in den Hintergrund wandern.
        mainContent.descendantFocusability =
            if (visible) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_AFTER_DESCENDANTS
        closeOverlayOnBack.isEnabled = visible

        val previous = renderedOverlay
        renderedOverlay = overlay
        val overlayChanged = previous == null || previous::class != overlay::class
        if (overlayChanged) {
            // showListening() setzt Pegel und Skalierung des Orbs zurück. Pro Pegel-Tick aufgerufen,
            // würde es Glättung und Abklingen von setSoundLevel aushebeln — daher nur beim Wechsel.
            if (overlay is VoiceOverlay.Listening) voiceOrb.showListening() else voiceOrb.showNotListening()
        }

        when (overlay) {
            VoiceOverlay.Hidden -> Unit
            is VoiceOverlay.Listening -> {
                voiceOrb.setSoundLevel(overlay.soundLevel)
                voiceStatus.setText(if (overlay.ready) R.string.voice_listening else R.string.voice_preparing)
                showTranscript(overlay.partialText)
                showAction(R.string.voice_action_done)
            }
            is VoiceOverlay.Processing -> {
                voiceStatus.setText(R.string.voice_processing)
                showTranscript(overlay.partialText)
                showAction(null)
            }
            is VoiceOverlay.Error -> {
                voiceStatus.setText(errorMessage(overlay.error))
                showTranscript("")
                showAction(
                    when (overlay.error) {
                        VoiceError.NOT_AVAILABLE -> null
                        VoiceError.PERMISSION -> R.string.voice_action_settings
                        else -> R.string.voice_action_retry
                    }
                )
            }
            is VoiceOverlay.PermissionDenied -> {
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

        if (overlayChanged) moveFocus(overlay)
    }

    private fun renderChat(state: ChatUiState) {
        val messages = state.messages
        val grew = messages.size > renderedMessageCount
        renderedMessageCount = messages.size
        chatAdapter.submitList(messages) {
            if (grew) scrollToNewest()
        }
        chatEmpty.isVisible = messages.isEmpty()
        chatStatus.isVisible = state.isSending

        val error = state.error
        val errorChanged = error != renderedChatError
        renderedChatError = error
        // Vor dem Ausblenden abfragen: Danach hat die Leiste den Fokus schon verloren.
        val errorBarHadFocus = chatErrorBar.hasFocus()
        chatErrorBar.isVisible = error != null
        dismissChatErrorOnBack.isEnabled = error != null

        if (error != null) {
            chatErrorText.setText(chatErrorMessage(error))
            val settingsHelp = error in SETTINGS_ERRORS
            chatErrorSettings.isVisible = settingsHelp
            if (errorChanged) (if (settingsHelp) chatErrorSettings else chatErrorRetry).requestFocus()
        } else if (errorBarHadFocus) {
            voiceButton.requestFocus()
        }
    }

    /** Autoscroll zur neuesten Nachricht; stand der Fokus im Verlauf, wandert er mit. */
    private fun scrollToNewest() {
        val last = chatAdapter.itemCount - 1
        if (last < 0) return
        val focusInList = chatList.hasFocus()
        chatList.scrollToPosition(last)
        if (focusInList) {
            chatList.post { chatList.findViewHolderForAdapterPosition(last)?.itemView?.requestFocus() }
        }
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
        VoiceError.PERMISSION -> R.string.voice_error_permission
        VoiceError.NOT_AVAILABLE -> R.string.voice_error_not_available
        VoiceError.OTHER -> R.string.voice_error_other
    }

    private fun chatErrorMessage(error: HermesError): Int = when (error) {
        HermesError.NOT_CONFIGURED -> R.string.chat_error_not_configured
        HermesError.OFFLINE -> R.string.chat_error_offline
        HermesError.UNREACHABLE -> R.string.chat_error_unreachable
        HermesError.TIMEOUT -> R.string.chat_error_timeout
        HermesError.UNAUTHORIZED -> R.string.chat_error_unauthorized
        HermesError.SERVER -> R.string.chat_error_server
        HermesError.INVALID_RESPONSE -> R.string.chat_error_invalid_response
    }

    private companion object {
        const val KEY_RATIONALE_BEFORE_REQUEST = "rationale_before_request"
        const val KEY_DENIED_WITHOUT_RATIONALE = "denied_without_rationale"

        /** Fehler, bei denen vermutlich Adresse oder Token falsch sind: Einstellungen anbieten und fokussieren. */
        val SETTINGS_ERRORS = setOf(HermesError.NOT_CONFIGURED, HermesError.UNREACHABLE, HermesError.UNAUTHORIZED)
    }
}
