package com.phasetranscrystal.fpsmatch.common.item.edit.handler;

import com.phasetranscrystal.fpsmatch.common.item.edit.EditToolItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record ClickActionContext(EditToolItem tool, ItemStack stack, ServerPlayer player, boolean isDoubleClicked,
                                 boolean isShiftKeyDown, ClickAction action) {
}