# HA for CMF

**Home Assistant auf der CMF Watch Pro 2 steuern**

[English](README.md) · **Deutsch** · [Releases](https://github.com/AlexRosbach/HA-for-CMF/releases) · [Einrichtung](docs/INSTALLATION.md) · [Problemlösung](docs/TROUBLESHOOTING.md) · [Sicherheit](SECURITY.md)

HA for CMF ist ein **experimentelles Open-Source-Projekt** aus einer Android-App und einer benutzerdefinierten Watch-App für die CMF Watch Pro 2. Die Android-App stellt die Verbindung zu Home Assistant her und überträgt Zustände und Aktionen über Bluetooth Low Energy auf die Uhr.

**Keine offizielle App von Nothing/CMF oder Home Assistant.** Die Uhr benötigt einen kompatiblen **WP2 Apps Loader**. Eine normale Stock-Firmware ohne Loader genügt nicht. Es handelt sich nicht um Wear OS.

## Funktionen

- Bis zu **sechs eigene Favoritenseiten** mit jeweils vier Kacheln.
- Interaktive Vorschau auf dem Smartphone: Kacheln belegen, umbenennen, Seiten sortieren und löschen.
- Licht, Schalter, Szenen, Skripte und Sensoren über Home Assistant; Funktionsumfang abhängig von der Entität.
- Seitenwechsel auf der Uhr durch Wischen, Krone oder Pfeile; einfache Suche nach Gerätetyp.
- **Deutsch oder Englisch** in den Einstellungen auswählbar.
- **System-, Hell- oder Dunkelmodus** für Handy und Uhr.
- Sicherheitsabfragen auf dem Handy für Verriegeln/Entriegeln, getrennte Türöffner-Aktion.
- Verschlüsselte Speicherung des Tokens auf dem Android-Gerät (Android Keystore).

> **Wichtig:** Die Releases sind aktuell Testversionen. Erfolgreiche GitHub-Builds bedeuten nicht, dass jede Funktion auf jeder echten Uhr und jedem Android-Gerät getestet ist. Nicht als einzigen Haustürzugang einsetzen.

## Schnellstart

1. **CMF Watch Pro 2 mit kompatiblem WP2 Apps Loader** vorbereiten. Android 12+ und Home Assistant mit erreichbarer API bereitstellen.
2. Passende **APK** und **.wp2app** derselben Version von [Releases](https://github.com/AlexRosbach/HA-for-CMF/releases) herunterladen.
3. Android-App installieren und unter **Einstellungen** Home-Assistant-URL und persönlichen Zugriffstoken eintragen. Sprache und Design auswählen.
4. Im Bereich **Uhr** nach der Uhr suchen, die App auf der Uhr installieren und Seiten in der Vorschau gestalten.
5. **Bridge starten**. Anschließend die Custom-App auf der Uhr öffnen und mit Wischgeste oder Krone zwischen den Seiten wechseln.

Weitere Einzelheiten: [Installation](docs/INSTALLATION.md), [Fehlerbehebung](docs/TROUBLESHOOTING.md), [Sprache und Design](docs/CONFIGURATION.md).

## Screenshots – Original-App, anonymisiert

![Bereinigte echte Android-Screenshots mit Raumsteuerung, Entitäten-Browser, Uhr-Vorschau und Bluetooth-Bridge](docs/screenshots/readme-ui-redacted.jpg)

<sub>Echte Android-Aufnahmen der v0.6, für die öffentliche README bearbeitet: Home-Assistant-Entitäts-IDs, Bluetooth-MAC-Adresse, Gerätebezeichnungen und Benachrichtigungen wurden durch **Beispieldaten** ersetzt. Die Bilder sind **kein** Nachweis für auf der Hardware geprüfte v0.7-Hell-/Dunkeldarstellung. Siehe [Screenshot-Richtlinie](docs/SCREENSHOTS.md).</sub>

Die App akzeptiert aus Sicherheitsgründen **nur HTTPS-Verbindungen** zu Home Assistant; HTTP ist deaktiviert, um Zugriffstokens zu schützen.

## Bekannte Einschränkungen

- Nur mit kompatibler Custom-App-Umgebung auf der CMF Watch Pro 2.
- Kein direktes Mikrofon/Voice-Assistent in der Custom-App, keine Gesundheitsdaten-Synchronisierung.
- Bluetooth und tatsächliche Uhrenbedienung müssen auf realen Geräten geprüft werden.
- Öffentliche CI-Test-APKs werden mit wechselnden Testschlüsseln signiert. Bei einem Signaturkonflikt muss die alte App gegebenenfalls deinstalliert werden. Dabei gehen die gespeicherten Einstellungen verloren.
- Reale Schlösser nur nach sorgfältigem Test mit einer ungefährlichen Test-Entität verwenden.

## Mitmachen und Sicherheit

Fehlerberichte, Übersetzungen und getestete Geräteinformationen sind willkommen. [Mitwirken](CONTRIBUTING.md) · [Sicherheitslücken vertraulich melden](SECURITY.md) · [Datenschutz und Berechtigungen](docs/PRIVACY.md) · [Änderungen](CHANGELOG.md).

Lizenz: [MIT](LICENSE). Namen und Marken von Nothing, CMF und Home Assistant gehören ihren jeweiligen Rechteinhabern. Das Projekt ist unabhängig.

Weitere Projekte: [LanLens](https://github.com/AlexRosbach/LanLens).
