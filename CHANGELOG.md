# Changelog

## Unreleased

### Changed

- Build and attach plugin assets when a GitHub release is published instead of on pushes or pull requests. Keep manual runs on existing release tags and verify that the tag matches the manifest version.

### Fixed

- Set up the Android SDK in GitHub Actions before installing build tools so `sdkmanager` is available on the runner.

## 0.1.0

### Added

- Initial SDK 2 plugin for panels exposing `/dev/ledjni`.
- Color picker, brightness, configurable channel drive and six effects.
- Direct JNI access and an optional root helper with bounded requests.
- Home Assistant RGB light with settings synchronization.
- Hardware status, RGB test and simulation mode.
- Standalone build tools, protocol tests and maintainer handoff documentation.
