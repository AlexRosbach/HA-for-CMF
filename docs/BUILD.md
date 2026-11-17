# Building from source

Both Android and watch packages are built by the repository's [GitHub Actions workflow](../.github/workflows/build.yml).

## Watch package

Install the Rust target, Git, Python and the build dependencies required by the upstream community SDK (including Nix and uv as used in CI). Then:

```bash
rustup target add thumbv7em-none-eabi
bash build.sh
```

The watch package is written to `.build/ha-for-cmf.wp2app`. It is intended for a compatible community WP2 Apps loader, **not** for stock Wear OS or official firmware flashing.

## Android APK

Install JDK 17, Android Platform 35 and Build Tools 35. Build the watch package first. Supply your own Android signing credentials:

```bash
export ANDROID_JAR="$ANDROID_HOME/platforms/android-35/android.jar"
export ANDROID_BUILD_TOOLS="$ANDROID_HOME/build-tools/35.0.0"
export APK_KEYSTORE="/secure/path/release-or-test.jks"
export APK_KEY_ALIAS="my-key"
export APK_KEYSTORE_PASSWORD="from-a-secret-store"
bash android/build.sh
```

The APK is written to `android/.build/ha-for-cmf-android.apk`.

**Never commit keystores, signing secrets, HA access tokens or personal BLE logs.** CI currently creates a fresh test key for each run, so installation over another test build can fail. For production, configure a persistent protected release key separately; never store it directly in the workflow or repository.

## Testing before a release

- Build watch Rust and Android Java packages and verify APK signature.
- Test language and all three theme options on phone and watch preview.
- Confirm watch BLE synchronization, category search, page navigation and lock confirmation on an actual device.
- Check screenshots for real identifiers, addresses and token values.
- Keep releases marked *prerelease* until real-device compatibility and the signing/update path have been validated.

Further details: [Troubleshooting](TROUBLESHOOTING.md), [Security policy](../SECURITY.md).
