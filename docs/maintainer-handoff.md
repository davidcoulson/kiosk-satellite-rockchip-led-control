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
5. Create a stable GitHub release with three assets from `dist/`: `kiosk-satellite-plugin.json`, `rockchip-led-control-VERSION.zip` and `rockchip-led-control-VERSION.zip.sha256`.
6. Test installation through the repository URL on a KS build that supports SDK 2.

KS reads the README from the release tag. Editing the default branch does not change the README attached to an already installed release. The workflows build and test the package but do not publish releases automatically.

## SDK maintenance

The vendored interfaces under `sdk/` are compile-time dependencies licensed under Apache-2.0. Compare them with the host interfaces when updating SDK support. SDK 2 adds native files, runtime status, persistent settings updates and RGB light publication while retaining SDK 1 compatibility in the host.

## Initial scope

This plugin includes six independently implemented effects. It does not reproduce all 23 effects from the original fork. The maintainer can add effects without changing the host application. More device nodes, vendor-specific color calibration and additional hardware controls also belong in the plugin repository.
