package com.phasetranscrystal.fpsmatch.common.entity.drop;

import com.phasetranscrystal.fpsmatch.FPSMConfig;
import com.phasetranscrystal.fpsmatch.core.item.IThrowEntityAble;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.IGun;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public enum DropType {
    MAIN_WEAPON((stack -> {
        for(Predicate<ItemStack> predicate : FPSMUtil.MAIN_WEAPON_PREDICATE){
            if(predicate.test(stack)){
                return true;
            }
        }
        return false;
    })),
    SECONDARY_WEAPON((stack -> {
        for (Predicate<ItemStack> predicate : FPSMUtil.SECONDARY_WEAPON_PREDICATE) {
            if(predicate.test(stack)){
                return true;
            }
        }
        return false;
    })),
    THIRD_WEAPON((stack -> {
        for (Predicate<ItemStack> predicate : FPSMUtil.THIRD_WEAPON_PREDICATE) {
            if(predicate.test(stack)){
                return true;
            }
        }
        return false;
    })),
    THROW((stack -> {
        for (Predicate<ItemStack> predicate : FPSMUtil.THROW_PREDICATE) {
            if(predicate.test(stack)){
                return true;
            }
        }
        return false;
    })),
    MISC((stack -> true));

    private final Predicate<ItemStack> predicate;

    DropType(Predicate<ItemStack> predicate) {
        this.predicate = predicate;
    }

    public Predicate<Player> inventoryMatch(){
        return (player) -> {
            int i = player.getInventory().clearOrCountMatchingItems(predicate, 0, player.inventoryMenu.getCraftSlots());
            return i < getLimit(this);
        };
    }

    public Predicate<ItemStack> itemMatch(){
        return predicate;
    }

    public static int getLimit(DropType type){
        return switch (type) {
            case MAIN_WEAPON -> FPSMConfig.common.mainWeaponCount.get();
            case SECONDARY_WEAPON -> FPSMConfig.common.secondaryWeaponCount.get();
            case THIRD_WEAPON -> FPSMConfig.common.thirdWeaponCount.get();
            case THROW -> FPSMConfig.common.throwableCount.get();
            case MISC -> Integer.MAX_VALUE;
        };
    }

    public static DropType getItemDropType(ItemStack itemStack) {
        for (DropType type : DropType.values()) {
            if(type.itemMatch().test(itemStack)){
                return type;
            }
        }
        return DropType.MISC;
    }
}