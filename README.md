# HA for CMF

Control one Home Assistant light or switch from a CMF Watch Pro 2. The watch displays the state reported by Home Assistant and offers a single on/off button. It refreshes the state every three seconds while the app is open.

The Mac bridge connects to the watch over Bluetooth LE and to Home Assistant over HTTP(S). Configure your own Home Assistant URL and entity ID. Keep your access token on the Mac.

## Requirements

- CMF Watch Pro 2 with [WP2 Apps](https://github.com/cmf-tech-community/wp2-apps) already working, including its app loader. This project does not install watch firmware.
- Apple Silicon Mac with Bluetooth enabled, plus Git, Python 3.12+, uv, Nix and Rustup.
- A Home Assistant installation reachable from the Mac, a `light.*` or `switch.*` entity, and a long-lived access token. Use HTTPS when sending the token over an untrusted network. See the [Home Assistant API docs](https://developers.home-assistant.io/docs/api/rest/).

## Build and install the watch app

```bash
rustup target add thumbv7em-none-eabi
bash build.sh
cd .build/wp2-apps
uv run --locked wp2apps scan --show-address
uv run --locked wp2apps install ../ha-for-cmf.wp2app --address WATCH_UUID
```

Substitute the displayed Bluetooth UUID for `WATCH_UUID` and note the numeric slot printed by the installer. Dismiss the watch's **Paired** prompt before starting the bridge.

## Start the bridge

From the same `.build/wp2-apps` directory:

```bash
read -rs 'HA_TOKEN?Home Assistant token: '
export HA_TOKEN
uv run --locked python ../../bridge.py --address WATCH_UUID --slot 0 \
  --url http://homeassistant.local:8123 --entity light.example
unset HA_TOKEN
```

The hidden token prompt above uses macOS's default zsh shell. Replace the URL, example entity ID, and slot with your own. Open **Custom Apps → ha-for-cmf** once the bridge reports that the watch is connected. Dismiss any **Paired** prompt first. Keep the bridge running and the Mac in Bluetooth range. If the phone's companion app is connected, turn off Bluetooth on the phone during the test.

WP2 Apps is an experimental community project. This initial release uses a Mac bridge and supports one configurable entity per running bridge.
