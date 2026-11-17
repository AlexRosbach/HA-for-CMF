# Third-party notices

HA for CMF is an independent MIT-licensed community project. It uses external dependencies and tools to build/install the custom watch package and Android APK.

| Dependency / project | Usage | License / source |
|---|---|---|
| [WP2 Apps community SDK](https://github.com/fmctools/cmf-watch-sdk) | Rust/no_std watch API, rendering and packager/loader tooling | MIT license reported in the upstream repository; verify the exact pinned revision before redistribution |
| [Home Assistant](https://www.home-assistant.io/) | External API protocol and user-provided instance | This repository does **not** redistribute Home Assistant |
| Android SDK / Android Open Source Project | Android system APIs and command-line build tools | Subject to Android SDK / AOSP terms |
| Rust and compiler tools | Compiling watch package | Tools and their dependencies retain their own licenses |
| GitHub Actions | CI build and prerelease publication | Hosted CI service |

The watch build script pins a specific upstream SDK revision; review the upstream license and any bundled transitive dependencies before shipping a stable commercial or widely distributed app. This file is an initial notice, **not a complete Software Bill of Materials** or a legal compliance audit.

Nothing, CMF, Nothing X, Home Assistant and their marks are property of their respective owners. HA for CMF is not affiliated with, endorsed by or officially supported by those organisations.

Do not copy official logos into the app or public documentation without verifying applicable trademark rules.
