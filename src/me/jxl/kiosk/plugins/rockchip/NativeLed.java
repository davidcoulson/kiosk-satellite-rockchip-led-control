// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.rockchip;

public final class NativeLed {
    private NativeLed() {}
    public static void load(String path) { System.load(path); }
    public static native int probe();
    public static native int write(int red, int green, int blue);
    public static native int off();
}
