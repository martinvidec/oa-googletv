# oa-googletv
Google TV-App für Open-Austria Agents

Android-TV-App (Leanback) in Kotlin. Eine Activity, die über den `LEANBACK_LAUNCHER` im
TV-Launcher erscheint (Banner und App-Icon sind Platzhalter-Vektorgrafiken), mit
Spracheingabe.

## Spracheingabe

Code unter `app/src/main/java/org/openaustria/googletv/voice/`:

| Datei | Aufgabe |
|---|---|
| `VoiceRecognizer.kt` | Schnittstelle zur Spracherkennung, Fehlerklassen (`VoiceError`) |
| `AndroidVoiceRecognizer.kt` | Implementierung über den systemweiten `SpeechRecognizer` |
| `VoiceUiState.kt` | UI-Zustand: Overlay-Zustand und zuletzt erkannter Text |
| `VoiceViewModel.kt` | Zustandsmaschine, stellt den Zustand als `StateFlow` bereit |

`MainActivity` ist reine UI: Sie rendert den Zustand, fragt `RECORD_AUDIO` zur Laufzeit an
und leitet Fernbedienungs-Eingaben weiter.

- **Bedienung nur per D-Pad:** „Spracheingabe" auswählen oder die Such-Taste der Fernbedienung
  drücken. Im Overlay liegt der Fokus je nach Zustand auf dem Mikrofon-Orb bzw. der
  Primäraktion; der Hintergrund ist für den Fokus gesperrt. Zurück schließt das Overlay. Die
  Such-Taste wird bei offenem Overlay verworfen, damit sie nicht die Systemsuche startet.
- **Berechtigung:** Wird der Mikrofonzugriff abgelehnt, bietet das Overlay „Mikrofon erlauben"
  an; nach endgültiger Ablehnung „Einstellungen öffnen". Ein per Zurück geschlossener
  Berechtigungsdialog gilt noch nicht als endgültig (`PermissionDenialTracker`). Meldet der
  Erkennungsdienst trotz erteilter Berechtigung fehlenden Mikrofonzugriff, zeigt das Overlay
  einen eigenen Fehler mit „Einstellungen öffnen" statt erneut anzufragen.
- **Manifest:** `android.hardware.microphone` ist optional, damit TVs ohne eingebautes Mikrofon
  nicht ausgefiltert werden. Der `<queries>`-Eintrag für `android.speech.RecognitionService`
  ist ab API 30 nötig, sonst meldet `SpeechRecognizer.isRecognitionAvailable()` immer `false`.
- **Tests:** `./gradlew testDebugUnitTest` (ViewModel mit Fake-Recognizer, läuft auch in der CI).

## Hermes-Chat

Jedes erkannte Sprach-Ergebnis (`VoiceViewModel.results`) geht als Nachricht an den Hermes-Agent;
Frage und Antwort erscheinen im Chat-Verlauf. Kein eigenes Backend (D6): Die App spricht direkt mit
dem Hermes-Gateway. Code unter `app/src/main/java/org/openaustria/googletv/hermes/`:

| Datei | Aufgabe |
|---|---|
| `HermesSettings.kt` | Endpoint + Token, Validierung der Adresse, Aufbau der Request-URL |
| `HermesSettingsStore.kt` | Persistenz in SharedPreferences (`hermes_settings`, vom Backup ausgenommen) |
| `HermesClient.kt` | Schnittstelle zum Agent, Ergebnis- und Fehlerklassen (`HermesError`) |
| `HermesGatewayClient.kt` | Implementierung über die OpenAI-kompatible API des Gateways |
| `HttpTransport.kt` | HTTP-POST über `HttpURLConnection` (Timeouts: 10 s Verbindung, 90 s Antwort) |
| `AndroidNetworkMonitor.kt` | Prüft vor dem Senden, ob das Gerät ein Netzwerk hat |
| `ChatUiState.kt`, `ChatViewModel.kt` | Verlauf, Sende-Warteschlange, Fehler und „Erneut senden" |
| `ChatAdapter.kt` | RecyclerView-Zeilen des Verlaufs |

- **Einrichtung:** Auf dem Startbildschirm „Einstellungen" → Gateway-Adresse (z. B.
  `http://192.168.1.10:8642`) und Token eintragen → „Speichern". Die App sendet an
  `<Adresse>/v1/chat/completions` (Angabe mit `/v1` oder der vollständigen URL geht auch) mit
  `Authorization: Bearer <Token>`; ohne Token entfällt der Header. Die API ist zustandslos, deshalb
  gehen die letzten 20 Nachrichten des Verlaufs mit.
- **Klartext-HTTP:** `usesCleartextTraffic` ist aktiv, weil das Gateway im Heimnetz meist ohne TLS
  läuft. Das Token geht dann unverschlüsselt durchs Netz — außerhalb des Heimnetzes `https://` nutzen.
- **Bedienung:** Die Nachrichten im Verlauf sind fokussierbar und per D-Pad durchblätterbar. Ist
  eine Antwort höher als der sichtbare Verlauf, blättert D-Pad hoch/runter erst seitenweise durch
  sie, danach springt der Fokus weiter. Bei jeder neuen Nachricht oder Antwort scrollt der Verlauf
  zu ihr, Oberkante bündig (ein Fokus im Verlauf wandert mit). Neue Nachrichten während einer
  laufenden Anfrage werden der Reihe nach gesendet.
- **Fehler:** Kein Netzwerk, Gateway nicht erreichbar, Zeitüberschreitung, abgelehntes Token
  (HTTP 401/403), Token mit ungültigen Zeichen, Serverfehler und unlesbare Antworten erscheinen als
  Meldungsleiste mit „Erneut senden", bei Adress-/Token-Problemen zusätzlich „Einstellungen öffnen"
  (fokussiert, auch wenn derselbe Fehler direkt noch einmal auftritt). Die betroffene Nachricht
  bleibt als „nicht gesendet" im Verlauf; OK auf ihr sendet sie erneut. Eine erneut gesendete
  Nachricht wandert ans Ende des Verlaufs, die Antwort erscheint direkt darunter. Zurück blendet die
  Meldung aus.
- **Tests:** Client mit Fake-Transport, ViewModel mit Fake-Client (`app/src/test/.../hermes/`).

## Eckdaten

| | |
|---|---|
| Sprache | Kotlin 2.0.21 |
| Build | Gradle 8.11.1 (Version in `gradle/wrapper/gradle-wrapper.properties`), Android Gradle Plugin 8.7.3 (Version in `gradle/libs.versions.toml`) |
| SDK | `minSdk` 21, `compileSdk`/`targetSdk` 35 |
| JDK | 17 |
| Package | `org.openaustria.googletv` (Debug: `org.openaustria.googletv.debug`) |

## Voraussetzungen

- JDK 17
- Android SDK mit Platform 35 (z. B. über Android Studio); Pfad per `ANDROID_HOME`
  oder `local.properties` (`sdk.dir=/pfad/zum/sdk`, nicht eingecheckt)
- `adb` (Android SDK Platform-Tools) zum Installieren

## Gradle-Wrapper

Der Gradle-Wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) ist eingecheckt und auf
Gradle 8.11.1 festgelegt. Eine lokale Gradle-Installation ist **nicht** nötig: `./gradlew` lädt
beim ersten Aufruf die passende Distribution (Prüfsumme in
`gradle/wrapper/gradle-wrapper.properties`). Unter Windows `gradlew.bat` statt `./gradlew`.

Die CI nutzt denselben Wrapper (`.github/workflows/ci.yml`, Job `android-build`) und validiert
dabei die Prüfsumme von `gradle-wrapper.jar`.

Gradle-Version aktualisieren (ändert die Wrapper-Dateien, danach committen). Die Prüfsumme
immer mitgeben, sonst entfernt der Task `distributionSha256Sum` aus
`gradle-wrapper.properties`. Den Wert für die `-bin.zip` der Zielversion von
<https://gradle.org/release-checksums/> nehmen:

```bash
./gradlew wrapper --gradle-version <version> --distribution-type bin --gradle-distribution-sha256-sum <sha256>
```

Für die aktuell festgelegte Version 8.11.1 lautet die Prüfsumme
`f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`.

## Build

```bash
./gradlew assembleDebug     # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # -> app/build/outputs/apk/release/app-release-unsigned.apk
```

Der Release-Build ist unsigniert; Signing-Konfiguration folgt bei Bedarf.

## Installation (adb)

Am TV bzw. Google-TV-Streamer die Entwickleroptionen und das Debugging aktivieren
(Einstellungen → System → Info → „Android TV OS-Build" 7× antippen; danach
Entwickleroptionen → USB-/Netzwerk-Debugging).

```bash
adb connect <tv-ip>:5555                                      # nur bei Netzwerk-Debugging
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.openaustria.googletv.debug/org.openaustria.googletv.MainActivity
```

Die App erscheint danach mit ihrem Banner in der App-Leiste des TV-Launchers. Alternativ
im Android-Studio-Emulator mit einem „Television"-Gerät (API 21+) testen.

Das CI-Artefakt `app-debug` enthält das Debug-APK jedes Builds.
