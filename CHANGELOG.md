# Changelog

## 2026.09.11.01

- Switch to date-based versioning (`YYYY.MM.DD.NN`), matching this author's other projects.
- Metadata only otherwise: author field and AI-assisted note in the README.

## 0.2.0

### Added

- Port 19 additional effects from [davidcoulson/kiosk-satellite](https://github.com/davidcoulson/kiosk-satellite)'s `feature/rgb-led-support` branch: Sunrise/Sunset, Moonlight Glow, Lightning Storm, Wake-Up Alarm, Candle Flicker, Fairytwinkle, Fireworks Burst, Beacon Pulse, Heartbeat Pulse, Soft Glow, Rolling Fog (Pronounced), three Pacifica variants, three Aurora variants, Bubbles and Disco Sparkle — alongside this plugin's original six (None/Pulse/Blink/Rainbow/Candle/Random). Every ported effect is independently stateful (a fresh instance per selection, matching this plugin's own effect lifecycle) and covered by an elapsed-time sweep in `LedTest` checking every channel stays in range across each effect's full period, including the two slowest: Sunrise/Sunset's 300s cycle and Wake-Up Alarm's one-shot 600s ramp.

## Unreleased

### Changed

- Document automatic setting saves and the on-device text edit dialog.

- Consolidate the manifest and interfaces into the first public SDK 1, including optional KS observations and transient controls. The LED plugin requests neither host capability.
- Document GitHub Actions releases as the installation path and local ZIPs as developer testing only.

- Document gesture assignments, drawer shortcuts and Home Assistant buttons for plugin actions.

- Build and attach plugin assets when a GitHub release is published instead of on pushes or pull requests. Keep manual runs on existing release tags and verify that the tag matches the manifest version.

### Fixed

- Handle dotted Android platform directories such as `android-37.0` without crashing. Release builds explicitly select Android 35 and tests cover mixed platform installations.

- Set up the Android SDK in GitHub Actions before installing build tools so `sdkmanager` is available on the runner.

## 0.1.0

### Added

- Initial SDK 1 plugin for panels exposing `/dev/ledjni`.
- Color picker, brightness, configurable channel drive and six effects.
- Direct JNI access and an optional root helper with bounded requests.
- Home Assistant RGB light with settings synchronization.
- Hardware status, RGB test and simulation mode.
- Standalone build tools, protocol tests and maintainer handoff documentation.
