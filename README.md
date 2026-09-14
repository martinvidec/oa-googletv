# oa-googletv
Google TV-App für Open-Austria Agents

Android-TV-App (Leanback) in Kotlin. Das Projekt ist derzeit ein Skelett: eine Activity,
die über den `LEANBACK_LAUNCHER` im TV-Launcher erscheint, mit Banner und App-Icon
(beides Platzhalter-Vektorgrafiken).

## Eckdaten

| | |
|---|---|
| Sprache | Kotlin 2.0.21 |
| Build | Gradle 8.11.1, Android Gradle Plugin 8.7.3 (Versionen in `gradle/libs.versions.toml`) |
| SDK | `minSdk` 21, `compileSdk`/`targetSdk` 35 |
| JDK | 17 |
| Package | `org.openaustria.googletv` (Debug: `org.openaustria.googletv.debug`) |

## Voraussetzungen

- JDK 17
- Android SDK mit Platform 35 (z. B. über Android Studio); Pfad per `ANDROID_HOME`
  oder `local.properties` (`sdk.dir=/pfad/zum/sdk`, nicht eingecheckt)
- `adb` (Android SDK Platform-Tools) zum Installieren

## Gradle-Wrapper

Der Gradle-Wrapper (`gradlew`, `gradle/wrapper/`) ist noch **nicht** eingecheckt. Einmalig mit
einer lokalen Gradle-Installation erzeugen:

```bash
gradle wrapper --gradle-version 8.11.1 --distribution-type bin
```

Die CI macht denselben Schritt vor jedem Build (`.github/workflows/ci.yml`, Job `android-build`).

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
