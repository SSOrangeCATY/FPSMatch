package com.phasetranscrystal.fpsmatch.core.minimap.hud;

@FunctionalInterface
public interface HudSafeAreaContributor {
    void contribute(HudSafeAreaRegistry registry);
}