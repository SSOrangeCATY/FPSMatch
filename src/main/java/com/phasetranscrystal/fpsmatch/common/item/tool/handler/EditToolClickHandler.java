package com.phasetranscrystal.fpsmatch.common.item.tool.handler;

import com.phasetranscrystal.fpsmatch.common.item.tool.EditToolItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface EditToolClickHandler {
    void handleClick(EditToolItem tool, ItemStack stack, ServerPlayer player,
                     boolean isDoubleClicked, boolean isShiftKeyDown, ClickAction action);

}