package com.ptcrys.fpsmatch.common.client.minimap;

import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Objects;

public enum MinimapScope {
    MATCH_HUD,
    TACTICAL_SCREEN,
    EDITOR;

    public static MinimapScope fromWire(WireIdentity.Scope scope) {
        Objects.requireNonNull(scope, "scope");
        return switch (scope) {
            case MATCH_HUD -> MATCH_HUD;
            case TACTICAL_SCREEN -> TACTICAL_SCREEN;
            case EDITOR -> EDITOR;
        };
    }

    public WireIdentity.Scope toWire() {
        return switch (this) {
            case MATCH_HUD -> WireIdentity.Scope.MATCH_HUD;
            case TACTICAL_SCREEN -> WireIdentity.Scope.TACTICAL_SCREEN;
            case EDITOR -> WireIdentity.Scope.EDITOR;
        };
    }
}