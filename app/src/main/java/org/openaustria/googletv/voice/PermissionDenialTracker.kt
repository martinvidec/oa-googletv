package org.openaustria.googletv.voice

/**
 * Entscheidet, ob eine abgelehnte RECORD_AUDIO-Anfrage endgültig ist.
 *
 * `shouldShowRequestPermissionRationale` liefert nach der Anfrage `false`, wenn die Ablehnung
 * endgültig ist — ab API 30 aber auch, wenn der Dialog ohne Entscheidung geschlossen wurde
 * (Zurück-Taste). Endgültig gilt eine Ablehnung deshalb nur, wenn danach keine Begründung mehr
 * angezeigt werden soll **und** vorher schon eine Begründung fällig war oder bereits eine
 * Ablehnung ohne Begründung vorlag. Ein einzelnes „Zurück" führt so nicht zu „Einstellungen öffnen".
 */
class PermissionDenialTracker {

    /** Rationale-Wert unmittelbar vor der laufenden Anfrage. */
    var rationaleBeforeRequest: Boolean = false

    /** Die letzte Anfrage endete abgelehnt, ohne dass danach eine Begründung fällig war. */
    var deniedWithoutRationale: Boolean = false

    fun onRequest(rationale: Boolean) {
        rationaleBeforeRequest = rationale
    }

    fun onGranted() {
        deniedWithoutRationale = false
    }

    /** Liefert `true`, wenn nur noch der Weg über die Einstellungen hilft. */
    fun onDenied(rationaleAfter: Boolean): Boolean {
        if (rationaleAfter) {
            // Normale Ablehnung: Das System zeigt den Dialog beim nächsten Mal wieder.
            deniedWithoutRationale = false
            return false
        }
        val permanently = rationaleBeforeRequest || deniedWithoutRationale
        deniedWithoutRationale = true
        return permanently
    }
}
