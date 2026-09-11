// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.rockchip;

/**
 * One of the 19 effects ported from davidcoulson/kiosk-satellite's
 * led_effects.dart — distinct from this plugin's own original five
 * (None/Pulse/Blink/Rainbow/Candle/Random, see LedMath.frame), which stay
 * as pure stateless functions of elapsed time. These need real per-frame
 * state (running RNGs, decaying flash/gust levels, drifting wave phases),
 * so each is its own small object: a fresh instance is created every time
 * the effect is (re)selected, exactly as LedManager.dart does, so
 * restarting an effect always starts from clean state.
 */
interface LedRichEffect {
    /** [elapsedMs] is time since this effect instance started (not device
     *  uptime) — every effect below only ever uses *differences* in time,
     *  matching the millis() semantics of the original ESPHome lambdas.
     *  [base] is the light's last real (non-effect) color, 0..255 each. */
    int[] tick(long elapsedMs, int[] base);
}
