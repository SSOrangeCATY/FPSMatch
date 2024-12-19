package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.shop.ShopData;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface ShopMap {
    FPSMShop getShop();

    default void addPlayerMoney(UUID uuid, int money){
        this.getShop().getPlayerShopData(uuid).addMoney(money);
        this.getShop().syncShopMoneyData(uuid);
    }

    default void removePlayerMoney(UUID uuid, int money){
        this.getShop().getPlayerShopData(uuid).reduceMoney(money);
        this.getShop().syncShopMoneyData(uuid);
    }

    default void setPlayerMoney(UUID uuid, int money){
        this.getShop().getPlayerShopData(uuid).setMoney(money);
        this.getShop().syncShopMoneyData(uuid);
    }


}
