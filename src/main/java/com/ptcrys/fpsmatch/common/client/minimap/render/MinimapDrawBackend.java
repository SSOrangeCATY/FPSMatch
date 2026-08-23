package com.ptcrys.fpsmatch.common.client.minimap.render;

/**
 * Pure draw-command backend contract. Version adapters submit actual GUI work.
 */
public interface MinimapDrawBackend {
    void submit(MinimapFrame frame);
}