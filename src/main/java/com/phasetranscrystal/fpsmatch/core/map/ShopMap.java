package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface ShopMap<T extends BaseMap> extends IMap<T> {
    FPSMShop getShop();
    @Nullable
    ShopData defineShopData();
    default void addPlayerMoney(UUID uuid, int money){
        this.getShop().getPlayerShopData(uuid).addMoney(money);
    }

    default void removePlayerMoney(UUID uuid, int money){
        this.getShop().getPlayerShopData(uuid).takeMoney(money);
    }

    default void setPlayerMoney(UUID uuid, int money){
        this.getShop().getPlayerShopData(uuid).setMoney(money);
    }


}
