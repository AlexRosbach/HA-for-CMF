# HA for CMF

Show up to four Home Assistant entities on a CMF Watch Pro 2. Tap a light or switch to turn it on or off. Scenes, scripts, and button entities can be triggered as one-tap actions. Other entities are read-only and can display a state and unit, such as temperature or humidity.

![Four entity tiles running in the watch emulator](docs/four-entities.png)

The Android phone loads the entity list from Home Assistant and talks to the watch over Bluetooth LE. The phone is the bridge, including while the screen is locked if the foreground service stays connected. The watch does not need the Home Assistant token.

## Connect on Android

1. Your watch must already have the [WP2 Apps firmware and loader](https://github.com/cmf-tech-community/wp2-apps). The Android app installs or updates only the bundled HA for CMF watch app. It does not flash firmware.
2. Download the Android APK from [Releases](https://github.com/AlexRosbach/HA-for-CMF/releases), install it on Android 12 or later, and open **HA for CMF**.
3. Open your Home Assistant profile. Under **Security → Long-lived access tokens**, create a token for the app. Use an HTTPS URL on untrusted networks. Do not share the token. The Android app keeps it in memory for the active session.
4. In HA for CMF, enter the URL and token. Tap **Geräte auswählen (max. 4)**, search by friendly name or entity ID, and select one to four entries. The order in which you select them becomes their order on the watch.
5. Close competing watch apps, such as Nothing X, or disconnect them from the watch. Keep the phone's Bluetooth on. In HA for CMF tap **Uhr suchen**, allow Nearby devices, and select your Watch Pro 2 from the live Bluetooth list. The list also includes bonded and unnamed BLE devices because some watches do not advertise their full retail name.
6. Exit any open WP2 app on the watch. On the phone tap **Install or update watch app**. Wait for success, then dismiss the watch's **Paired** notification if shown.
7. Tap **Bridge starten**. When the phone says it is ready, dismiss the watch's **Paired** prompt and open **Custom Apps → ha-for-cmf**. Use **Bridge stoppen** to end the Bluetooth session or before changing the selection.

Long names and states are shortened for the small display. The initial Android release is experimental, and compatibility with Nothing X running alongside it has not been verified. The watch UI has been tested with a simulated BLE bridge. The Android Bluetooth path still needs a physical phone-to-watch test.

## Build from source

For the watch package, use Git, Python, Rustup, uv, and Nix:

```bash
rustup target add thumbv7em-none-eabi
bash build.sh
```

For Android, install JDK 17, Android SDK Platform 35, and Android Build-Tools 35. Set `ANDROID_JAR` and `ANDROID_BUILD_TOOLS` to their installed paths. Set `APK_KEYSTORE`, `APK_KEY_ALIAS`, and `APK_KEYSTORE_PASSWORD` to your own signing credentials. Run `bash android/build.sh` after building the watch package.

The original single-entity Mac bridge remains available in [v0.1.0](https://github.com/AlexRosbach/HA-for-CMF/releases/tag/v0.1.0).

## Redesigned UI (experimental)

The phone setup now uses cards for pairing, Home Assistant configuration and the bridge. Bluetooth discovery displays live BLE results and paired devices, with progress and scan errors. If the watch has an unexpected name, verify the Bluetooth address before selecting it. The watch display uses a dark CMF-inspired style with orange active tiles and a clear connection state, while retaining the existing one-to-four entity protocol.

**Scope:** This does not add a native Wear OS app or support for heart rate, dimming, or health sync. Home Assistant scenes, scripts and buttons are now available as one-tap actions. The CMF Watch Pro 2 requires the separate WP2 Apps loader and firmware. Physical discovery, pairing, and app installation remain unverified on real hardware; the Android build and watch firmware should be tested before release.


## GitHub Actions test builds

The `Build Android and watch packages` workflow creates an Android APK and the `ha-for-cmf.wp2app` watch package. Open **Actions**, choose the latest successful run, and download the `ha-for-cmf-ci-packages` artifact. The Android APK is **signed with a disposable CI test key**, not the release signing key. It may require uninstalling an older app signed with another key (which clears local app state). Do not enter your Home Assistant access token into a build from an untrusted source.

A green CI build verifies compilation/package generation, **not** that real Watch Pro 2 Bluetooth binding works. Physical device testing is still required before a stable release.

The watch must already have the compatible custom WP2 Apps loader installed. This project does not flash the watch or modify its original firmware.

## Rooms, advanced entity browser and locks (v0.4.0 experimental)

- Open **Favoriten für die Uhr auswählen** to browse entities with multi-term search (name, ID, room, state) and filters for room, type, availability, and favourites. Select up to 24 entities; the watch groups them by Home Assistant room in pages of four. The watch page arrows navigate between rooms.
- **Räume & Geräte** on Android loads live room assignments from Home Assistant using `areas()`, `area_name()`, and `area_entities()` through `/api/template`. Pick a room to see its devices and controls. The first 30 devices are shown in the room view.
- **Lock actions require explicit confirmation.** On Android every unlock and lock action shows a confirmation dialog naming the room and entity, then rechecks its live state. On the watch, the first tap on a lock shows **UNLOCK? / LOCK?** and **TAP AGAIN**; only a second tap within 5 seconds requests the action.
- **Unlock** calls `lock.unlock`; **Tür öffnen (Schnapper)** calls `lock.open` only for locks advertising `LockEntityFeature.OPEN` (`supported_features & 1`). Each action has its own explicit confirmation dialog on Android. The watch deliberately offers only lock/unlock, not physical unlatching.
- Areas not exposed by older Home Assistant versions are grouped under **Ohne Raum**. Filtering and controls still work.
- The launcher now uses an orange app icon, and native Android labels use dark foreground colors on light cards.

Security: The Android app retains your access token in memory for the session only. Do not leave a paired phone unlocked and unattended when adding door locks to watch favourites. Watch lock actions need an active bridge. Test any lock against the correct entity and door before relying on it for access. The new watch and phone versions must be installed together for room page metadata support.

This build is a **pre-release** because real CMF Watch Pro 2 Bluetooth and locking behavior still need device-level verification.
