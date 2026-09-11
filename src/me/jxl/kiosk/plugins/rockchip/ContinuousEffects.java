// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.rockchip;

import static me.jxl.kiosk.plugins.rockchip.LedEffectMath.*;

/**
 * Effects that are pure functions of elapsed time with no internal random
 * state — safe to tick at any rate (this plugin's fixed 60ms loop, or
 * anything else) with no rescaling, unlike the probability-driven ones in
 * FlickerEffects.java and PacificaEffect.java.
 */
final class BeaconPulseEffect implements LedRichEffect {
    private static final int AMB_R = 2, AMB_G = 2, AMB_B = 6;
    private static final int BEAM_R = 255, BEAM_G = 220, BEAM_B = 140;
    private static final double PERIOD = 3.0, FLASH_WIDTH = 0.15;

    public int[] tick(long elapsedMs, int[] base) {
        double t = elapsedMs / 1000.0;
        double phase = (t % PERIOD) / PERIOD;
        double d = phase > 0.5 ? 1.0 - phase : phase;
        double flash = Math.exp(-(d * d) / (2.0 * FLASH_WIDTH * FLASH_WIDTH));
        return new int[]{
            r255(AMB_R + (BEAM_R - AMB_R) * flash),
            r255(AMB_G + (BEAM_G - AMB_G) * flash),
            r255(AMB_B + (BEAM_B - AMB_B) * flash),
        };
    }
}

final class HeartbeatPulseEffect implements LedRichEffect {
    private static final double PERIOD = 1.0; // 60 BPM
    private static final double LUB_CENTER = 0.08, LUB_WIDTH = 0.05, LUB_STRENGTH = 1.0;
    private static final double DUB_CENTER = 0.22, DUB_WIDTH = 0.04, DUB_STRENGTH = 0.6;
    private static final double MIN_LEVEL = 0.03;
    private static final int HEART_R = 200, HEART_G = 10, HEART_B = 30;

    public int[] tick(long elapsedMs, int[] base) {
        double t = elapsedMs / 1000.0;
        double phase = (t % PERIOD) / PERIOD;
        if (phase < 0) phase += 1.0;
        double dl = phase - LUB_CENTER;
        double lub = Math.exp(-(dl * dl) / (2.0 * LUB_WIDTH * LUB_WIDTH)) * LUB_STRENGTH;
        double dd = phase - DUB_CENTER;
        double dub = Math.exp(-(dd * dd) / (2.0 * DUB_WIDTH * DUB_WIDTH)) * DUB_STRENGTH;
        double intensity = Math.min(lub + dub, 1.0);
        double level = MIN_LEVEL + (1.0 - MIN_LEVEL) * intensity;
        return new int[]{r255(HEART_R * level), r255(HEART_G * level), r255(HEART_B * level)};
    }
}

final class SoftGlowEffect implements LedRichEffect {
    private static final double BREATHE_PERIOD = 8.0, MIN_LEVEL = 0.55, EASE_CURVE = 1.5;

    public int[] tick(long elapsedMs, int[] base) {
        double t = elapsedMs / 1000.0;
        double breathe = Math.sin((t / BREATHE_PERIOD) * 6.2831853) * 0.5 + 0.5;
        breathe = Math.pow(breathe, EASE_CURVE);
        double level = MIN_LEVEL + (1.0 - MIN_LEVEL) * breathe;
        return new int[]{r255(base[0] * level), r255(base[1] * level), r255(base[2] * level)};
    }
}

final class RollingFogEffect implements LedRichEffect {
    private static final int[][] PAL = {
        {10, 12, 14}, {20, 22, 25}, {35, 37, 40}, {50, 53, 58},
        {65, 68, 74}, {80, 84, 92}, {95, 100, 108}, {110, 115, 124},
        {125, 130, 140}, {140, 146, 156}, {158, 164, 174}, {176, 182, 192},
        {195, 200, 208}, {212, 216, 222}, {228, 231, 236}, {245, 247, 250},
    };

    public int[] tick(long elapsedMs, int[] base) {
        double t = elapsedMs / 1000.0;
        double thickness = 0.35 + 0.65 * (Math.sin(t * 0.03) * 0.5 + 0.5);
        double contrast = 1.6;
        double combined = noise1d(t * 1.3) * 0.55 + noise1d(-t * 0.9 + 5.2) * 0.30 + noise1d(t * 1.8 + 2.7) * 0.15;
        double v = combined * 0.5 + 0.5;
        v *= thickness;
        v = clamp01(0.5 + (v - 0.5) * contrast);
        double[] rgb = lerpPalette16(PAL, v * 15.0);
        return new int[]{r255(rgb[0]), r255(rgb[1]), r255(rgb[2])};
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}

/** Slow multi-keyframe warm/cool day cycle — warm dominates dawn/dusk,
 *  cool dominates midday. 300s per cycle (the source's own default). */
final class SunriseSunsetEffect implements LedRichEffect {
    private static final double TOTAL_CYCLE_SECONDS = 300.0;
    // {phase 0..1, warm 0..1, cool 0..1}
    private static final double[][] KP = {
        {0.00, 0.00, 0.00}, // deep night
        {0.18, 0.05, 0.00}, // pre-dawn twilight
        {0.25, 0.35, 0.02}, // first light
        {0.32, 0.55, 0.05}, // sunrise glow, warm-dominant
        {0.40, 0.55, 0.30}, // morning
        {0.50, 0.35, 0.75}, // midday, cool-dominant daylight
        {0.62, 0.55, 0.30}, // afternoon
        {0.72, 0.60, 0.05}, // sunset glow, warm-dominant
        {0.80, 0.30, 0.00}, // dusk
        {0.88, 0.05, 0.00}, // late twilight
        {1.00, 0.00, 0.00}, // back to night (wraps to phase 0)
    };

    public int[] tick(long elapsedMs, int[] base) {
        double t = elapsedMs / 1000.0;
        double phase = (t % TOTAL_CYCLE_SECONDS) / TOTAL_CYCLE_SECONDS;
        if (phase < 0.0) phase += 1.0;
        double ww = KP[0][1], cw = KP[0][2];
        for (int k = 0; k < KP.length - 1; k++) {
            if (phase >= KP[k][0] && phase <= KP[k + 1][0]) {
                double span = KP[k + 1][0] - KP[k][0];
                double frac = span > 0.0 ? (phase - KP[k][0]) / span : 0.0;
                ww = KP[k][1] * (1 - frac) + KP[k + 1][1] * frac;
                cw = KP[k][2] * (1 - frac) + KP[k + 1][2] * frac;
                break;
            }
        }
        return mixWarmCool(ww, cw);
    }
}

/** Slow, dim cool-tint breathing — a calmer counterpart to SoftGlow, which
 *  breathes the current base color instead of a fixed tint. */
final class MoonlightGlowEffect implements LedRichEffect {
    private static final double BREATHE_PERIOD = 10.0, MIN_LEVEL = 0.15, MAX_LEVEL = 0.50, EASE_CURVE = 1.5;

    public int[] tick(long elapsedMs, int[] base) {
        double t = elapsedMs / 1000.0;
        double breathe = Math.sin((t / BREATHE_PERIOD) * 6.2831853) * 0.5 + 0.5;
        breathe = Math.pow(breathe, EASE_CURVE);
        double level = MIN_LEVEL + (MAX_LEVEL - MIN_LEVEL) * breathe;
        return mixWarmCool(0.0, level);
    }
}

/** One-shot 10-minute ramp from off to full brightness — warm rises first,
 *  then cool rises while warm recedes slightly — then holds at the final
 *  level once the ramp completes, rather than looping. */
final class WakeUpAlarmEffect implements LedRichEffect {
    private static final double DURATION_S = 600.0;

    public int[] tick(long elapsedMs, int[] base) {
        double t = (elapsedMs / 1000.0) / DURATION_S;
        if (t > 1.0) t = 1.0;
        double ww, cw;
        if (t < 0.5) {
            double local = t / 0.5;
            ww = local;
            cw = 0.0;
        } else {
            double local = (t - 0.5) / 0.5;
            ww = 1.0 - local * 0.4;
            cw = local;
        }
        return mixWarmCool(ww, cw);
    }
}
