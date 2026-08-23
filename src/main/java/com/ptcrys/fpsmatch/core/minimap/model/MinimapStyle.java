package com.ptcrys.fpsmatch.core.minimap.model;

public sealed interface MinimapStyle permits RegionStyle, LineStyle, TextStyle, IconStyle {
    NamespacedId id();

    StyleType type();
}
