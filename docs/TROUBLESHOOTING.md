# Troubleshooting

## "App not installed"

Public test APKs are currently signed with a **fresh, disposable key** in GitHub Actions. Android refuses an update if the previous app uses a different signature. Uninstall the previous HA for CMF first, then install the current APK. **Uninstalling erases local configuration**. Make a note of your page layout and keep your HA token safely outside screenshots. For seamless updates the project needs a stable protected release signing key.

## Watch not found

- Confirm **Bluetooth Scan** and **Bluetooth Connect / Nearby Devices** permissions.
- Wake the watch and bring it close to the phone.
- Close/disconnect competing apps such as Nothing X when they hold the BLE connection.
- Re-scan and verify the watch before choosing a MAC/unknown advertised device.
- Confirm compatible WP2 Apps loader; stock firmware is not sufficient.

## "Waiting", "Offline" or no entities on the watch

- Start the bridge from the phone; keep Bluetooth available.
- Install **matching Android and watch package versions**.
- Check Home Assistant URL/token and network connectivity from the phone.
- Create at least one favourite page with a configured entity using the phone preview.
- Retry after reconnecting BLE. A green "Connected" indicator only means a valid bridge snapshot was received; the watch may still have empty pages.

## Page changes not visible

Save the custom pages in the phone editor. Verify the bridge is running; restart it if the watch doesn't receive the next snapshot. If versions differ, reinstall the matching watch app.

## The watch UI is clipped

Custom watch rendering uses a 466×466 coordinate system with a round safe area; different loader or firmware versions may clip outer pixels. Update both packages, use **short per-tile labels** and provide sanitized real watch photos in an issue with your loader version. Never post lock/door names.

## Watch flashes or flickers

The app avoids redraws for identical snapshots, but some custom loaders and BLE update cycles may repaint. Check watch/loader versions and note whether the flicker happens during Search, page changes or idle status updates.

## English / dark mode not applied to watch

- Verify Android **Settings → Language / Theme**.
- Restart the bridge if necessary; preference flags are sent with every watch snapshot.
- Update both v0.7+ APK and matching watch app. Older watch versions don't understand the extended snapshot metadata.

## Home Assistant errors

- Use a reachable URL and a valid long-lived access token.
- Prefer HTTPS for external access; verify TLS/certificates.
- Ensure the selected entity exists and its state is not `unavailable`.
- Older Home Assistant versions or restricted template API access may not expose room assignments.

## Problems with door locks

Do not diagnose against a live door if you might be locked out. Reproduce safely with a test or virtual lock. A watch command **must** require the user to confirm on the phone, and physical opening is never sent from the watch alone.

## Reporting issues

Use [CONTRIBUTING.md](../CONTRIBUTING.md). Redact tokens, HA URL, notification bar personal details, private entity names, door labels, MAC addresses and home network information before posting.
