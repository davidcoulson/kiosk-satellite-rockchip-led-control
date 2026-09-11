// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.rockchip;

import java.util.Random;
import static me.jxl.kiosk.plugins.rockchip.LedEffectMath.*;

/**
 * Effects with a genuine per-tick random-event chance and per-tick decay in
 * the Dart source (as opposed to StatefulEffects.java's absolute-ms state
 * machines, which are tick-rate safe as-is). Each tracks real elapsed time
 * between calls (like PacificaEffect already does) and uses
 * LedEffectMath.scaledChance/scaledDecay so the average event rate and
 * decay half-life match the original regardless of this plugin's fixed
 * 60ms tick, rather than the effect literally running 2-3x slower/faster
 * than intended.
 */
final class DiscoSparkleEffect implements LedRichEffect {
    private static final double NATIVE_INTERVAL_S = 0.03; // 30ms in the Dart source
    private static final double BASE_BRIGHTNESS = 0.25;
    private static final double FLASH_CHANCE = 0.008; // 8/1000 per native tick
    private final Random rnd = new Random();

    private double flash = 0.0;
    private Long lastMs = null;

    public int[] tick(long nowMs, int[] base) {
        if (lastMs == null) lastMs = nowMs;
        double dt = (nowMs - lastMs) / 1000.0;
        lastMs = nowMs;

        double t = nowMs / 1000.0;
        double hue = (t * 0.05) % 1.0;
        double[] baseRgb = hsvToRgb(hue, 0.8, BASE_BRIGHTNESS);
        flash *= scaledDecay(0.75, NATIVE_INTERVAL_S, dt);
        if (rnd.nextDouble() < scaledChance(FLASH_CHANCE, NATIVE_INTERVAL_S, dt)) flash = 1.0;
        double r = baseRgb[0] * (1 - flash) + flash;
        double g = baseRgb[1] * (1 - flash) + flash;
        double b = baseRgb[2] * (1 - flash) + flash;
        return new int[]{r255(r * 255), r255(g * 255), r255(b * 255)};
    }
}

/** Dim warm ambient shimmer, interrupted by irregular bursts of 1..3 bright
 *  cool-white sub-flashes ("strikes") — a storm at a distance. */
final class LightningStormEffect implements LedRichEffect {
    private static final double NATIVE_INTERVAL_S = 0.02; // 20ms in the Dart source
    private static final double STRIKE_CHANCE = 0.002; // 4/2000 per native tick
    private final Random rnd = new Random();

    private double flash = 0.0;
    private int pending = 0;
    private long nextMs = 0;
    private Long lastMs = null;

    public int[] tick(long nowMs, int[] base) {
        if (lastMs == null) lastMs = nowMs;
        double dt = (nowMs - lastMs) / 1000.0;
        lastMs = nowMs;

        double t = nowMs / 1000.0;
        double amb = 0.02 + 0.03 * (noise1d(t * 0.5) * 0.5 + 0.5);

        flash *= scaledDecay(0.72, NATIVE_INTERVAL_S, dt);
        if (pending == 0 && rnd.nextDouble() < scaledChance(STRIKE_CHANCE, NATIVE_INTERVAL_S, dt)) {
            pending = 1 + rnd.nextInt(3);
            nextMs = nowMs;
        }
        if (pending > 0 && nowMs >= nextMs) {
            flash = 0.85 + rnd.nextInt(150) / 1000.0;
            pending--;
            nextMs = nowMs + 50 + rnd.nextInt(100);
        }
        if (flash > 1.0) flash = 1.0;

        return mixWarmCool(amb, flash);
    }
}

/** Ported from white_effects.yaml's "Candle Flicker (Warm White)": exact
 *  brightness curve (noise + occasional decaying "gust" dips, floored so
 *  the candle never fully goes dark) applied to a fixed warm-candle tint
 *  instead of the source's bare white channel. */
final class CandleFlickerEffect implements LedRichEffect {
    private static final double NATIVE_INTERVAL_S = 0.05; // 50ms in the Dart source
    private static final int WARM_R = 255, WARM_G = 147, WARM_B = 41; // ~2000K candle tint
    private static final double GUST_CHANCE = 0.005; // 5/1000 per native tick
    private final Random rnd = new Random();

    private double gust = 0.0;
    private Long lastMs = null;

    public int[] tick(long nowMs, int[] base) {
        if (lastMs == null) lastMs = nowMs;
        double dt = (nowMs - lastMs) / 1000.0;
        lastMs = nowMs;

        double t = nowMs / 1000.0;
        double baseFlame = noise1d(t * 3.0) * 0.5 + 0.5;
        double micro = noise1d(t * 11.3 + 2.0) * 0.5 + 0.5;
        double v = baseFlame * 0.70 + micro * 0.30;

        gust *= scaledDecay(0.90, NATIVE_INTERVAL_S, dt);
        if (rnd.nextDouble() < scaledChance(GUST_CHANCE, NATIVE_INTERVAL_S, dt)) {
            gust += 0.30 + rnd.nextInt(300) / 1000.0;
            if (gust > 0.65) gust = 0.65;
        }
        v -= gust;
        if (v < 0.12) v = 0.12; // candle never fully goes dark
        if (v > 1.0) v = 1.0;

        return new int[]{r255(WARM_R * v), r255(WARM_G * v), r255(WARM_B * v)};
    }
}
