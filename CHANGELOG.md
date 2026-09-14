# Changelog

## 0.2.4-20260914

- Fix: 0.2.3's guard never fired. It refused to start only on `ENOENT`/`ENODEV`, on the reasoning that SELinux denials surface as `EACCES` and so a genuine `ENOENT` must mean the node is absent. The first half is true and the conclusion does not follow: SELinux denies `untrusted_app` the lookup in `/dev`, so `open()` returns `EACCES` *before* it can discover the file is not there. Tested on a panel with no LED hardware whatsoever, the plugin still reported errno 13 and enabled itself exactly as before. Only root can distinguish the two cases, and root is precisely what may be unavailable.
- The guard now refuses on **any** probe failure rather than a missing node specifically, which is the condition that was actually meant: this plugin cannot open the device, so it cannot work.
- Three exemptions, all of which defer instead of refusing. Simulation mode never opens the device. Root fallback, when armed, defers to `detect()` — the root helper may reach a node this process cannot, and asking it during `start()` would block the host's enable call behind a root-manager prompt that can sit unanswered for minutes. A native library that will not load leaves the plugin unable to probe at all, which makes the question unanswerable rather than answered no.
- The refusal message names both escape hatches. A plugin's settings page stays reachable while it is disabled ("Enable this plugin from its entry row to run it"), so Root fallback and Simulation can both be switched on after a refusal — verified on a panel, since refusing otherwise risked stranding users outside the settings that would fix it.

## 0.2.3-20260914

- Change: a panel with no `/dev/ledjni` node can no longer enable this plugin at all. The hardware probe ran on the worker thread, after `start()` had already returned, so Kiosk Satellite recorded the plugin as enabled and the missing device showed up only as an error status — on a plugin that stayed switched on, kept re-probing hardware that will never appear, and offered settings that could never do anything. The probe now runs synchronously in `start()`, and a missing node throws; the host's `PluginBridge.enable` routes that through `fail()`, which writes `enabled=false` and keeps the message. Enabling the plugin on a panel without the LED driver now fails with an explanation instead of appearing to succeed.
- Only a genuinely absent node blocks enabling (`ENOENT`/`ENODEV`). A node that exists but is unreadable (`EACCES`) is untouched, because the root fallback may still reach it. Simulation mode is exempt and stays enableable on any panel — it never opens the device. A native library that will not load is also exempt: it leaves the plugin unable to probe, so the question has no answer and the old deferred reporting applies.

## 0.2.2-20260911

- Fix: enabling the plugin failed with "Failed requirement." on every real device. Cause: Kiosk Satellite's host caps a plugin's published light at 24 effects (`PluginBridge.kt`'s `publishLight`), and this plugin's 6 original + 19 ported effects totaled 25 — one over, with no error message attached to that check on the host side. Fixed by dropping "None" from the list published to Home Assistant's effect dropdown (24 entries); the host already accepts "None" as a light-state value even when it's absent from the declared list, so sending it (e.g. from this plugin's own on-device Settings screen) still works. The one real change: "None" (solid color, no animation) is no longer a pickable option in Home Assistant's effect dropdown specifically.

## 0.2.1-20260911

- Correction: an earlier attempt at this release used a 4-component date-based version (`2026.09.11.01`), which Kiosk Satellite's plugin manifest validator rejects (`FormatException: Invalid plugin ID or version`) — it requires 3-component semver, optionally with a `-suffix`. That broken release has been removed; this one embeds the date as a semver prerelease suffix instead.
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
