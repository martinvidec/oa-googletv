package org.openaustria.googletv.hermes

import android.content.Context

/** Persistenz der [HermesSettings]; als Schnittstelle, damit das [ChatViewModel] ohne Gerät testbar ist. */
interface HermesSettingsStore {
    fun load(): HermesSettings
    fun save(settings: HermesSettings)
}

/**
 * Speichert Endpoint und Token in den SharedPreferences der App. Die Datei ist vom Backup
 * ausgenommen (`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`), damit das Token
 * nicht in der Cloud-Sicherung landet.
 */
class SharedPreferencesSettingsStore(context: Context) : HermesSettingsStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): HermesSettings = HermesSettings(
        endpoint = prefs.getString(KEY_ENDPOINT, null).orEmpty(),
        token = prefs.getString(KEY_TOKEN, null).orEmpty(),
    )

    override fun save(settings: HermesSettings) {
        prefs.edit()
            .putString(KEY_ENDPOINT, settings.endpoint.trim())
            .putString(KEY_TOKEN, settings.token.trim())
            .apply()
    }

    private companion object {
        /** Muss zu den Backup-Ausschlüssen in `res/xml/` passen. */
        const val PREFS_NAME = "hermes_settings"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_TOKEN = "token"
    }
}
