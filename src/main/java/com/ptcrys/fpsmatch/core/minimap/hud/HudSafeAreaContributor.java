package com.ptcrys.fpsmatch.core.minimap.hud;

@FunctionalInterface
public interface HudSafeAreaContributor {
    void contribute(HudSafeAreaRegistry registry);
}