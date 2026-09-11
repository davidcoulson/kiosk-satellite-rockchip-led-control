# Maintainer handoff

The plugin is self-contained. Device-specific source, JNI code, root helper, effects, tests and build tooling live in this repository. Kiosk Satellite contains only the reusable SDK support and generic RGB entity integration.

## Ownership

Keep `rockchip-led-control` as the plugin ID. Change the manifest author to the new maintainer's preferred attribution and update repository links after ownership is agreed. Do not claim that the original issue author has reviewed or validated this plugin until they have done so.

A GitHub repository transfer preserves the repository history and redirect. KS records the repository URL used for installation to prevent another repository from replacing the same plugin ID. Until repository-source migration is supported, users moving to a different URL should uninstall and reinstall through the new URL. Uninstalling deletes saved settings, so record them first.

## Release

1. Validate the hardware matrix in `hardware-verification.md`.
2. Update `version` in `kiosk-satellite-plugin.json` and document changes in `CHANGELOG.md`.
3. Run `python3 tools/test.py` and `python3 tools/build.py`.
4. Commit the tested source, manifest and documentation and create a matching version tag.
5. Publish a stable GitHub release for that tag. The Release workflow tests and builds the tagged source and attaches `kiosk-satellite-plugin.json`, `rockchip-led-control-VERSION.zip` and `rockchip-led-control-VERSION.zip.sha256`. Wait for it to finish successfully.
6. Test installation through the repository URL on a KS build that supports SDK 1.

KS reads the README from the release tag. Editing the default branch does not change the README attached to an already installed release. Publishing a release triggers the workflow. Commits and pull requests do not trigger builds. The tag must match the manifest version, with an optional `v` prefix.

The workflow builds against Android 35 explicitly. To reproduce that platform selection locally, run `python3 tools/build.py --android-platform 35`. Newer platforms preinstalled on a runner do not change the release build.

To retry manually, run `gh workflow run build.yml --ref vVERSION` on the existing release tag. The release must already exist. Manual runs on branches are rejected. The workflow attaches assets to the release but does not create or publish the release itself. Use the workflow token for uploads. KS rejects manually attached assets and verifies the package against GitHub's digest. Direct ZIP installation is reserved for developer testing.

A rerun uses the source from the original release tag. After fixing build code, publish a new version whose tag includes the fix. Re-running a failed older tag does not pick up changes from the default branch.

## SDK maintenance

The vendored interfaces under `sdk/` are compile-time dependencies licensed under Apache-2.0. Compare them with the host interfaces when updating SDK support. SDK 1 includes native files, runtime status, plugin settings, RGB lights, KS observations and transient controls. All examples and plugins use this first public API version.

## Initial scope

This plugin includes its own six independently implemented effects plus 19 ported from the original fork's `led_effects.dart` (0.2.0) — 25 total, more than the fork's own 23, since the fork's four generic effects (Pulse/Strobe/Random/Flicker) differ from this plugin's own six. The maintainer can add effects without changing the host application. More device nodes, vendor-specific color calibration and additional hardware controls also belong in the plugin repository.
