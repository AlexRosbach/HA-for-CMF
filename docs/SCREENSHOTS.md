# Screenshot standards for public documentation

## Current screenshot status

The repository already contains `docs/four-entities.png` (older emulator example) and `docs/watch-mockups.png` (earlier concept design). They are **not** proof of the current v0.7 UI or real-device compatibility.

**Fresh screenshots have not yet been added.** Images supplied during private troubleshooting must be reviewed and sanitized before publication. Avoid presenting mockups as real device captures.

## Screenshots needed for the public README

| Suggested file | Content | Notes |
|---|---|---|
| `docs/screenshots/android-home-light.png` | Home and rooms | Sample names only |
| `docs/screenshots/android-home-dark.png` | Dark-mode dashboard | Same demo setup |
| `docs/screenshots/android-entities-en.png` | English entity browser | Synthetic entities and areas |
| `docs/screenshots/android-watch-editor.png` | Interactive watch preview | Non-sensitive sample tiles |
| `docs/screenshots/android-settings.png` | Language/appearance selectors | Token field hidden; preferably use demo URL |
| `docs/screenshots/watch-light.jpg` | Real watch with light UI | Round display clearly visible |
| `docs/screenshots/watch-dark.jpg` | Real watch with dark UI | Round display clearly visible |

These paths are **planned filenames**, not currently published image files. Do **not** add references to nonexistent paths in the README. Once sanitized files are committed, update the screenshot gallery.

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
