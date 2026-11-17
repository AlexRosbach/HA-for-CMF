# Installation and first connection

[English](../README.md) · [Deutsch](../README.de.md) · [Troubleshooting](TROUBLESHOOTING.md)

## Requirements

- **CMF Watch Pro 2** with compatible custom-app firmware/loader from the [WP2 Apps community tooling](https://github.com/fmctools/cmf-watch-sdk). The project does **not** modify firmware itself.
- Android 12+ (API 31+), Bluetooth enabled, permission to scan/connect to nearby devices.
- Home Assistant instance reachable **from the phone** and a long-lived access token under your HA profile's Security section.
- Phone and watch must use **matching HA for CMF release versions**.

## Install

1. Download `ha-for-cmf-android.apk` and `ha-for-cmf.wp2app` from [Releases](https://github.com/AlexRosbach/HA-for-CMF/releases).
2. Install the Android APK from Files. Grant permission for installation from your chosen app only when needed. If Android reports *App not installed*, see [Signature conflict](TROUBLESHOOTING.md#app-not-installed).
3. In **Settings**, choose **English / Deutsch** and **System / Light / Dark**. Enter your HA URL, e.g. `https://homeassistant.example.com` (replace with your own instance), and your token. Press **Test connection & load rooms**.
4. In **Watch**, tap **Find watch**. Wake the watch. If Nothing X occupies the connection, disconnect or close it temporarily. Verify the selected device carefully.
5. Tap **Install / update watch app** to install the included matching watch package using the compatible loader; wait for the installer to finish. The APK cannot flash firmware.
6. In **Watch → Customize watch screen**, create your own pages, tap tiles to assign entities and save. Short labels work best.
7. Tap **Start bridge**, then launch the custom app on the watch. Swipe, use the crown or the arrows to change pages. **Search** offers device categories.
8. Open a harmless switch or a test light to verify that commands and states update.

## Changing language and theme

- **Settings → Language:** Deutsch / English.
- **Settings → Theme:** System / Light / Dark.
- The phone recreates its screen to apply the new selection. The running bridge shares the preference on the next watch state response; reinstalling the watch package is not normally necessary when both packages are compatible.

## Locks and safety

Lock/unlock requests from the watch trigger a **phone confirmation**; the watch itself is not an authentication factor. Phone-controlled physical door-latch opening uses a separate confirmation. Test with safe dummy/test entities before exposing actual locks. Do not rely on the project for sole home access.

## Updating

CI prereleases currently use different **test signing keys** across runs. They cannot reliably update over older APKs; you may need to uninstall, which clears saved configuration. Back up entity names/page layout manually before uninstalling. Persistent trusted release signing is still pending.

## Connectivity tips

- **HTTPS is required on all connections starting in v0.7.** Set up a TLS-enabled Home Assistant reverse proxy, trusted HTTPS hostname or VPN access to a TLS endpoint. Plain HTTP (including local IPs) is blocked to protect the token.
- Phone and HA server must be reachable from one another (VPN/local LAN as appropriate).
- Do not paste access tokens into issues or screenshots.
- Bluetooth connection and loader compatibility are experimental: success on one watch does not guarantee universal compatibility.
