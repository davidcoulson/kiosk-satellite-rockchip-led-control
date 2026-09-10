// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.rockchip;

final class LedMath {
    static int[] color(String hex) {
        if (!hex.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Invalid RGB color");
        int rgb = Integer.parseInt(hex.substring(1),16);
        return new int[]{rgb>>16 & 255,rgb>>8 & 255,rgb & 255};
    }
    static int scale(int channel, double brightness, int limit) {
        if (channel<0 || channel>255 || !Double.isFinite(brightness) || brightness<0 || brightness>100 || limit<1 || limit>255)
            throw new IllegalArgumentException("Invalid LED channel range");
        return (int)Math.round(channel * brightness * limit / 25500.0);
    }
    static int[] hue(double hue) {
        double h = (hue-Math.floor(hue))*6;
        double x = 1-Math.abs(h%2-1);
        double[][] segments={{1,x,0},{x,1,0},{0,1,x},{0,x,1},{x,0,1},{1,0,x}};
        double[] rgb=segments[(int)h];
        return new int[]{(int)Math.round(rgb[0]*255),(int)Math.round(rgb[1]*255),(int)Math.round(rgb[2]*255)};
    }
    static int[] frame(String effect, long elapsed, double period, int[] base) {
        double phase=(elapsed%Math.max(1,(long)period))/period;
        if ("Rainbow".equals(effect)) return hue(phase);
        if ("Random".equals(effect)) return hue((Math.floor(elapsed/period)*0.61803398875)%1);
        double factor=1;
        if ("Pulse".equals(effect)) factor=0.1+0.9*(1-Math.cos(phase*2*Math.PI))/2;
        if ("Blink".equals(effect)) factor=phase<0.5?1:0;
        if ("Candle".equals(effect)) factor=0.65+0.2*Math.sin(elapsed/73.0)+0.15*Math.sin(elapsed/139.0);
        return new int[]{(int)Math.round(base[0]*factor),(int)Math.round(base[1]*factor),(int)Math.round(base[2]*factor)};
    }
}
