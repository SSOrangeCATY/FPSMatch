package com.phasetranscrystal.fpsmatch.core.data;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public record KillMessage(Component killer, Component dead, ItemStack weapon, boolean isHeadShot) {

}
