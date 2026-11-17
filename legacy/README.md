# Legacy / unsupported prototype

`bridge.py` is the original **one-entity Python BLE bridge**, superseded by the Android `BridgeService` and multi-page watch application in v0.6+.

It is retained for historical reference only. It is **not part of the current Android/watch build or supported setup procedure**. This old script has its own configuration and should not be used for sensitive lock or alarm control. In particular, do not send Home Assistant tokens using an unencrypted HTTP connection.

For supported installation steps, use [Installation](../docs/INSTALLATION.md) and the latest matching [Releases](https://github.com/AlexRosbach/HA-for-CMF/releases).
