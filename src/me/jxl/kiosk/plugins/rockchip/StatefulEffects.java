// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.rockchip;

import java.util.Random;
import static me.jxl.kiosk.plugins.rockchip.LedEffectMath.*;

/**
 * State-machine effects whose transitions are gated by absolute-millisecond
 * thresholds compared against elapsedMs, rather than by a per-tick
 * probability — safe to tick at any rate (including this plugin's fixed
 * 60ms loop, coarser than these effects' original 20-40ms interval in the
 * Dart source) with no rescaling: the same real-time durations either way,
 * just a slightly coarser time resolution.
 */
final class FairytwinkleEffect implements LedRichEffect {
    private static final int[][] PAL = {
        {255, 214, 140}, {255, 200, 150}, {255, 190, 170}, {255, 180, 195},
        {250, 175, 215}, {240, 175, 230}, {225, 180, 240}, {205, 185, 245},
        {185, 195, 250}, {165, 205, 250}, {150, 215, 250}, {140, 225, 245},
        {150, 230, 235}, {170, 235, 225}, {200, 240, 215}, {230, 245, 210},
    };
    private static final int FADE_IN_MS = 800, FADE_OUT_MS = 1200;
    private static final int MIN_HOLD_MS = 1500, MAX_HOLD_MS = 4000;
    private static final int MIN_PAUSE_MS = 1000, MAX_PAUSE_MS = 3000;
    private final Random rnd = new Random();

    private int state = 0;
    private long stateStart = 0;
    private long nextMs = 0;
    private long holdMs = 0;
    private double hue = 0.0;

    public int[] tick(long nowMs, int[] base) {
        double brightness = 0.0;
        if (state == 0) {
            if (nowMs >= nextMs) {
                hue = rnd.nextInt(1000) / 1000.0;
                state = 1;
                stateStart = nowMs;
                holdMs = MIN_HOLD_MS + rnd.nextInt(MAX_HOLD_MS - MIN_HOLD_MS);
            }
        } else if (state == 1) {
            brightness = (nowMs - stateStart) / (double) FADE_IN_MS;
            if (brightness >= 1.0) {
                brightness = 1.0;
                state = 2;
                stateStart = nowMs;
            }
        } else if (state == 2) {
            brightness = 1.0;
            if (nowMs - stateStart >= holdMs) {
                state = 3;
                stateStart = nowMs;
            }
        } else if (state == 3) {
            brightness = 1.0 - (nowMs - stateStart) / (double) FADE_OUT_MS;
            if (brightness <= 0.0) {
                brightness = 0.0;
                state = 0;
                nextMs = nowMs + MIN_PAUSE_MS + rnd.nextInt(MAX_PAUSE_MS - MIN_PAUSE_MS);
            }
        }
        double[] rgb = lerpPalette16(PAL, hue * 15.0);
        return new int[]{r255(rgb[0] * brightness), r255(rgb[1] * brightness), r255(rgb[2] * brightness)};
    }
}

final class FireworksBurstEffect implements LedRichEffect {
    private static final int LAUNCH_MS = 600, DECAY_MS = 900;
    private static final int MIN_PAUSE_MS = 1500, MAX_PAUSE_MS = 4000;
    private final Random rnd = new Random();

    private int state = 0;
    private long stateStart = 0;
    private long nextMs = 0;
    private double hue = 0.0;

    public int[] tick(long nowMs, int[] base) {
        double r = 0, g = 0, b = 0;
        if (state == 0) {
            if (nowMs >= nextMs) {
                state = 1;
                stateStart = nowMs;
                hue = rnd.nextInt(1000) / 1000.0;
            }
        } else if (state == 1) {
            double t = (nowMs - stateStart) / (double) LAUNCH_MS;
            if (t >= 1.0) {
                state = 2;
                stateStart = nowMs;
            } else {
                double level = t * 0.4;
                r = g = b = level * 255.0;
            }
        } else if (state == 2) {
            double t = (nowMs - stateStart) / (double) DECAY_MS;
            if (t >= 1.0) {
                state = 0;
                nextMs = nowMs + MIN_PAUSE_MS + rnd.nextInt(MAX_PAUSE_MS - MIN_PAUSE_MS);
            } else {
                double decay = Math.pow(1.0 - t, 2.0);
                double[] rgb = hsvToRgb(hue, 1.0, decay);
                r = rgb[0] * 255.0;
                g = rgb[1] * 255.0;
                b = rgb[2] * 255.0;
            }
        }
        return new int[]{r255(r), r255(g), r255(b)};
    }
}

final class BubblesEffect implements LedRichEffect {
    private static final int PAUSE_MIN = 500, PAUSE_MAX = 2000;
    private static final int LIFE_MIN = 2000, LIFE_MAX = 5000;
    private static final double POP_DECAY_RATE = 3.0;
    private static final double MAX_BRIGHTNESS = 0.5;
    private final Random rnd = new Random();

    private int state = 0;
    private long stateStart = 0;
    private double hue = 0.5;
    private double popFlash = 0.0;
    private long pauseDuration = 0;
    private long lifeDuration = 0;
    private Long lastMs = null;

    public int[] tick(long nowMs, int[] base) {
        if (lastMs == null) lastMs = nowMs;
        double dt = (nowMs - lastMs) / 1000.0;
        lastMs = nowMs;
        if (dt > 0.2) dt = 0.02;

        if (state == 0) {
            if (nowMs - stateStart >= pauseDuration) {
                state = 1;
                stateStart = nowMs;
                hue = 0.5 + rnd.nextInt(250) / 1000.0;
                lifeDuration = LIFE_MIN + rnd.nextInt(LIFE_MAX - LIFE_MIN);
            }
        } else if (state == 1) {
            if (nowMs - stateStart >= lifeDuration) {
                state = 2;
                stateStart = nowMs;
                popFlash = 1.0;
            }
        } else if (state == 2) {
            popFlash -= dt * POP_DECAY_RATE;
            if (popFlash <= 0.0) {
                popFlash = 0.0;
                state = 0;
                stateStart = nowMs;
                pauseDuration = PAUSE_MIN + rnd.nextInt(PAUSE_MAX - PAUSE_MIN);
            }
        }

        double r = 0.0, g = 0.0, b = 0.0;
        if (state == 1) {
            double[] rgb = hsvToRgb(hue, 0.35, MAX_BRIGHTNESS);
            r = rgb[0]; g = rgb[1]; b = rgb[2];
        } else if (state == 2) {
            double[] rgb = hsvToRgb(hue, 0.15, popFlash);
            r = rgb[0]; g = rgb[1]; b = rgb[2];
        }
        return new int[]{r255(r * 255), r255(g * 255), r255(b * 255)};
    }
}
