package com.ptcrys.fpsmatch.core.minimap.contract;

import java.util.regex.Pattern;

public final class MinimapFormatContract {
    private static final Pattern INTERNAL_SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public static final MinimapFormatVersion CURRENT = new MinimapFormatVersion(1, 0);
    public static final String SOURCE_EXTENSION = ".fpsmap";
    public static final String RUNTIME_EXTENSION = ".fpsmapc";

    private MinimapFormatContract() {
    }

    public static boolean isInternalSlug(String value) {
        if (value == null || value.equals(".") || value.equals("..")) {
            return false;
        }
        return value.length() <= MinimapHardLimits.MAX_INTERNAL_SLUG_UTF8_BYTES
                && INTERNAL_SLUG.matcher(value).matches();
    }
}
