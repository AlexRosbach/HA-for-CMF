# HA for CMF – Kurzanleitung (Deutsch)

**HA for CMF** bringt ausgewählte Home-Assistant-Entitäten auf die CMF Watch Pro 2 (mit kompatiblem Community-WP2-Apps-Loader). Die App besteht aus einer Android-Bridge und einer separaten Uhr-App. Das Projekt ist **inoffiziell und experimentell**.

## Installation

1. Android-APK und `.wp2app` aus demselben [GitHub Release](https://github.com/AlexRosbach/HA-for-CMF/releases) herunterladen.
2. Die APK auf Android 12+ installieren. Bei Signaturkonflikt mit älteren CI-Testbuilds muss die vorherige App deinstalliert werden.
3. Unter **Einstellungen** Sprache **Deutsch/Englisch** und Darstellung **System/Hell/Dunkel** wählen.
4. Home Assistant URL und persönlichen langlebigen Zugriffstoken eingeben.
5. Im Tab **Uhr** Favoritenseiten erstellen, umbenennen, Kacheln zuordnen und mit der interaktiven Vorschau kontrollieren.
6. Uhr suchen, Uhr-Paket installieren und die Bridge starten. Danach die Custom-App auf der Uhr öffnen.

## Bedienung und Grenzen

- Bis zu **6 Seiten mit je 4 Kacheln**; blättern per Krone, Pfeilen oder Wischen.
- Suche nach Kategorien Licht, Schalter, Szene und Sensor.
- Entsperren/Öffnen von Türen wird **nicht ohne zusätzliche Bestätigung** vom Handy ausgeführt.
- Spracherkennung über das Uhrenmikrofon ist **nicht Bestandteil** der aktuellen Version.
- Echter Hardwarebetrieb ist noch nicht umfassend verifiziert; GitHub-CI meldet nur die erfolgreichen Builds.

Weitere Infos: [Installation](INSTALLATION.md) · [Fehlersuche](TROUBLESHOOTING.md) · [Sicherheit](../SECURITY.md) · [Datenschutz](PRIVACY.md).
