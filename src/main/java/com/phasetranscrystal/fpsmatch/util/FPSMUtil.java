package com.phasetranscrystal.fpsmatch.util;

import com.google.common.collect.ImmutableList;
import com.phasetranscrystal.fpsmatch.core.item.IThrowEntityAble;
import com.phasetranscrystal.fpsmatch.gamerule.FPSMatchRule;
import com.phasetranscrystal.fpsmatch.impl.FPSMImpl;
import com.phasetranscrystal.fpsmatch.item.CompositionC4;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;

public class FPSMUtil {
    public static final List<GunTabType> MAIN_WEAPON = ImmutableList.of(GunTabType.RIFLE,GunTabType.SNIPER,GunTabType.SHOTGUN,GunTabType.SMG,GunTabType.MG);;
    public static final Predicate<ItemStack> MAIN_WEAPON_PREDICATE = (itemStack -> {
        if(itemStack.getItem() instanceof IGun gun){
            return isMainWeapon(gun.getGunId(itemStack));
        }else{
            return false;
        }
    });
    public static final Predicate<ItemStack> SECONDARY_WEAPON_PREDICATE = (itemStack -> {
        if(itemStack.getItem() instanceof IGun gun){
            return getGunTypeByGunId(gun.getGunId(itemStack)).filter(gunTabType -> gunTabType == GunTabType.PISTOL).isPresent();
        }else{
            return false;
        }
    });
    public static final Predicate<ItemStack> THIRD_WEAPON_PREDICATE;
    public static final Predicate<ItemStack> THROW_PREDICATE;
    public static final Predicate<ItemStack> MISC_PREDICATE = (itemStack -> true);
    public static final Predicate<ItemStack> C4_PREDICATE = (itemStack -> itemStack.getItem() instanceof CompositionC4);

    static{
        THROW_PREDICATE = (itemStack -> {
            if(itemStack.getItem() instanceof IThrowEntityAble){
                return true;
            }else{
                if (FPSMImpl.findEquipmentMod()){
                    try{
                        return itemStack.getItem() instanceof me.xjqsh.lrtactical.api.item.IThrowable;
                    }catch (Exception e){
                        return false;
                    }
                }else{
                    return false;
                }
            }
        });


        THIRD_WEAPON_PREDICATE = (itemStack -> {
            if(itemStack.getItem() instanceof IGun gun){
                return getGunTypeByGunId(gun.getGunId(itemStack)).filter(gunTabType -> gunTabType == GunTabType.RPG).isPresent();
            }else{
                if (FPSMImpl.findEquipmentMod()){
                    try{
                        return itemStack.getItem() instanceof me.xjqsh.lrtactical.api.item.IMeleeWeapon;
                    }catch (Exception e){
                        return false;
                    }
                }else{
                    return false;
                }
            }
        });
    }

    public static Optional<GunTabType> getGunTypeByGunId(ResourceLocation gunId){
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(commonGunIndex -> GunTabType.valueOf(commonGunIndex.getType().toUpperCase(Locale.US)));
    }

    public static boolean isMainWeapon(ResourceLocation gunId){
        return getGunTypeByGunId(gunId).filter(MAIN_WEAPON::contains).isPresent();
    }

    public static void sortPlayerInventory(Player player) {
        if (player.level().getGameRules().getRule(FPSMatchRule.RULE_AUTO_SORT_PLAYER_INV).get()) {
            Inventory inventory = player.getInventory();

            List<ItemStack> allItems = new ArrayList<>();
            for (int i = 0; i < inventory.items.size(); i++) {
                ItemStack stack = inventory.items.get(i);
                if (!stack.isEmpty()) {
                    allItems.add(stack.copy()); // 创建独立副本
                }
                inventory.items.set(i, ItemStack.EMPTY);
            }

            // 2. 按分类分组（操作副本）
            Map<Predicate<ItemStack>, List<ItemStack>> categoryMap = new LinkedHashMap<>();
            categoryMap.put(MAIN_WEAPON_PREDICATE, new ArrayList<>());
            categoryMap.put(SECONDARY_WEAPON_PREDICATE, new ArrayList<>());
            categoryMap.put(THIRD_WEAPON_PREDICATE, new ArrayList<>());
            categoryMap.put(C4_PREDICATE, new ArrayList<>());
            categoryMap.put(THROW_PREDICATE, new ArrayList<>());
            categoryMap.put(MISC_PREDICATE, new ArrayList<>());

            for (ItemStack stack : allItems) {
                for (Map.Entry<Predicate<ItemStack>, List<ItemStack>> entry : categoryMap.entrySet()) {
                    if (entry.getKey().test(stack)) {
                        entry.getValue().add(stack);
                        break;
                    }
                }
            }

            // 3. 安全合并堆叠（不修改原始数据）
            List<ItemStack> mainWeapons = mergeItemStacks(categoryMap.get(MAIN_WEAPON_PREDICATE));
            List<ItemStack> secondaryWeapons = mergeItemStacks(categoryMap.get(SECONDARY_WEAPON_PREDICATE));
            List<ItemStack> thirdWeapons = mergeItemStacks(categoryMap.get(THIRD_WEAPON_PREDICATE));
            List<ItemStack> c4Items = mergeItemStacks(categoryMap.get(C4_PREDICATE));
            List<ItemStack> throwables = mergeItemStacks(categoryMap.get(THROW_PREDICATE));
            List<ItemStack> miscItems = mergeItemStacks(categoryMap.get(MISC_PREDICATE));

            // 4. 投掷物排序
            throwables.sort(Comparator.comparing(stack ->
                    stack.getHoverName().getString().toLowerCase()
            ));

            // 5. 创建新物品分布
            Map<Integer, ItemStack> sortedSlots = new HashMap<>();
            List<ItemStack> remainingItems = new ArrayList<>();

            // 6. 分配快捷栏位
            assignToSlot(sortedSlots, mainWeapons, 0);
            assignToSlot(sortedSlots, secondaryWeapons, 1);
            assignToSlot(sortedSlots, thirdWeapons, 2);
            assignToSlot(sortedSlots, c4Items, 3);

            // 7. 分配投掷物 (5-9 槽)
            int throwableSlot = 4;
            for (ItemStack throwable : throwables) {
                if (throwableSlot > 8) {
                    remainingItems.add(throwable);
                } else {
                    sortedSlots.put(throwableSlot++, throwable);
                }
            }

            // 8. 合并剩余物品
            remainingItems.addAll(mainWeapons);
            remainingItems.addAll(secondaryWeapons);
            remainingItems.addAll(thirdWeapons);
            remainingItems.addAll(c4Items);
            remainingItems.addAll(miscItems);

            // 9. 应用新分布
            for (int i = 0; i < inventory.items.size(); i++) {
                if (sortedSlots.containsKey(i)) {
                    inventory.items.set(i, sortedSlots.get(i));
                } else if (i >= 9 && !remainingItems.isEmpty()) {
                    inventory.items.set(i, remainingItems.remove(0));
                }
            }
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    // 安全的堆叠合并方法
    private static List<ItemStack> mergeItemStacks(List<ItemStack> stacks) {
        Map<ItemKey, Integer> countMap = new HashMap<>();
        // 1. 计算总数并为每个键保留模板
        Map<ItemKey, ItemStack> templateMap = new HashMap<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;

            ItemKey key = new ItemKey(stack);
            countMap.put(key, countMap.getOrDefault(key, 0) + stack.getCount());
            templateMap.putIfAbsent(key, stack.copy()); // 为每个键保留一个模板
        }

        // 2. 重建堆栈
        List<ItemStack> result = new ArrayList<>();
        for (Map.Entry<ItemKey, Integer> entry : countMap.entrySet()) {
            ItemKey key = entry.getKey();
            int total = entry.getValue();
            ItemStack template = templateMap.get(key);
            int maxStack = template.getMaxStackSize();

            while (total > 0) {
                ItemStack newStack = template.copy();
                newStack.setCount(Math.min(total, maxStack));
                result.add(newStack);
                total -= newStack.getCount();
            }
        }
        return result;
    }


    // 分配物品到指定槽位
    private static void assignToSlot(Map<Integer, ItemStack> map, List<ItemStack> items, int slot) {
        if (!items.isEmpty()) {
            map.put(slot, items.remove(0));
        }
    }

    public static void setTotalDummyAmmo(ItemStack itemStack, IGun iGun, int amount){
        Optional<CommonGunIndex> commonGunIndexOptional = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack));
        if(commonGunIndexOptional.isPresent()){
            CommonGunIndex gunIndex = commonGunIndexOptional.get();
            int maxAmmon = gunIndex.getGunData().getAmmoAmount();
            iGun.useDummyAmmo(itemStack);
            if(amount - maxAmmon > 0) {
                iGun.setCurrentAmmoCount(itemStack,maxAmmon);
                int dummy = amount - maxAmmon;
                iGun.setMaxDummyAmmoAmount(itemStack,dummy);
                iGun.setDummyAmmoAmount(itemStack, dummy);
            }else{
                iGun.setCurrentAmmoCount(itemStack,amount);
                iGun.setDummyAmmoAmount(itemStack,0);
                iGun.setMaxDummyAmmoAmount(itemStack,0);
            }
        }
    }

    public static int getTotalDummyAmmo(ItemStack itemStack, IGun iGun){
        Optional<CommonGunIndex> commonGunIndexOptional = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack));
        if(commonGunIndexOptional.isPresent()){
            CommonGunIndex gunIndex = commonGunIndexOptional.get();
            int maxAmmon = gunIndex.getGunData().getAmmoAmount();
            int dummy = iGun.getMaxDummyAmmoAmount(itemStack);
            return maxAmmon + dummy;
        }
        return 0;
    }

    /**
     * use dummy ammo
     * */
    public static void fixGunItem( @NotNull ItemStack itemStack, @NotNull IGun iGun) {
        Optional<CommonGunIndex> gunIndexOptional = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack));
        if(gunIndexOptional.isPresent()){
            int maxAmmon = gunIndexOptional.get().getGunData().getAmmoAmount();
            iGun.setCurrentAmmoCount(itemStack,maxAmmon);
        }
        int maxAmmo = iGun.getMaxDummyAmmoAmount(itemStack);
        if(maxAmmo > 0) {
            iGun.useDummyAmmo(itemStack);
            iGun.setDummyAmmoAmount(itemStack,maxAmmo);
        }
    }

    /**
     * use dummy ammo
     * */
    public static void resetGunAmmo(ItemStack itemStack, IGun iGun){
        Optional<CommonGunIndex> commonGunIndexOptional = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack));
        if(commonGunIndexOptional.isPresent()){
            CommonGunIndex gunIndex = commonGunIndexOptional.get();
            int maxAmmon = gunIndex.getGunData().getAmmoAmount();
            iGun.setCurrentAmmoCount(itemStack,maxAmmon);
            iGun.useDummyAmmo(itemStack);
            iGun.setDummyAmmoAmount(itemStack,iGun.getMaxDummyAmmoAmount(itemStack));
        }
    }


    /**
    *  use dummy ammo
    * */
    public static void resetAllGunAmmo(@NotNull ServerPlayer serverPlayer){
        Inventory inventory = serverPlayer.getInventory();
        List<NonNullList<ItemStack>> compartments = ImmutableList.of(inventory.items, inventory.armor, inventory.offhand);
        compartments.forEach((itemList)-> itemList.forEach(itemStack -> {
            if(itemStack.getItem() instanceof IGun iGun){
                resetGunAmmo(itemStack,iGun);
            }
        }));
    }

}
