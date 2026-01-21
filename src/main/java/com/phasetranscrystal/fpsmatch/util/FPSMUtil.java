package com.phasetranscrystal.fpsmatch.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.mojang.authlib.GameProfile;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.drop.DropType;
import com.phasetranscrystal.fpsmatch.common.entity.MatchDropEntity;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMInventorySelectedS2CPacket;
import com.phasetranscrystal.fpsmatch.common.sound.FPSMSoundRegister;
import com.phasetranscrystal.fpsmatch.compat.CounterStrikeGrenadesCompat;
import com.phasetranscrystal.fpsmatch.compat.LrtacticalCompat;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.item.BlastBombItem;
import com.phasetranscrystal.fpsmatch.core.item.IThrowEntityAble;
import com.phasetranscrystal.fpsmatch.common.gamerule.FPSMatchRule;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.compat.impl.FPSMImpl;
import com.phasetranscrystal.fpsmatch.core.team.MapTeams;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import me.xjqsh.lrtactical.entity.ThrowableItemEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;

public class FPSMUtil {
    public static final List<GunTabType> MAIN_WEAPON = ImmutableList.of(GunTabType.RIFLE,GunTabType.SNIPER,GunTabType.SHOTGUN,GunTabType.SMG,GunTabType.MG);
    public static final List<Predicate<ItemStack>> MAIN_WEAPON_PREDICATE = new ArrayList<>();
    public static final List<Predicate<ItemStack>> SECONDARY_WEAPON_PREDICATE = new ArrayList<>();
    public static final List<Predicate<ItemStack>> THIRD_WEAPON_PREDICATE = new ArrayList<>();
    public static final List<Predicate<ItemStack>> THROW_PREDICATE = new ArrayList<>();
    public static final List<Predicate<ItemStack>> C4_PREDICATE = new ArrayList<>();
    public static final List<Predicate<ItemStack>> MISC_PREDICATE = new ArrayList<>();

    static{
        addMainWeaponPredicate((itemStack -> {
            if(itemStack.getItem() instanceof IGun gun){
                return isMainWeapon(gun.getGunId(itemStack));
            }else{
                return false;
            }
        }));

        addSecondaryWeaponPredicate((itemStack -> {
            if(itemStack.getItem() instanceof IGun gun){
                return getGunTypeByGunId(gun.getGunId(itemStack)).filter(gunTabType -> gunTabType == GunTabType.PISTOL).isPresent();
            }else{
                return false;
            }
        }));

        addThrowablePredicate((itemStack -> itemStack.getItem() instanceof IThrowEntityAble));
        addThrowablePredicate((itemStack -> {
            if (FPSMImpl.findLrtacticalMod()){
                try{
                    return itemStack.getItem() instanceof me.xjqsh.lrtactical.api.item.IThrowable;
                }catch (Exception e){
                    return false;
                }
            }else{
                return false;
            }
        }));
        addThrowablePredicate((itemStack -> {
            if (FPSMImpl.findCounterStrikeGrenadesMod()){
                try{
                    return itemStack.getItem() instanceof club.pisquad.minecraft.csgrenades.item.CounterStrikeGrenadeItem;
                }catch (Exception e){
                    return false;
                }
            }else{
                return false;
            }
        }));

        addThirdWeaponPredicate((itemStack -> {
            if(itemStack.getItem() instanceof IGun gun){
                return getGunTypeByGunId(gun.getGunId(itemStack)).filter(gunTabType -> gunTabType == GunTabType.RPG).isPresent();
            }else{
                if (FPSMImpl.findLrtacticalMod()){
                    try{
                        return itemStack.getItem() instanceof me.xjqsh.lrtactical.api.item.IMeleeWeapon;
                    }catch (Exception e){
                        return false;
                    }
                }else{
                    return false;
                }
            }
        }));

        addC4Predicate((itemStack -> itemStack.getItem() instanceof BlastBombItem));

        MISC_PREDICATE.add((itemStack -> true));
    }

    public static Optional<GunTabType> getGunTypeByGunId(ResourceLocation gunId){
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(commonGunIndex -> GunTabType.valueOf(commonGunIndex.getType().toUpperCase(Locale.US)));
    }

    public static boolean isMainWeapon(ResourceLocation gunId){
        return getGunTypeByGunId(gunId).filter(MAIN_WEAPON::contains).isPresent();
    }

    public static boolean sortPlayerInventory(ServerPlayer player) {
        if (player.level().getGameRules().getRule(FPSMatchRule.RULE_AUTO_SORT_PLAYER_INV).get()) {
            Inventory inventory = player.getInventory();

            List<ItemStack> allItems = new ArrayList<>();
            for (int i = 0; i < inventory.items.size(); i++) {
                ItemStack stack = inventory.items.get(i);
                if (!stack.isEmpty()) {
                    allItems.add(stack.copy());
                }
                inventory.items.set(i, ItemStack.EMPTY);
            }

            Map<List<Predicate<ItemStack>>, List<ItemStack>> categoryMap = new LinkedHashMap<>();

            categoryMap.put(MAIN_WEAPON_PREDICATE, new ArrayList<>());
            categoryMap.put(SECONDARY_WEAPON_PREDICATE, new ArrayList<>());
            categoryMap.put(THIRD_WEAPON_PREDICATE, new ArrayList<>());
            categoryMap.put(C4_PREDICATE, new ArrayList<>());
            categoryMap.put(THROW_PREDICATE, new ArrayList<>());
            categoryMap.put(MISC_PREDICATE, new ArrayList<>());

            for (ItemStack stack : allItems) {
                categorized: {
                    for (Map.Entry<List<Predicate<ItemStack>>, List<ItemStack>> entry : categoryMap.entrySet()) {
                        for (Predicate<ItemStack> predicate : entry.getKey()) {
                            if (predicate.test(stack)) {
                                entry.getValue().add(stack);
                                break categorized;
                            }
                        }
                    }
                }
            }

            List<ItemStack> mainWeapons = mergeItemStacks(categoryMap.get(MAIN_WEAPON_PREDICATE));
            List<ItemStack> secondaryWeapons = mergeItemStacks(categoryMap.get(SECONDARY_WEAPON_PREDICATE));
            List<ItemStack> thirdWeapons = mergeItemStacks(categoryMap.get(THIRD_WEAPON_PREDICATE));
            List<ItemStack> c4Items = mergeItemStacks(categoryMap.get(C4_PREDICATE));
            List<ItemStack> throwable = mergeItemStacks(categoryMap.get(THROW_PREDICATE));
            List<ItemStack> miscItems = mergeItemStacks(categoryMap.get(MISC_PREDICATE));

            throwable.sort(Comparator.comparing(stack ->
                    stack.getHoverName().getString().toLowerCase()
            ));

            Map<Integer, ItemStack> sortedSlots = new HashMap<>();
            List<ItemStack> remainingItems = new ArrayList<>();

            boolean flag1 = assignToSlot(sortedSlots, mainWeapons, 0);
            boolean flag2 = assignToSlot(sortedSlots, secondaryWeapons, 1);
            boolean flag3 = assignToSlot(sortedSlots, thirdWeapons, 2);
            boolean flag4 = assignToSlot(sortedSlots, c4Items, 3);
            boolean flag5 = assignToSlot(sortedSlots, miscItems, 8);

            if (!throwable.isEmpty()) {
                int throwableSlot = 4;
                for (ItemStack t : throwable) {
                    if (throwableSlot > 7) {
                        remainingItems.add(t);
                    } else {
                        sortedSlots.put(throwableSlot++, t);
                    }
                }
            }

            remainingItems.addAll(mainWeapons);
            remainingItems.addAll(secondaryWeapons);
            remainingItems.addAll(thirdWeapons);
            remainingItems.addAll(c4Items);
            remainingItems.addAll(miscItems);

            for (int i = 0; i < inventory.items.size(); i++) {
                if (sortedSlots.containsKey(i)) {
                    inventory.items.set(i, sortedSlots.get(i));
                } else if (i >= 9 && !remainingItems.isEmpty()) {
                    inventory.items.set(i, remainingItems.remove(0));
                }
            }

            int carriedSlot = inventory.selected;

            if (inventory.items.get(carriedSlot).isEmpty()) {
                int direction = 1;
                int currentSlot = carriedSlot;
                int attempts = 0;

                while (attempts < 18) {
                    currentSlot += direction;

                    if (currentSlot > 8) {
                        direction = -1;
                        currentSlot = 8;
                    } else if (currentSlot < 0) {
                        direction = 1;
                        currentSlot = 0;
                    }

                    if (!inventory.items.get(currentSlot).isEmpty()) {
                        carriedSlot = currentSlot;
                        break;
                    }

                    attempts++;
                }
            }

            FPSMatch.sendToPlayer(player, new FPSMInventorySelectedS2CPacket(carriedSlot));

            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            return true;
        } else {
            return false;
        }
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
    private static boolean assignToSlot(Map<Integer, ItemStack> map, List<ItemStack> items, int slot) {
        if (!items.isEmpty()) {
            map.put(slot, items.remove(0));
            return true;
        }
        return false;
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

    public static void setDummyAmmo(ItemStack itemStack, IGun iGun, int amount){
        TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack)).ifPresent(index -> {
            iGun.useDummyAmmo(itemStack);
            iGun.setMaxDummyAmmoAmount(itemStack, amount);
            iGun.setDummyAmmoAmount(itemStack, amount);
            iGun.setCurrentAmmoCount(itemStack, index.getGunData().getAmmoAmount());
        });
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

    public static ItemStack fixGunItem(@NotNull ItemStack itemStack) {
        if(itemStack.getItem() instanceof IGun iGun){
            fixGunItem(itemStack,iGun);
            return itemStack;
        }
        return itemStack;
    }
        /**
         * use dummy ammo
         * */
    public static void fixGunItem(@NotNull ItemStack itemStack, @NotNull IGun iGun) {
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

    public static void addMainWeaponPredicate(Predicate<ItemStack> predicate){
        MAIN_WEAPON_PREDICATE.add(predicate);
    }
    public static void addSecondaryWeaponPredicate(Predicate<ItemStack> predicate){
        SECONDARY_WEAPON_PREDICATE.add(predicate);
    }
    public static void addThirdWeaponPredicate(Predicate<ItemStack> predicate){
        THIRD_WEAPON_PREDICATE.add(predicate);
    }
    public static void addThrowablePredicate(Predicate<ItemStack> predicate){
        THROW_PREDICATE.add(predicate);
    }
    public static void addC4Predicate(Predicate<ItemStack> predicate){
        C4_PREDICATE.add(predicate);
    }

    public static void playerDropMatchItem(ServerPlayer player, ItemStack itemStack){
        RandomSource random = player.getRandom();
        DropType type = DropType.getItemDropType(itemStack);
        MatchDropEntity dropEntity = new MatchDropEntity(player.level(),itemStack,type);
        double d0 = player.getEyeY() - (double)0.3F;
        Vec3 pos = new Vec3(player.getX(), d0, player.getZ());
        dropEntity.setPos(pos);
        float f8 = Mth.sin(player.getXRot() * ((float)Math.PI / 180F));
        float f2 = Mth.cos(player.getXRot() * ((float)Math.PI / 180F));
        float f3 = Mth.sin(player.getYRot() * ((float)Math.PI / 180F));
        float f4 = Mth.cos(player.getYRot() * ((float)Math.PI / 180F));
        float f5 = random.nextFloat() * ((float)Math.PI * 2F);
        float f6 = 0.02F * random.nextFloat();
        dropEntity.setDeltaMovement((double)(-f3 * f2 * 0.3F) + Math.cos(f5) * (double)f6, -f8 * 0.3F + 0.1F + (random.nextFloat() - random.nextFloat()) * 0.1F, (double)(f4 * f2 * 0.3F) + Math.sin(f5) * (double)f6);
        player.level().addFreshEntity(dropEntity);
    }

    public static void playerDeadDropWeapon(ServerPlayer serverPlayer, boolean dropThrowable) {
        Optional<BaseMap> map = FPSMCore.getInstance().getMapByPlayer(serverPlayer);

        map.flatMap(baseMap -> baseMap.getMapTeams().getTeamByPlayer(serverPlayer))
                .ifPresent(team -> {
                    ItemStack weapon = findWeaponToDrop(serverPlayer);

                    if (!weapon.isEmpty()) {
                        playerDropMatchItem(serverPlayer, weapon);
                    }

                    if(!dropThrowable) return;
                    Inventory inventory = serverPlayer.getInventory();
                    List<ItemStack> throwable = searchInventoryForType(inventory, DropType.THROW);
                    if (!throwable.isEmpty()) {
                        playerDropMatchItem(serverPlayer, throwable.get(0));
                    }
                });
    }

    /**
     * 查找要掉落的主要武器
     */
    public static ItemStack findWeaponToDrop(ServerPlayer serverPlayer) {
        Inventory inventory = serverPlayer.getInventory();

        // 按优先级搜索所有物品栏
        for (DropType type : DropType.values()) {
            if (type == DropType.MISC || type == DropType.THIRD_WEAPON || type == DropType.THROW) {
                continue;
            }

            List<ItemStack> foundWeapon = searchInventoryForType(inventory, type);
            if (!foundWeapon.isEmpty()) {
                return foundWeapon.get(0);
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * 在物品栏中搜索指定类型的物品
     */
    public static List<ItemStack> searchInventoryForType(Inventory inventory, DropType... type) {
        List<List<ItemStack>> inventorySections = Arrays.asList(
                inventory.items,    // 主物品栏
                inventory.armor,    // 装备栏
                inventory.offhand   // 副手
        );

        ArrayList<ItemStack> foundItems = new ArrayList<>();
        for (List<ItemStack> section : inventorySections) {
            for (ItemStack stack : section) {
                for (DropType dropType : type) {
                    if (!stack.isEmpty() && dropType.itemMatch().test(stack)) {
                        foundItems.add(stack);
                    }
                }
            }
        }

        return foundItems;
    }

    public static double linearInterpolate(double start, double end, double factor) {
        return start + (end - start) * factor;
    }

    /**
     * 将物品添加到玩家库存
     */
    public static void addItemToPlayerInventory(ServerPlayer player, ItemStack itemStack) {
        if(itemStack.getItem() instanceof IGun iGun){
            Optional<GunTabType> type = FPSMUtil.getGunTypeByGunId(iGun.getGunId(itemStack));
            type.ifPresent(t->{
                player.level().playSound(player,player.getOnPos(), FPSMSoundRegister.getGunBoughtSound(t),player.getSoundSource(),1,1);
            });
        }else{
            SoundEvent sound;
            if(FPSMImpl.findLrtacticalMod() && LrtacticalCompat.isKnife(itemStack.getItem())){
                sound = FPSMSoundRegister.getKnifeBoughtSound();
            }else{
                sound = FPSMSoundRegister.getItemBoughtSound(itemStack.getItem());
            }
            player.level().playSound(player,player.getOnPos(), sound, player.getSoundSource(),1,1);
        }

        if (itemStack.getItem() instanceof ArmorItem armorItem) {
            player.setItemSlot(armorItem.getEquipmentSlot(), itemStack);
        } else {
            player.getInventory().add(itemStack);
            FPSMUtil.sortPlayerInventory(player);
        }
    }

    /**
     * 获取玩家所有物品(包括装备和副手)
     */
    public static Iterable<ItemStack> getAllPlayerItems(ServerPlayer player) {
        return Iterables.concat(
                player.getInventory().items,
                player.getInventory().armor,
                player.getInventory().offhand
        );
    }




    /**
     * 从伤害源中中获取击杀者
     * */
    public static ServerPlayer getKiller(ServerPlayer dead, DamageSource source) {
        Entity src = source.getEntity();
        if (src instanceof ServerPlayer sp) return sp;
        Entity direct = source.getDirectEntity();
        if (direct instanceof ServerPlayer dsp) return dsp;

        if (direct instanceof Projectile proj && proj.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        if (direct instanceof ThrowableItemProjectile tip && tip.getOwner() instanceof ServerPlayer owner2){
            return owner2;
        }
        if (direct instanceof AreaEffectCloud cloud && cloud.getOwner() instanceof ServerPlayer owner3){
            return owner3;
        }
        if (direct instanceof PrimedTnt tnt && tnt.getOwner() instanceof ServerPlayer owner4){
            return owner4;
        }

        Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(dead);
        if(opt.isPresent()){
            BaseMap baseMap = opt.get();
            Map<UUID, Float> hurtMap = baseMap.getMapTeams().getDamageMap().get(dead.getUUID());
            return hurtMap.entrySet().stream()
                    .max(Comparator.comparingDouble(Map.Entry::getValue))
                    .flatMap(e -> FPSMCore.getInstance().getPlayerByUUID(e.getKey()))
                    .orElse(null);
        }
        return null;
    }

    /**
     * 从伤害源中中获取击杀者的武器
     * */
    public static ItemStack getKillerWeapon(DamageSource source){
        Player attacker;
        if (source.getEntity() instanceof Player p) {
            attacker = p;
        } else if (source.getEntity() instanceof ThrowableItemProjectile throwable
                && throwable.getOwner() instanceof Player p) {
            attacker = p;
        } else {
            attacker = null;
        }

        ItemStack itemStack;
        if (source.getDirectEntity() instanceof ThrowableItemProjectile projectile) {
            itemStack = projectile.getItem();
        } else if (FPSMImpl.findCounterStrikeGrenadesMod()) {
            itemStack = CounterStrikeGrenadesCompat.getItemFromDamageSource(source);
        }else{
            itemStack = ItemStack.EMPTY;
        }

        return (itemStack.isEmpty() && attacker != null) ? attacker.getMainHandItem() : itemStack;
    }

    public static ResourceLocation fetchSkin(UUID id, String name){
        return Minecraft.getInstance().getSkinManager()
                .getInsecureSkinLocation(new GameProfile(id, name));
    }

    /**
     * 检查实体是否为追踪实体并返回其拥有者
     */
    public static ServerPlayer getOwnerIfTraceable(Entity... entities) {
        for (Entity entity : entities) {
            if (entity instanceof TraceableEntity traceable) {
                Entity owner = traceable.getOwner();
                if (owner instanceof ServerPlayer serverPlayer) {
                    return serverPlayer;
                }
            }
        }
        return null;
    }

    /**
     * 计算助攻玩家（符合伤害阈值的首个助攻者）
     *
     * @param deadPlayer 死亡玩家
     * @return 助攻玩家数据（可能为空）
     */
    public static Optional<PlayerData> calculateAssistPlayer(BaseMap map, ServerPlayer deadPlayer, float minAssistDamageRatio) {
        MapTeams mapTeams = map.getMapTeams();

        if (mapTeams.getTeamByPlayer(deadPlayer).isEmpty()) return Optional.empty();

        Map<UUID, Float> hurtDataMap = mapTeams.getDamageMap().getOrDefault(deadPlayer.getUUID(), null);
        if (hurtDataMap == null || hurtDataMap.isEmpty()) {
            return Optional.empty();
        }

        float minAssistDamage = deadPlayer.getMaxHealth() * minAssistDamageRatio;
        return hurtDataMap.entrySet().stream()
                .filter(entry -> entry.getValue() > minAssistDamage)
                .sorted(Map.Entry.<UUID, Float>comparingByValue().reversed())
                .limit(1)
                .findAny()
                .flatMap(entry -> mapTeams.getTeamByPlayer(entry.getKey())
                        .flatMap(team -> team.getPlayerData(entry.getKey())));
    }
}
