package com.phasetranscrystal.fpsmatch.core.shop.skin;

import com.phasetranscrystal.fpsmatch.core.shop.ItemType;

import java.util.UUID;

public interface PlayerSkinData {
    ShopGunSkin getSkin(String team, ItemType type, int index);
    UUID getOwner();
}
