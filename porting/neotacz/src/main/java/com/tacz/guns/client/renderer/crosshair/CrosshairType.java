package com.tacz.guns.client.renderer.crosshair;

import com.google.common.collect.Maps;
import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.Map;

public enum CrosshairType {
    EMPTY,
    DOT_1,
    CIRCLE_1,
    CIRCLE_2,
    CIRCLE_3,
    CROSS_1,
    CROSS_2,
    CROSS_3,
    CROSS_4,
    CROSS_5,
    CROSS_6,
    LINE_1,
    LINE_2,
    LINE_3,
    SQUARE_1,
    SQUARE_2,
    SQUARE_3,
    SQUARE_4,
    SQUARE_5,
    SQUARE_6,
    TRIDENT_1,
    TRIDENT_2;

    private static final Map<CrosshairType, Identifier> CACHE = Maps.newHashMap();

    public static Identifier getTextureLocation(CrosshairType type) {
        Identifier location = CACHE.get(type);
        if (location == null) {
            location = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/crosshair/normal/%s.png".formatted(type.name().toLowerCase(Locale.US)));
            CACHE.put(type, location);
        }
        return location;
    }
}
