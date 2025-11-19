package com.phasetranscrystal.fpsmatch.common.capability.team;

import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapability;
import com.phasetranscrystal.fpsmatch.core.capability.team.TeamCapability;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.shop.INamedType;
import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;

public class ShopCapability<T extends Enum<T> & INamedType> extends TeamCapability implements FPSMCapability.Savable<FPSMShop<T>> {
    private final BaseTeam team;
    private FPSMShop<T> shop;
    private Class<T> type;
    private int startMoney = 800;

    public ShopCapability(ServerTeam team) {
        this.team = team;
    }

    public void setStartMoney(int startMoney) {
        this.startMoney = startMoney;
    }

    public void setType(Class<T> type) {
        this.type = type;
    }

    private void create(){
        if(type == null) throw new IllegalArgumentException("type is null");
        this.shop = FPSMShop.create(type,team.name,startMoney);
    }

    public FPSMShop<T> getShop() {
        if(shop == null) create();
        return shop;
    }

    @Override
    public BaseTeam getHolder() {
        return team;
    }

    @Override
    public Codec<FPSMShop<T>> codec() {
        return FPSMShop.withCodec(type);
    }

    @Override
    public FPSMShop<T> write(FPSMShop<T> value) {
        return this.shop = value;
    }

    @Override
    public FPSMShop<T> read() {
        return getShop();
    }
}
