// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.rockchip;

import java.util.Random;
import static me.jxl.kiosk.plugins.rockchip.LedEffectMath.*;

/**
 * Shared structure behind all three Pacifica variants — four drifting
 * palette waves plus a random foam sparkle, tuned per-variant by the
 * concrete subclasses below. The wave motion already integrates real
 * elapsed time (dt) rather than assuming a fixed tick rate, so it needs no
 * rescaling for this plugin's 60ms loop; only the foam decay/spawn chance
 * (a per-100ms-tick constant in the Dart source) uses
 * LedEffectMath.scaledDecay/scaledChance to keep the same real-time
 * sparkle rate regardless of tick rate.
 */
abstract class PacificaEffect implements LedRichEffect {
    private static final double NATIVE_INTERVAL_S = 0.1; // 100ms in the Dart source
    private final Random rnd = new Random();

    abstract int[][] palDeep();
    abstract int[][] palMid();
    abstract int[][] palBright();
    abstract double[] speedBase(); // [s1, s2, s3, s4]
    abstract double[] speedAmp();
    abstract double[] speedPeriodMs();
    abstract double w1Mult();
    abstract double w2Mult();
    abstract double w3Pow();
    abstract double w3Mult();
    abstract double w4Pow();
    abstract double w4Mult();
    abstract double foamDecay();
    abstract double foamChance(); // 0..1, per NATIVE_INTERVAL_S
    abstract int foamAddMin();
    abstract int foamAddRange();
    abstract double foamMax();

    private double t1 = 0, t2 = 0, t3 = 0, t4 = 0, foam = 0;
    private Long lastMs = null;

    public int[] tick(long nowMs, int[] base) {
        if (lastMs == null) lastMs = nowMs;
        double dt = (nowMs - lastMs) / 1000.0;
        lastMs = nowMs;
        if (dt > 0.5) dt = 0.1;

        double[] sBase = speedBase(), sAmp = speedAmp(), sPeriod = speedPeriodMs();
        double speed1 = sBase[0] + sAmp[0] * Math.sin(nowMs / sPeriod[0]);
        double speed2 = sBase[1] + sAmp[1] * Math.sin(nowMs / sPeriod[1] + 1.0);
        double speed3 = sBase[2] + sAmp[2] * Math.sin(nowMs / sPeriod[2] + 2.0);
        double speed4 = sBase[3] + sAmp[3] * Math.sin(nowMs / sPeriod[3] + 3.0);
        t1 += dt * speed1;
        t2 += dt * speed2;
        t3 += dt * speed3;
        t4 += dt * speed4;

        double wave1 = Math.sin(t1 * 0.6) * 0.5 + 0.5;
        double wave2 = Math.sin(-t2 * 0.9 + 1.7) * 0.5 + 0.5;
        double wave3 = Math.sin(t3 * 0.4 + 3.1) * 0.5 + 0.5;
        double wave4 = Math.sin(-t4 * 1.2 + 4.4) * 0.5 + 0.5;

        double r = 0, g = 0, b = 0;

        double pos = t1 * 0.8 % 16.0;
        if (pos < 0) pos += 16.0;
        double[] lp = lerpPalette16(palDeep(), pos);
        r += lp[0]; g += lp[1]; b += lp[2];

        pos = (wave1 * 16.0 + t2 * 0.3) % 16.0;
        if (pos < 0) pos += 16.0;
        lp = lerpPalette16(palMid(), pos);
        double w1 = wave1 * w1Mult();
        r += lp[0] * w1; g += lp[1] * w1; b += lp[2] * w1;

        pos = (wave2 * 16.0 + t3 * 0.4) % 16.0;
        if (pos < 0) pos += 16.0;
        lp = lerpPalette16(palMid(), pos);
        double w2 = wave2 * w2Mult();
        r += lp[0] * w2; g += lp[1] * w2; b += lp[2] * w2;

        pos = (wave3 * 16.0 + t4 * 0.5) % 16.0;
        if (pos < 0) pos += 16.0;
        lp = lerpPalette16(palBright(), pos);
        double w3 = Math.pow(wave3, w3Pow()) * w3Mult();
        r += lp[0] * w3; g += lp[1] * w3; b += lp[2] * w3;

        double w4 = Math.pow(wave4, w4Pow()) * w4Mult();
        int[][] bright = palBright();
        r += bright[15][0] * w4; g += bright[15][1] * w4; b += bright[15][2] * w4;

        foam *= scaledDecay(foamDecay(), NATIVE_INTERVAL_S, dt);
        if (rnd.nextDouble() < scaledChance(foamChance(), NATIVE_INTERVAL_S, dt)) {
            foam += foamAddMin() + rnd.nextInt(foamAddRange());
            if (foam > foamMax()) foam = foamMax();
        }
        r += foam; g += foam; b += foam;

        return new int[]{
            r255(clamp255(r)), r255(clamp255(g)), r255(clamp255(b)),
        };
    }

    private static double clamp255(double v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}

final class PacificaCalmLagoonEffect extends PacificaEffect {
    int[][] palDeep() { return new int[][]{
        {5, 40, 42}, {6, 48, 50}, {8, 58, 60}, {10, 68, 70},
        {12, 78, 80}, {15, 90, 92}, {18, 102, 104}, {22, 115, 117},
        {28, 128, 130}, {35, 142, 144}, {45, 156, 158}, {58, 170, 172},
        {75, 185, 187}, {95, 200, 200}, {120, 215, 213}, {150, 230, 225},
    }; }
    int[][] palMid() { return new int[][]{
        {10, 60, 62}, {14, 72, 74}, {18, 85, 87}, {24, 98, 100},
        {30, 112, 114}, {38, 126, 128}, {48, 140, 142}, {60, 155, 155},
        {75, 170, 168}, {92, 185, 180}, {112, 198, 190}, {135, 210, 198},
        {160, 222, 205}, {188, 232, 212}, {215, 242, 220}, {240, 250, 232},
    }; }
    int[][] palBright() { return new int[][]{
        {40, 140, 140}, {60, 160, 158}, {85, 180, 175}, {112, 198, 190},
        {140, 213, 202}, {168, 225, 212}, {195, 235, 222}, {215, 242, 230},
        {230, 247, 238}, {240, 250, 244}, {247, 252, 248}, {250, 253, 250},
        {252, 254, 252}, {253, 254, 253}, {254, 255, 254}, {255, 255, 255},
    }; }
    double[] speedBase() { return new double[]{3.5, 2.5, 2.0, 1.2}; }
    double[] speedAmp() { return new double[]{2.0, 1.5, 1.0, 0.8}; }
    double[] speedPeriodMs() { return new double[]{9000, 13000, 17000, 21000}; }
    double w1Mult() { return 0.55; }
    double w2Mult() { return 0.45; }
    double w3Pow() { return 3.0; }
    double w3Mult() { return 0.5; }
    double w4Pow() { return 4.0; }
    double w4Mult() { return 0.35; }
    double foamDecay() { return 0.85; }
    double foamChance() { return 0.015; }
    int foamAddMin() { return 20; }
    int foamAddRange() { return 40; }
    double foamMax() { return 80; }
}

final class PacificaStormEffect extends PacificaEffect {
    int[][] palDeep() { return new int[][]{
        {2, 3, 6}, {3, 4, 8}, {4, 6, 11}, {5, 8, 14},
        {6, 10, 18}, {8, 13, 23}, {10, 17, 29}, {13, 22, 36},
        {17, 28, 44}, {22, 36, 54}, {28, 46, 66}, {36, 58, 80},
        {46, 72, 95}, {58, 88, 110}, {72, 104, 124}, {88, 120, 138},
    }; }
    int[][] palMid() { return new int[][]{
        {4, 6, 10}, {6, 9, 15}, {9, 13, 21}, {13, 18, 28},
        {18, 24, 36}, {24, 32, 46}, {32, 42, 58}, {42, 54, 72},
        {54, 68, 88}, {68, 84, 104}, {85, 102, 120}, {104, 120, 136},
        {125, 138, 150}, {148, 156, 162}, {172, 174, 174}, {195, 192, 186},
    }; }
    int[][] palBright() { return new int[][]{
        {20, 24, 30}, {35, 40, 48}, {55, 62, 70}, {78, 86, 94},
        {104, 112, 118}, {132, 138, 142}, {160, 164, 166}, {186, 188, 188},
        {206, 208, 206}, {222, 224, 220}, {234, 236, 232}, {242, 244, 240},
        {247, 249, 246}, {250, 252, 249}, {253, 254, 252}, {255, 255, 255},
    }; }
    double[] speedBase() { return new double[]{10.0, 7.0, 5.0, 3.5}; }
    double[] speedAmp() { return new double[]{6.0, 5.0, 3.0, 2.5}; }
    double[] speedPeriodMs() { return new double[]{6000, 9000, 12000, 15000}; }
    double w1Mult() { return 0.65; }
    double w2Mult() { return 0.55; }
    double w3Pow() { return 2.2; }
    double w3Mult() { return 0.85; }
    double w4Pow() { return 3.0; }
    double w4Mult() { return 0.65; }
    double foamDecay() { return 0.80; }
    double foamChance() { return 0.04; }
    int foamAddMin() { return 80; }
    int foamAddRange() { return 140; }
    double foamMax() { return 220; }
}

final class PacificaDeepCurrentEffect extends PacificaEffect {
    int[][] palDeep() { return new int[][]{
        {0, 1, 4}, {0, 1, 6}, {0, 2, 8}, {0, 2, 10},
        {0, 3, 13}, {0, 4, 16}, {1, 5, 20}, {1, 6, 24},
        {2, 8, 29}, {3, 10, 34}, {4, 13, 40}, {6, 17, 47},
        {9, 22, 54}, {13, 28, 62}, {18, 36, 70}, {24, 45, 78},
    }; }
    int[][] palMid() { return new int[][]{
        {1, 2, 8}, {2, 3, 12}, {3, 5, 17}, {4, 7, 23},
        {6, 10, 30}, {8, 14, 38}, {11, 19, 47}, {15, 25, 57},
        {20, 32, 68}, {27, 41, 80}, {35, 52, 92}, {45, 65, 105},
        {58, 80, 118}, {73, 97, 130}, {92, 116, 142}, {114, 137, 154},
    }; }
    int[][] palBright() { return new int[][]{
        {3, 4, 15}, {5, 7, 22}, {8, 11, 31}, {12, 17, 42},
        {18, 25, 54}, {25, 35, 68}, {34, 47, 83}, {45, 62, 99},
        {58, 79, 115}, {74, 98, 131}, {92, 118, 146}, {113, 138, 160},
        {136, 158, 172}, {160, 177, 183}, {186, 196, 193}, {212, 215, 206},
    }; }
    double[] speedBase() { return new double[]{4.0, 3.0, 2.0, 1.2}; }
    double[] speedAmp() { return new double[]{2.0, 1.5, 1.0, 0.6}; }
    double[] speedPeriodMs() { return new double[]{11000, 15000, 19000, 23000}; }
    double w1Mult() { return 0.45; }
    double w2Mult() { return 0.35; }
    double w3Pow() { return 4.5; }
    double w3Mult() { return 0.40; }
    double w4Pow() { return 5.0; }
    double w4Mult() { return 0.25; }
    double foamDecay() { return 0.90; }
    double foamChance() { return 0.004; }
    int foamAddMin() { return 10; }
    int foamAddRange() { return 20; }
    double foamMax() { return 50; }
}
