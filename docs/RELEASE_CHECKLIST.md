# Release checklist

This project is already publicly visible on GitHub. **Do not mark a release stable** before completing the outstanding real-hardware, security and signing work.

## Before tagging

- [ ] Merge only reviewed changes with green Android and Rust builds.
- [ ] Confirm build logs and artifacts contain **no HA tokens, personal URLs, Bluetooth MAC addresses, private keys or sensitive screenshots**.
- [ ] Test *English* and *Deutsch* including settings, entity browser, watch page editor, dialogs and watch UI.
- [ ] Test System, Light and Dark theme on Android (including dialogs, keyboard, status bar) and the round watch UI.
- [ ] Test page editor: up to six pages, four slots each, aliases, deletion, reorder and BLE live refresh.
- [ ] Test real CMF Watch Pro 2 on a documented loader/firmware version with Bluetooth and device sleep/wake.
- [ ] Test a safe light/switch/sensor, an unavailable entity, and recovery from lost BLE / HA connectivity.
- [ ] Test watch lock requests **cannot** unlock without phone confirmation; verify replay/timeout/disconnect behaviour on safe test entities.
- [ ] Confirm old Android and watch packages show a clear version mismatch instead of silently operating incorrectly.
- [ ] Provide screenshots for both themes and languages from a **demo environment**, with EXIF and sensitive data removed.
- [ ] Confirm MIT license and [third-party notices](../THIRD_PARTY_NOTICES.md) reflect the actual SDK revision and any bundled dependencies.
- [ ] Document known defects and supported hardware/loader combinations in release notes.

## Signing and distribution

- [ ] Use **one persistent, securely protected Android release-signing key** for stable versions.
- [ ] Keep keystore and passwords **outside GitHub source**; restrict access to protected environment/secret handling.
- [ ] Maintain separate debug/test and stable release packages to avoid signature conflicts.
- [ ] Verify APK signature, install, upgrade, uninstall and data migration.
- [ ] Verify embedded `.wp2app` matches the standalone watch asset version.
- [ ] Prefer SHA-256 checksums for download verification.
- [ ] Do not distribute an APK as stable solely because CI has passed.

## Public docs

- [ ] README quick start matches tested UI.
- [ ] German README and English README agree on major capabilities.
- [ ] No placeholder/broken screenshot paths in public README.
- [ ] Update CHANGELOG, SECURITY and privacy documentation.
- [ ] Confirm private GitHub vulnerability reporting is enabled, if available, or provide a clear private contact path.
- [ ] Confirm license/trademark text is accurate.
- [ ] Reconfirm repository visibility and branch protection before inviting the public.

## Current CI notes

The GitHub Actions test workflow builds the watch package and signs the Android APK with a **temporary test key** generated on the runner. That key changes per run and is **not** suitable for a stable update channel. Public releases built this way must remain marked prerelease.

The repository does not yet claim a security review or fully verified real-device compatibility.
