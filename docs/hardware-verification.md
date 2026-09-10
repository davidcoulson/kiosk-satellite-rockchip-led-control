# Hardware verification

The initial implementation is ready for testing on the panels described in issue #494. A non-Rockchip test kiosk can verify the UI and simulation but cannot establish physical compatibility.

## Protocol

The driver is opened with `O_RDWR | O_CLOEXEC | O_NOCTTY`. The RGB requests are `0xa1`, `0xa2` and `0xa3`. Each request receives the channel value as a scalar integer, not a pointer. Request `0x99` with argument zero turns output off. The plugin maps RGB and brightness into the configured maximum channel drive before making these calls.

These details come from David Coulson's documented panel implementation at commit `a6f3dfcc9cb8419e0c81b12ae02ab26ed036f29f` and the protocol research credited there. The plugin uses its own JNI implementation. A failed RGB write triggers a best-effort all-off request and reports the error.

## Verify a panel

1. Record the model, Android version, firmware build and whether `/dev/ledjni` exists. Do not change node permissions or SELinux policy as part of this test.
2. Install and enable the plugin with simulation off, root fallback off and maximum channel drive at 15.
3. Record the status message. Direct probing opens and closes the node without issuing color commands. Applying settings verifies whether writes work too.
4. Enable output with moderate brightness. Test red, green and blue individually. Record physical results rather than relying on the HA state.
5. Test brightness at several levels and run each effect. Check whether the panel needs a lower or higher maximum drive value. The default has not been calibrated for every panel.
6. Disable output, disable the plugin and re-enable it. Confirm no effect worker continues after disable and that the saved output choice behaves as expected.
7. If direct access is denied and the device is already rooted, enable root fallback and approve the root manager prompt. Check detection, colors and effects again. Denying or canceling the prompt should produce an actionable status without blocking KS settings.
8. Enable the Home Assistant light and test on/off, RGB, brightness and effects. Verify that commands update the plugin subpage and persisted settings.
9. Update the plugin while enabled. Confirm that its helper and animation stop, the replacement resumes and settings are retained.
10. Uninstall the plugin and confirm the LED turns off and the entity stops responding.

## Root helper

The optional helper runs a fixed Java entry point through `su` and Android's `app_process`, loading the plugin's verified JNI library. It communicates through stdin and stdout with only probe, RGB, off and quit requests. It does not accept arbitrary shell commands or configurable executable paths.

This avoids executing a downloaded standalone binary directly from writable app storage. Root-manager behavior and SELinux policy still vary by firmware. The helper is a compatibility path that requires physical validation, not a guarantee that root bypasses every policy.

## Report results

Include model and firmware, direct/root status, exact errors, maximum drive value, colors observed, tested effects and whether Home Assistant state changes matched the physical LED. Include a short video if color ordering or effect timing is unexpected. Do not include authentication tokens or root-manager credentials.
