// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.rockchip;

/**
 * Shared color math for the rich effect set below, ported 1:1 from
 * davidcoulson/kiosk-satellite's led_effects.dart (feature/rgb-led-support
 * branch), which itself ports common/light_effects_helpers.h from the
 * accent_light_nonaddressable_effects.yaml / white_effects.yaml ESPHome
 * reference configs these effects were designed against.
 */
final class LedEffectMath {
    private LedEffectMath() {}

    static int r255(double v) {
        long r = Math.round(v);
        if (r < 0) return 0;
        if (r > 255) return 255;
        return (int) r;
    }

    /** HSV (0..1 each) to RGB (0..1 each). */
    static double[] hsvToRgb(double h, double s, double v) {
        h = h % 1.0;
        if (h < 0) h += 1.0;
        int i = (int) Math.floor(h * 6.0);
        double f = h * 6.0 - i;
        double p = v * (1.0 - s);
        double q = v * (1.0 - f * s);
        double w = v * (1.0 - (1.0 - f) * s);
        switch (i) {
            case 0: return new double[]{v, w, p};
            case 1: return new double[]{q, v, p};
            case 2: return new double[]{p, v, w};
            case 3: return new double[]{p, q, v};
            case 4: return new double[]{w, p, v};
            default: return new double[]{v, p, q};
        }
    }

    /** Fractional lookup (0..16, wrapping) into a 16-entry RGB palette,
     *  linearly interpolating the two nearest entries. 0..255 each, not
     *  clamped or rounded (callers do that via r255). */
    static double[] lerpPalette16(int[][] pal, double pos) {
        int idx0 = (int) Math.floor(pos);
        int idx1 = (idx0 + 1) % 16;
        double frac = pos - idx0;
        return new double[]{
            pal[idx0][0] * (1 - frac) + pal[idx1][0] * frac,
            pal[idx0][1] * (1 - frac) + pal[idx1][1] * frac,
            pal[idx0][2] * (1 - frac) + pal[idx1][2] * frac,
        };
    }

    // ~2000K candle-warm and ~6500K cool-white tints standing in for the
    // real warm/cold-white channels the white_effects.yaml source effects
    // drive, which this hardware doesn't have (see the ported effects
    // below): a fixed-tint RGB mix proportional to each effect's own
    // ww/cw levels, same brightness math as the source.
    static final int[] WARM_TINT = {255, 147, 41};
    static final int[] COOL_TINT = {200, 220, 255};

    static int[] mixWarmCool(double ww, double cw) {
        return new int[]{
            r255(WARM_TINT[0] * ww + COOL_TINT[0] * cw),
            r255(WARM_TINT[1] * ww + COOL_TINT[1] * cw),
            r255(WARM_TINT[2] * ww + COOL_TINT[2] * cw),
        };
    }

    /** Four-octave sine noise shared by RollingFog, LightningStorm and
     *  CandleFlicker's flame/gust math. */
    static double noise1d(double x) {
        return Math.sin(x * 1.0) * 0.500
            + Math.sin(x * 2.13 + 1.7) * 0.250
            + Math.sin(x * 4.07 + 3.1) * 0.125
            + Math.sin(x * 8.53 + 0.6) * 0.0625;
    }

    /**
     * The source effects (LightningStorm, DiscoSparkle, CandleFlicker,
     * Pacifica's foam) apply a per-tick decay multiplier and a per-tick
     * random-event chance, tuned for the Dart source's own effect-specific
     * tick interval (20-100ms). This plugin ticks every effect on one
     * fixed 60ms loop instead, so a literal port would run those decays
     * and event chances at the wrong real-world rate. These two convert a
     * "per nativeIntervalS-tick" constant into the equivalent applied over
     * an arbitrary elapsed [dt] seconds, so the real-time behavior (decay
     * half-life, average events per second) matches the original
     * regardless of how often tick() actually gets called.
     */
    static double scaledDecay(double decayPerNativeTick, double nativeIntervalS, double dt) {
        if (dt <= 0.0) return 1.0;
        return Math.pow(decayPerNativeTick, dt / nativeIntervalS);
    }

    /** Equivalent per-[dt] trigger probability (0..1) for an event whose
     *  chance was [chancePerNativeTick] (0..1) once every
     *  [nativeIntervalS] seconds in the Dart source. */
    static double scaledChance(double chancePerNativeTick, double nativeIntervalS, double dt) {
        if (dt <= 0.0) return 0.0;
        return 1.0 - Math.pow(1.0 - chancePerNativeTick, dt / nativeIntervalS);
    }
}
