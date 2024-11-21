package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.BaseMap;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface GiveStartKitsMap<T extends BaseMap> {
    T getMap();
    List<ItemStack> getKits(String team);
    void setKits(String team, ItemStack itemStack);
    void setAllTeamKits(ItemStack itemStack);
}
