# Screenshot standards for public documentation

## Public gallery

The public README embeds [readme-ui-redacted.jpg](screenshots/readme-ui-redacted.jpg). It is a **montage derived from actual v0.6 Android screenshots**, not a render or screenshot of the v0.7 language/theme release. The source screenshots were provided during real-device troubleshooting.

Changes applied before publication:
- Cropped away the phone status bar, notifications and the song/media title.
- Replaced all visible Home Assistant entity IDs with dummy IDs, for example `light.living_room_ceiling`.
- Replaced the Bluetooth device MAC with `XX:XX:XX:XX:XX:XX`.
- Changed room/device names where they may reveal household details.
- No real Home Assistant domain, access token or password is included.
- Exported through Pillow without EXIF metadata.

This gallery is for showing **real UI navigation and styling**, not verifying actual Bluetooth compatibility. For a future v0.7 gallery, capture additional light, dark, German and English screens with **demo entities**. An old watch photo showing the removed Voice Assistant and the old blue mockups **must not** appear as current release imagery.

## Redaction rules

1. Replace real Home Assistant URLs/domains with `ha.example.invalid` or a local demo.
2. Remove or regenerate **all** tokens, API keys, passwords, QR codes, and identifiable network addresses before capture.
3. Replace room/entity names involving door locks, household occupants, addresses, schedules, cameras and alarm systems with `Demo Room`, `Demo Light`, `Test Switch`, etc.
4. Crop or blur notification text, status bar identifiers, GPS clues, account avatars, serial numbers, Bluetooth MAC addresses and background photographs.
5. Verify the edited image again at 100% zoom. Metadata/EXIF should be stripped for public exports.
6. Prefer a **separate demo HA instance** rather than manually redacting live, sensitive data.
7. Record app, watch and loader versions in the screenshot PR. Clearly label simulator renders and design mockups.

## How to contribute

Submit a pull request with checked, sanitized images and a short description. The project maintainer should inspect images again before publication. See [Security Policy](../SECURITY.md).
