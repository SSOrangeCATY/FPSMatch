package com.ptcrys.fpsmatch.core.minimap.editor.raster;

public final class Rgba8 {
    private Rgba8() {
    }

    public static int of(int red, int green, int blue, int alpha) {
        requireChannel(red, "red");
        requireChannel(green, "green");
        requireChannel(blue, "blue");
        requireChannel(alpha, "alpha");
        if (alpha == 0) {
            return 0;
        }
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    public static int red(int rgba) {
        return (rgba >>> 16) & 0xFF;
    }

    public static int green(int rgba) {
        return (rgba >>> 8) & 0xFF;
    }

    public static int blue(int rgba) {
        return rgba & 0xFF;
    }

    public static int alpha(int rgba) {
        return (rgba >>> 24) & 0xFF;
    }

    public static int sourceOver(int dst, int src) {
        int srcA = alpha(src);
        if (srcA <= 0) {
            return normalizeTransparent(dst);
        }
        if (srcA >= 255) {
            return of(red(src), green(src), blue(src), 255);
        }
        int dstA = alpha(dst);
        int outA = srcA + ((dstA * (255 - srcA) + 127) / 255);
        if (outA <= 0) {
            return 0;
        }
        int outR = (red(src) * srcA + red(dst) * dstA * (255 - srcA) / 255 + outA / 2) / outA;
        int outG = (green(src) * srcA + green(dst) * dstA * (255 - srcA) / 255 + outA / 2) / outA;
        int outB = (blue(src) * srcA + blue(dst) * dstA * (255 - srcA) / 255 + outA / 2) / outA;
        return of(clamp(outR), clamp(outG), clamp(outB), clamp(outA));
    }

    public static int scaleAlpha(int rgba, float coverage) {
        if (coverage <= 0.0f) {
            return 0;
        }
        int alpha = Math.round(alpha(rgba) * Math.min(1.0f, coverage));
        if (alpha <= 0) {
            return 0;
        }
        return of(red(rgba), green(rgba), blue(rgba), clamp(alpha));
    }

    private static int normalizeTransparent(int rgba) {
        return alpha(rgba) == 0 ? 0 : rgba;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static void requireChannel(int value, String name) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " channel must be in [0, 255]");
        }
    }
}
