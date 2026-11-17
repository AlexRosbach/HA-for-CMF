# Contributing to HA for CMF

Thank you for contributing to an independent community integration.

## Before you open an issue

Check [Troubleshooting](docs/TROUBLESHOOTING.md), the [latest prerelease](https://github.com/AlexRosbach/HA-for-CMF/releases) and open issues. For security vulnerabilities use [private reporting](SECURITY.md), **not** public issues.

Useful bug reports contain:
- Android version and phone model (no serial numbers)
- CMF Watch Pro 2 firmware and WP2 Apps loader version
- HA version and affected entity *type* (redact private names)
- Precise steps, expected vs actual results
- Sanitised screenshots or logs; never credentials, lock identities, BLE addresses or home network URLs
- Whether the same issue occurs after updating **both** packages

## Local development

The watch is built with Rust/no_std and the WP2 SDK; Android uses Java 17 with the Android SDK Platform and Build Tools 35. Read [README](README.md#build-from-source).

Create a feature branch, test builds, and keep changes small. Do not commit APKs, signing keys, HA tokens, phone backups or generated secrets.

For UI changes, test **both languages** (German/English), all **three themes** (system/light/dark), legibility on the round watch display and Android status bar insets. Prefer short Watch labels that fit 466px displays. Run the GitHub Actions build and include manual test results where available.

## Pull requests

Explain purpose, screenshots with dummy/sanitised data, compatibility and risks. A successful compiler run is not a substitute for real CMF Watch Pro 2 testing. Update documentation and [CHANGELOG](CHANGELOG.md) for visible changes.

## Documentation screenshots

See [docs/SCREENSHOTS.md](docs/SCREENSHOTS.md): never upload actual private HA URLs, usernames, access tokens, door labels, addresses or BLE MACs.

## Language

The app contains a small central text dictionary in `LocaleKit.java` and a locale flag in the watch BLE snapshot. New UI strings must have German and English forms. Do not automatically translate Home Assistant entity names or room names supplied by users.

## Licensing

Contributions to this MIT-licensed project are submitted under the repository's [MIT license](LICENSE). Respect third-party licenses in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
