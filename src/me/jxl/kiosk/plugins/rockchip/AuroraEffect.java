// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.rockchip;

import static me.jxl.kiosk.plugins.rockchip.LedEffectMath.*;

/**
 * Shared structure behind all three Aurora variants: one drifting palette
 * scan plus a slow breathing scale, tuned per-variant below. Pure function
 * of elapsed time, no random state — safe at any tick rate.
 */
abstract class AuroraEffect implements LedRichEffect {
    abstract int[][] pal();
    abstract double hueSpeed();
    abstract double breathePeriodHz(); // multiplies t inside sin(t * this)
    abstract double breathePow();
    abstract double scaleMin();
    abstract double scaleRange();

    public int[] tick(long elapsedMs, int[] base) {
        double t = elapsedMs / 1000.0;
        double huePos = (t * hueSpeed()) % 16.0;
        if (huePos < 0) huePos += 16.0;
        double breathing = Math.sin(t * breathePeriodHz()) * 0.5 + 0.5;
        breathing = Math.pow(breathing, breathePow());
        double scale = scaleMin() + scaleRange() * breathing;
        double[] rgb = lerpPalette16(pal(), huePos);
        return new int[]{r255(rgb[0] * scale), r255(rgb[1] * scale), r255(rgb[2] * scale)};
    }
}

final class AuroraSolarStormEffect extends AuroraEffect {
    int[][] pal() { return new int[][]{
        {0, 40, 10}, {0, 70, 20}, {10, 100, 30}, {20, 140, 40},
        {40, 180, 60}, {60, 210, 90}, {50, 200, 130}, {40, 180, 170},
        {30, 150, 200}, {40, 120, 210}, {70, 90, 210}, {110, 70, 200},
        {150, 60, 190}, {190, 60, 170}, {210, 70, 140}, {180, 90, 120},
    }; }
    double hueSpeed() { return 0.55; }
    double breathePeriodHz() { return 0.35; }
    double breathePow() { return 1.1; }
    double scaleMin() { return 0.35; }
    double scaleRange() { return 0.65; }
}

final class AuroraPastelDreamEffect extends AuroraEffect {
    int[][] pal() { return new int[][]{
        {140, 190, 155}, {140, 205, 165}, {145, 215, 170}, {150, 225, 175},
        {160, 235, 185}, {170, 245, 200}, {165, 240, 215}, {160, 230, 225},
        {155, 215, 235}, {160, 200, 235}, {175, 190, 235}, {195, 180, 230},
        {210, 175, 225}, {225, 175, 220}, {235, 180, 210}, {220, 185, 200},
    }; }
    double hueSpeed() { return 0.06; }
    double breathePeriodHz() { return 0.06; }
    double breathePow() { return 2.0; }
    double scaleMin() { return 0.45; }
    double scaleRange() { return 0.35; }
}

final class AuroraRedSkyEffect extends AuroraEffect {
    int[][] pal() { return new int[][]{
        {20, 0, 5}, {35, 0, 8}, {55, 0, 12}, {80, 2, 18},
        {105, 5, 25}, {130, 10, 35}, {150, 20, 50}, {170, 35, 68},
        {185, 55, 90}, {195, 80, 115}, {200, 105, 140}, {205, 130, 160},
        {210, 150, 175}, {215, 170, 190}, {220, 190, 205}, {225, 205, 215},
    }; }
    double hueSpeed() { return 0.15; }
    double breathePeriodHz() { return 0.12; }
    double breathePow() { return 1.3; }
    double scaleMin() { return 0.15; }
    double scaleRange() { return 0.85; }
}
