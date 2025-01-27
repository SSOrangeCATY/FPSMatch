package com.phasetranscrystal.fpsmatch.core.data;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public record DeathMessage(Component killer, Component dead, ItemStack weapon, boolean isHeadShot) {
    public Component getFullText() {
        return Component.translatable("fpsm.death.message" + (this.isHeadShot ? ".headshot" : ""), this.killer, this.dead, this.weapon.getDisplayName().getString().replace("[","").replace("]","").replaceAll("§.", ""));
    }

}
