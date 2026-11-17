# Language, themes and watch page configuration

## App language

Open **Settings → Language**, select **Deutsch** or **English**. This changes the Android UI and the watch's built-in navigation/status labels. **Home Assistant entity names, room names and custom page titles stay as you entered them** and are not machine-translated.

Translation strings are maintained centrally in `android/src/main/java/org/haforcmf/LocaleKit.java`. If you encounter untranslated labels, submit a sanitized screenshot or PR. The embedded watch font supports a limited character set; short ASCII-compatible labels are recommended.

## Appearance

Open **Settings → Theme** and select:
- **System:** follow your Android light/dark setting.
- **Light:** light surfaces and dark text.
- **Dark:** dark surfaces and high-contrast light text.

The selected appearance is also sent to the watch through the BLE state snapshot. The watch's true color rendering and round clipping may vary by loader/firmware; verify on your device.

## Custom watch pages

1. Open the **Watch** tab; the live circular preview appears above connection controls.
2. Tap **+ New favourite page** (maximum 6).
3. Rename a page, or move it left/right.
4. Tap any of its **four tiles** and choose an HA entity. Optionally define a **short label** of up to 10 characters.
5. Use the preview arrows to move between pages.
6. Save and sync. A running bridge reloads the layout at the next state interaction; otherwise start/restart it.

The page layout is encrypted locally with Android Keystore; uninstalling removes it.

## Supported entity actions

Light and switch toggles, one-tap scenes/scripts/buttons, read-only sensors and phone-confirmed lock controls are implemented as supported by the HA entity. Device attributes determine available actions; not every entity can be controlled.

## Watch search

Tap **Search / Suche**, choose the broad category **Lights, Switches, Scenes, Sensors**, then page through results. There is **no on-watch keyboard and no voice assistant** in current versions.

## Limitations

The phone must retain a working BLE bridge and a connection to Home Assistant. Background restrictions, BLE contention with Nothing X, and incompatible watch loaders can prevent syncing. See [Troubleshooting](TROUBLESHOOTING.md).
