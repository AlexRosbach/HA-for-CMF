# Security policy

## Supported versions

The latest prerelease is maintained on a **best-effort** basis until real-device testing and a stable signing process are complete.

| Release | Support |
|---|---|
| Latest prerelease | Best effort; not production certified |
| Previous prereleases | Not actively supported |
| Development branches | No support guarantee |

## Reporting a vulnerability

**Do not file a public issue** for sensitive vulnerabilities, Home Assistant tokens, lock bypasses, BLE replay scenarios or screenshots showing private household data.

Use GitHub's **Private vulnerability reporting / Security Advisories** for this repository if enabled. Otherwise reach the maintainer privately through their existing public project contact channel; do not post exploit details or credentials to public issues. Include affected version, loader/firmware, Android version, minimal reproduction steps and potential impact, with all private values redacted.

## Security boundaries

- HA credentials live on the Android device, not in the watch package. Settings are encrypted using Android Keystore-backed AES-GCM.
- The watch BLE message channel is **not itself strong authorization**. Never treat an address, paired connection or visual confirmation on the watch as sufficient permission for a door unlock.
- Watch-originated lock/unlock requests require explicit confirmation on the **phone**. The physical latch/open action is separate and phone-only.
- Do **not** use this app as a sole means of property access, alarm control or safety-critical automation.
- Starting with v0.7, **HTTPS is required** for all Home Assistant connections. Android cleartext traffic is disabled and the app rejects `http://` URLs. Set up TLS on your Home Assistant reverse proxy or use a TLS-enabled remote endpoint; never expose the HA long-lived token over HTTP.
- Public CI builds use disposable test signing credentials. They are **not suitable for trusted seamless updates**; a protected persistent release-signing process is needed before stable distribution.
- Do not submit actual bearer tokens, lock names, GPS coordinates, house addresses, MAC addresses, screenshots of personal notifications or unredacted logs.

## Release security checks

Review [Release checklist](docs/RELEASE_CHECKLIST.md) before publishing and [Privacy policy](docs/PRIVACY.md) for data handling. Confirm watch pairing is tested on physical devices and that lock actions fail safely under disconnect, restart and malformed BLE input.

This policy is modelled on the responsible-reporting approach used by [LanLens](https://github.com/AlexRosbach/LanLens); it does not claim a third-party security audit.
