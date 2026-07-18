package com.phasetranscrystal.fpsmatch.common.client.minimap.tactical;

/**
 * Pure gate inputs for opening the read-only tactical map.
 */
public record TacticalOpenRequest(
        boolean inGame,
        boolean capabilityPresent,
        boolean textInputActive,
        boolean passiveOnly
) {
}