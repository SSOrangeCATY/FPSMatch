package com.ptcrys.fpsmatch.common.client.minimap.hud;

/**
 * Pure render-time context for global HUD predicates. No Minecraft types.
 */
public record HudRenderContext(
        boolean capabilityPresent,
        boolean globalEnabled,
        boolean minimapEnabled,
        String gameType,
        boolean spectator,
        boolean tacticalScreenOpen
) {
    public HudRenderContext(
            boolean capabilityPresent,
            boolean globalEnabled,
            boolean minimapEnabled,
            String gameType,
            boolean spectator
    ) {
        this(
                capabilityPresent,
                globalEnabled,
                minimapEnabled,
                gameType,
                spectator,
                false
        );
    }

    public boolean minimapHudVisible() {
        return capabilityPresent
                && globalEnabled
                && minimapEnabled
                && !tacticalScreenOpen;
    }
}
