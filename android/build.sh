#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")" && pwd)
: "${ANDROID_JAR:?Set ANDROID_JAR to the Android 35 android.jar}"
: "${ANDROID_BUILD_TOOLS:?Set ANDROID_BUILD_TOOLS to the build-tools 35 directory}"
: "${APK_KEYSTORE:?Set APK_KEYSTORE to your signing keystore}"
: "${APK_KEY_ALIAS:?Set APK_KEY_ALIAS to the signing key alias}"
: "${APK_KEYSTORE_PASSWORD:?Set APK_KEYSTORE_PASSWORD to the signing keystore password}"

out="$root/.build"
mkdir -p "$out"
classes=$(mktemp -d "$out/classes.XXXXXX")
trap 'rm -rf "$classes"' EXIT
find "$root/src/main/java" -name '*.java' -print0 | xargs -0 javac --release 17 -cp "$ANDROID_JAR" -d "$classes"
jar cf "$classes/code.jar" -C "$classes" org
mkdir "$classes/assets"
cp "$root/../.build/ha-for-cmf.wp2app" "$classes/assets/"
"$ANDROID_BUILD_TOOLS/d8" --release --min-api 31 --lib "$ANDROID_JAR" --output "$classes" "$classes/code.jar"
"$ANDROID_BUILD_TOOLS/aapt2" link -I "$ANDROID_JAR" --manifest "$root/src/main/AndroidManifest.xml" \
  -A "$classes/assets" --version-code 2 --version-name 0.2.0 -o "$classes/unaligned.apk"
(cd "$classes" && zip -q unaligned.apk classes*.dex)
"$ANDROID_BUILD_TOOLS/zipalign" -f 4 "$classes/unaligned.apk" "$classes/aligned.apk"
"$ANDROID_BUILD_TOOLS/apksigner" sign --ks "$APK_KEYSTORE" --ks-key-alias "$APK_KEY_ALIAS" \
  --ks-pass env:APK_KEYSTORE_PASSWORD --out "$out/ha-for-cmf-android.apk" "$classes/aligned.apk"
"$ANDROID_BUILD_TOOLS/apksigner" verify "$out/ha-for-cmf-android.apk"
printf '%s\n' "$out/ha-for-cmf-android.apk"
