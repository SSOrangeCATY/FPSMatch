package com.tacz.guns.inventory.tooltip;

import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

public class BlockItemTooltip implements TooltipComponent {
    private final Identifier blockId;

    public BlockItemTooltip(Identifier blockId) {
        this.blockId = blockId;
    }

    public Identifier getBlockId() {
        return blockId;
    }
}
