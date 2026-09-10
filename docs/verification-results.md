# Initial verification results

These results cover the initial 0.1.0 implementation. They do not claim physical Rockchip panel validation.

| Area | Result |
| --- | --- |
| Standalone Java tests | Passed channel scaling, effect bounds, simulated HA commands, settings persistence and thread cleanup |
| Native protocol tests | Passed device path, read/write open mode, scalar ioctl arguments, RGB ordering, off command and error cleanup using fake system calls |
| Native builds | Built arm64-v8a, armeabi-v7a and x86_64 libraries and packaged the SDK 2 ZIP |
| KS package and entity tests | Passed SDK 1 compatibility, SDK 2 validation and RGB state checks |
| Home Assistant client | Passed real aioesphomeapi 46.4.0 list/state/command exchanges against the KS server, including RGB mode and effects |
| Native app UI | Verified grouped settings, color picker, sliders, dropdowns and runtime status on the test kiosk |
| Remote Admin UI | Verified saved colors, brightness and effects at desktop and phone widths |
| Unsupported hardware | Test kiosk has no `/dev/ledjni`. Plugin reported that limitation and kept settings accessible |
| Simulation | Published an RGB entity, accepted a simulated HA command and synchronized saved plugin values |
| Lifecycle | Verified native-library loading across updates and repeated enable/disable cycles in the same app process |
| Physical RGB output | Pending verification on OP's compatible panel |
| Root helper on panel firmware | Pending verification with that panel's root manager and SELinux policy |

The test kiosk was left with the plugin installed but disabled. Simulation and root fallback were turned off and the plugin settings were restored to their defaults.
