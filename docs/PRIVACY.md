# Privacy, permissions and data safety

HA for CMF is an independent, experimental app. There is **no hosted HA for CMF account or central cloud service** in the project's design.

## Data flows

- The **phone** connects to the Home Assistant URL you configure and sends authenticated API requests using your personal long-lived token.
- The phone queries device names, entity IDs, areas and states needed for browsing and watch tiles.
- Only compact watch tile labels, state indicators, pagination and commands are sent over the custom BLE channel. **The Home Assistant bearer token is not copied to the watch.**
- Local settings, including token and watch layout, are encrypted at rest using an AES-GCM key from Android Keystore. Removing the app removes those settings.
- The project does not implement analytics, advertising identifiers or a proprietary telemetry endpoint. Home Assistant and any VPN/proxy you choose have their own logging policies.

## Android permissions

| Permission | Reason |
|---|---|
| Internet | Connect to your Home Assistant instance |
| Bluetooth Scan / Connect | Find and communicate with the watch |
| Fine Location | Android BLE compatibility requirements, not a location-tracking feature |
| Foreground service / connected device | Maintain the active phone-watch bridge |
| Notifications | Show bridge status and safety confirmation for watch-initiated lock actions |

## Sensitive controls

BLE communication is not a strong identity proof. Watch lock/unlock requests must require separate confirmation on the phone. The app must not be relied on for emergency or sole home access. Avoid exposing sensitive entities until tested.

## Sharing screenshots

Screenshots and logs may contain home URLs, tokens, lock names, area names, device MAC addresses, other notifications, or phone details. **Redact before uploading to GitHub**. See [SCREENSHOTS.md](SCREENSHOTS.md).

## HTTP caveat

The Android manifest currently allows cleartext HTTP for private LAN Home Assistant deployments. This is **not a guarantee of transport confidentiality**; using HTTPS is strongly recommended, especially away from your home network.

## Data deletion

Uninstall the app to remove its local settings. To revoke access immediately, **delete its long-lived token in Home Assistant**. Uninstalling the app does not automatically revoke a Home Assistant token at the server.
