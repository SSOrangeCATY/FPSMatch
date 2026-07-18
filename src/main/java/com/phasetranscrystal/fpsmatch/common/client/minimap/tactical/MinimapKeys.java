package com.phasetranscrystal.fpsmatch.common.client.minimap.tactical;

/**
 * Pure key-gate helpers for the tactical map. Platform KeyMapping registration is separate.
 * Intentionally avoids TACZ InputExtraCheck so the gate is generic IN_GAME semantics.
 */
public final class MinimapKeys {
    private MinimapKeys() {
    }

    /**
     * @param inGame player is in a match/world context
     * @param capabilityPresent current MapKey has minimap capability/manifest
     * @param textInputActive chat/editor text field focused
     * @param alreadyOpen controller already open (consumeClick should not re-open)
     */
    public static boolean canConsumeOpen(
            boolean inGame,
            boolean capabilityPresent,
            boolean textInputActive,
            boolean alreadyOpen
    ) {
        if (!inGame || !capabilityPresent || textInputActive || alreadyOpen) {
            return false;
        }
        return true;
    }
}