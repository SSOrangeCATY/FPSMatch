package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.FPSMShop;

import java.util.List;
import java.util.UUID;

public interface ShopMap<T extends BaseMap> extends IMap<T> {
    FPSMShop getShop(String shopName);
    List<FPSMShop> getShops();
    List<String> getShopNames();

    default void addPlayerMoney(UUID uuid, int money){
        BaseTeam team = this.getMap().getMapTeams().getTeamByPlayer(uuid);
        if(team != null){
            this.getShop(team.name).getPlayerShopData(uuid).addMoney(money);
            this.getShop(team.name).syncShopMoneyData(uuid);
        }
    }

    default void removePlayerMoney(UUID uuid, int money){
        BaseTeam team = this.getMap().getMapTeams().getTeamByPlayer(uuid);
        if(team != null) {
            this.getShop(team.name).getPlayerShopData(uuid).reduceMoney(money);
            this.getShop(team.name).syncShopMoneyData(uuid);
        }
    }

    default void setPlayerMoney(UUID uuid, int money){
            BaseTeam team = this.getMap().getMapTeams().getTeamByPlayer(uuid);
            if(team != null){
                this.getShop(team.name).getPlayerShopData(uuid).setMoney(money);
                this.getShop(team.name).syncShopMoneyData(uuid);
            }
    }

    default void syncShopData(){
        this.getShops().forEach(shop->{
            shop.clearPlayerShopData();
            shop.syncShopData();
            shop.syncShopMoneyData();
        });
    }


}
