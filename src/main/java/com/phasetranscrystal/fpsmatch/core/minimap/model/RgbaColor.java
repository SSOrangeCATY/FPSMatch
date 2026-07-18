package com.phasetranscrystal.fpsmatch.core.minimap.model;

public record RgbaColor(int red, int green, int blue, int alpha) {
    public RgbaColor {
        requireChannel(red, "red");
        requireChannel(green, "green");
        requireChannel(blue, "blue");
        requireChannel(alpha, "alpha");
    }

    private static void requireChannel(int value, String name) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " channel must be in [0, 255]");
        }
    }
}
