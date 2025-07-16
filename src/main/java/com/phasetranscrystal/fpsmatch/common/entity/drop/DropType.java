package com.phasetranscrystal.fpsmatch.common.entity.drop;

import com.phasetranscrystal.fpsmatch.FPSMConfig;
import com.phasetranscrystal.fpsmatch.core.item.IThrowEntityAble;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public enum DropType {
    MAIN_WEAPON((player -> {
        int i = 0;
        for(Predicate<ItemStack> predicate : FPSMUtil.MAIN_WEAPON_PREDICATE){
            i += player.getInventory().clearOrCountMatchingItems(predicate, 0, player.inventoryMenu.getCraftSlots());
        }
        return i < FPSMConfig.common.mainWeaponCount.get();
    })),
    SECONDARY_WEAPON((player -> {
        int i = 0;
        for (Predicate<ItemStack> predicate : FPSMUtil.SECONDARY_WEAPON_PREDICATE) {
            i += player.getInventory().clearOrCountMatchingItems(predicate, 0, player.inventoryMenu.getCraftSlots());
        }
        return i < FPSMConfig.common.secondaryWeaponCount.get();
    })),
    THIRD_WEAPON((player -> {
        int i = 0;
        for (Predicate<ItemStack> predicate : FPSMUtil.THIRD_WEAPON_PREDICATE) {
            i += player.getInventory().clearOrCountMatchingItems(predicate, 0, player.inventoryMenu.getCraftSlots());
        }
        return i < FPSMConfig.common.thirdWeaponCount.get();
    })),
    THROW((player -> {
        int i = 0;
        for (Predicate<ItemStack> predicate : FPSMUtil.THROW_PREDICATE) {
            i += player.getInventory().clearOrCountMatchingItems(predicate, 0, player.inventoryMenu.getCraftSlots());
        }
        return i < FPSMConfig.common.throwableCount.get();
    })),

    MISC((player -> true));

    public final Predicate<Player> playerPredicate;
    DropType(Predicate<Player> playerPredicate) {
        this.playerPredicate = playerPredicate;
    }

    private static final Map<DropType, List<Predicate<ItemStack>>> PREDICATE_MAP = new HashMap<>();

    static {
        PREDICATE_MAP.put(DropType.MAIN_WEAPON,FPSMUtil.MAIN_WEAPON_PREDICATE);
        PREDICATE_MAP.put(DropType.SECONDARY_WEAPON,FPSMUtil.SECONDARY_WEAPON_PREDICATE);
        PREDICATE_MAP.put(DropType.THIRD_WEAPON,FPSMUtil.THIRD_WEAPON_PREDICATE);
        PREDICATE_MAP.put(DropType.THROW,FPSMUtil.THROW_PREDICATE);
        PREDICATE_MAP.put(DropType.MISC,FPSMUtil.MISC_PREDICATE);
    }
    public static List<Predicate<ItemStack>> getPredicateByDropType(DropType type){
        return PREDICATE_MAP.get(type);
    }

    public static DropType getItemDropType(ItemStack itemStack) {
        for (Map.Entry<DropType, List<Predicate<ItemStack>>> entrySet : PREDICATE_MAP.entrySet()) {
            if(entrySet.getValue().stream().anyMatch(predicate -> predicate.test(itemStack))){
                return entrySet.getKey();
            }
        }
        return DropType.MISC;
    }
}