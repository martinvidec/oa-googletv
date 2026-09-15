package org.openaustria.googletv

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import org.openaustria.googletv.hermes.EndpointValidation
import org.openaustria.googletv.hermes.HermesSettings
import org.openaustria.googletv.hermes.HermesSettingsStore
import org.openaustria.googletv.hermes.SharedPreferencesSettingsStore

/**
 * Verbindungsdaten zum Hermes-Gateway: Endpoint-URL und Token. Bedienung per D-Pad; die Felder
 * öffnen beim Auswählen die Bildschirmtastatur. Gespeichert wird nur eine gültige Adresse.
 */
class SettingsActivity : FragmentActivity() {

    private lateinit var store: HermesSettingsStore
    private lateinit var endpointInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = SharedPreferencesSettingsStore(this)

        endpointInput = findViewById(R.id.settings_endpoint)
        tokenInput = findViewById(R.id.settings_token)
        errorText = findViewById(R.id.settings_error)

        // Nach einer Neuerstellung stellen die EditTexts ihre Eingaben selbst wieder her.
        if (savedInstanceState == null) {
            val settings = store.load()
            endpointInput.setText(settings.endpoint)
            tokenInput.setText(settings.token)
        }

        findViewById<Button>(R.id.settings_save).setOnClickListener { save() }
        findViewById<Button>(R.id.settings_cancel).setOnClickListener { finish() }
    }

    private fun save() {
        val endpoint = endpointInput.text.toString().trim()
        when (HermesSettings.validateEndpoint(endpoint)) {
            EndpointValidation.VALID -> {
                store.save(HermesSettings(endpoint = endpoint, token = tokenInput.text.toString().trim()))
                Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
            EndpointValidation.EMPTY -> showError(R.string.settings_error_endpoint_empty)
            EndpointValidation.INVALID -> showError(R.string.settings_error_endpoint_invalid)
        }
    }

    private fun showError(message: Int) {
        errorText.setText(message)
        errorText.isVisible = true
        endpointInput.requestFocus()
    }
}
