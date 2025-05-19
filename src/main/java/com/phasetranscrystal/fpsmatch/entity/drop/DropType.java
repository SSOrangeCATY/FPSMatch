package com.phasetranscrystal.fpsmatch.entity.drop;

import com.phasetranscrystal.fpsmatch.FPSMConfig;
import com.phasetranscrystal.fpsmatch.core.item.IThrowEntityAble;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Predicate;

public enum DropType {
    MAIN_WEAPON((player -> {
        int i = player.getInventory().clearOrCountMatchingItems(FPSMUtil.MAIN_WEAPON_PREDICATE, 0, player.inventoryMenu.getCraftSlots());
        return i < FPSMConfig.common.mainWeaponCount.get();
    })),
    SECONDARY_WEAPON((player -> {
        int i = player.getInventory().clearOrCountMatchingItems(FPSMUtil.SECONDARY_WEAPON_PREDICATE, 0, player.inventoryMenu.getCraftSlots());
        return i < FPSMConfig.common.secondaryWeaponCount.get();
    })),
    THIRD_WEAPON((player -> {
        int i = player.getInventory().clearOrCountMatchingItems(FPSMUtil.THIRD_WEAPON_PREDICATE, 0, player.inventoryMenu.getCraftSlots());
        return i < FPSMConfig.common.thirdWeaponCount.get();
    })),
    THROW((player -> {
        int i = player.getInventory().clearOrCountMatchingItems(FPSMUtil.THROW_PREDICATE, 0, player.inventoryMenu.getCraftSlots());
        return i < FPSMConfig.common.throwableCount.get();
    })),

    MISC((player -> true));

    public final Predicate<Player> playerPredicate;
    DropType(Predicate<Player> playerPredicate) {
        this.playerPredicate = playerPredicate;
    }

    public static Predicate<ItemStack> getPredicateByDropType(DropType type){
        return switch (type) {
            case MAIN_WEAPON -> FPSMUtil.MAIN_WEAPON_PREDICATE;
            case SECONDARY_WEAPON -> FPSMUtil.SECONDARY_WEAPON_PREDICATE;
            case THIRD_WEAPON -> FPSMUtil.THIRD_WEAPON_PREDICATE;
            case THROW -> FPSMUtil.THROW_PREDICATE;
            case MISC -> FPSMUtil.MISC_PREDICATE;
        };
    }

    public static DropType getItemDropType(ItemStack itemStack) {
        if (itemStack.getItem() instanceof IGun iGun) {
            Optional<GunTabType> optional = FPSMUtil.getGunTypeByGunId(iGun.getGunId(itemStack));
            if (optional.isPresent()) {
                switch (optional.get()) {
                    case RIFLE, SNIPER, SHOTGUN, SMG, MG -> {
                        return DropType.MAIN_WEAPON;
                    }
                    case PISTOL -> {
                        return DropType.SECONDARY_WEAPON;
                    }
                    case RPG -> {
                        return DropType.THIRD_WEAPON;
                    }
                }
            }
        }

        if (itemStack.getItem() instanceof IThrowEntityAble) {
            return DropType.THROW;
        } else {
            return DropType.MISC;
        }
    }
}