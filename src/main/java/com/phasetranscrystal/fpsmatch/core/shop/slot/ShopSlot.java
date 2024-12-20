package com.phasetranscrystal.fpsmatch.core.shop.slot;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.shop.event.CheckCostEvent;
import com.phasetranscrystal.fpsmatch.core.shop.event.ShopSlotChangeEvent;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ListenerModule;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ShopSlot{
    public static final Codec<ShopSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("ItemStack").forGetter(ShopSlot::process),
            Codec.INT.fieldOf("defaultCost").forGetter(ShopSlot::getDefaultCost),
            Codec.INT.fieldOf("maxBuyCount").forGetter(ShopSlot::getMaxBuyCount),
            Codec.INT.fieldOf("groupId").forGetter(ShopSlot::getGroupId),
            Codec.list(Codec.STRING).fieldOf("listenerModule").forGetter(ShopSlot::getListenerNames)
    ).apply(instance, (itemstack,dC,mBC,gId,fL) -> {
        ShopSlot shopSlot = new ShopSlot(itemstack,dC,mBC,gId);
        fL.forEach(name->{
            ListenerModule lm = FPSMatch.listenerModuleManager.getListenerModule(name);
            if(lm != null){
                shopSlot.addListener(lm);
            }else{
                System.out.println("error : couldn't find listener module by -> " + name);
            }
        });
        return shopSlot;
    }));

    // 物品供应器，用于提供物品栈
    public final Supplier<ItemStack> itemSupplier;
    // 返回检查器，用于检查物品栈是否可以返回
    public final Predicate<ItemStack> returningChecker;
    // 默认价格
    public final int defaultCost;
    // 当前价格
    private int cost;
    // 组ID
    private int groupId = -1;
    // 已购买数量
    private int boughtCount = 0;
    // 最大购买数量
    private int maxBuyCount = 1;
    // 是否锁定
    private boolean locked = false;
    // 索引
    private int index = -1;
    private final ArrayList<ListenerModule> listener = new ArrayList<>();

    /**
     * 获取当前价格
     * @return 当前价格
     */
    public int getCost() {
        return cost;
    }

    public int getDefaultCost() {
        return defaultCost;
    }

    /**
     * 获取组ID
     * @return 组ID
     */
    public int getGroupId() {
        return groupId;
    }

    /**
     * 获取已购买数量
     * @return 已购买数量
     */
    public int getBoughtCount() {
        return boughtCount;
    }

    /**
     * 获取最大购买数量
     * @return 最大购买数量
     */
    public int getMaxBuyCount() {
        return maxBuyCount;
    }

    /**
     * 判断是否锁定
     * @return 是否锁定
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * 处理物品，返回一个新的物品栈
     * @return 新的物品栈
     */
    public ItemStack process() {
        return itemSupplier.get();
    }

    /**
     * 重置当前价格为默认价格
     */
    public void resetCost() {
        cost = defaultCost;
    }

    /**
     * 设置为锁定状态
     */
    public void lock() {
        locked = true;
    }

    public void lock(int boughtCount) {
        locked = true;
        this.boughtCount = Math.min(this.getMaxBuyCount(),boughtCount);
    }


    /**
     * 设置为非锁定状态
     */
    public void unlock() {
        locked = false;
    }

    public void unlock(int count) {
        this.boughtCount -= Math.max(this.boughtCount,count);
        if(boughtCount == 0){
            this.unlock();
        }
    }

    /**
     * 设置最大购买数量
     * @param maxBuyCount 最大购买数量
     */
    public void setMaxBuyCount(int maxBuyCount) {
        this.maxBuyCount = maxBuyCount;
    }

    /**
     * 判断是否有组
     * @return 是否有组
     */
    public boolean haveGroup(){
        return groupId >= 0;
    }

    /**
     * 设置索引
     * @param index 索引
     */
    public void setIndex(int index){
        if(this.index < 0){
            this.index = index;
        }
    }

    /**
     * 获取索引
     * @return 索引
     */
    public int getIndex(){
        return index;
    }

    /**
     * 构造函数，用于创建一个新的物品槽位
     * @param itemStack 物品栈
     * @param defaultCost 默认价格
     */
    public ShopSlot(ItemStack itemStack, int defaultCost) {
        this.itemSupplier = itemStack::copy;
        this.defaultCost = defaultCost;
        this.cost = defaultCost;
        this.returningChecker = getDefaultChecker();
    }

    /**
     * 构造函数，用于创建一个新的物品槽位，并设置最大购买数量
     * @param itemStack 物品栈
     * @param defaultCost 默认价格
     * @param maxBuyCount 最大购买数量
     */
    public ShopSlot(ItemStack itemStack, int defaultCost, int maxBuyCount) {
        this(itemStack, defaultCost);
        this.maxBuyCount = maxBuyCount;
    }

    /**
     * 构造函数，用于创建一个新的物品槽位，并设置组ID和返回检查器
     * @param itemStack 物品
     * @param defaultCost 默认价格
     * @param maxBuyCount 最大购买数量
     * @param groupId 组ID
     */
    public ShopSlot(ItemStack itemStack, int defaultCost, int maxBuyCount, int groupId) {
        this(itemStack,defaultCost,maxBuyCount);
        this.groupId = groupId;
    }

    /**
     * 构造函数，用于创建一个新的物品槽位，并设置组ID和返回检查器
     * @param supplier 物品供应器
     * @param defaultCost 默认价格
     * @param maxBuyCount 最大购买数量
     * @param groupId 组ID
     * @param checker 退款检查器
     */
    public ShopSlot(Supplier<ItemStack> supplier, int defaultCost, int maxBuyCount, int groupId, Predicate<ItemStack> checker) {
        this.itemSupplier = supplier;
        this.defaultCost = defaultCost;
        this.cost = defaultCost;
        this.maxBuyCount = maxBuyCount;
        this.groupId = groupId;
        this.returningChecker = checker;
    }

    /**
     * 判断是否可以购买
     * @param money 当前金钱
     * @return 是否可以购买
     */
    public boolean canBuy(int money) {
        return money >= cost && boughtCount < maxBuyCount;
    }

    /**
     * 判断是否可以返回
     * @return 是否可以返回
     */
    public boolean canReturn(Player player) {
        if(player.getInventory().clearOrCountMatchingItems(this.returningChecker,0,player.inventoryMenu.getCraftSlots()) > 0){
            return boughtCount > 0 && !locked;
        }else{
            return false;
        }
    }

    public Predicate<ItemStack> getDefaultChecker(){
        return (itemStack)->{
            ItemStack shopItem = this.process();
            if(itemStack.getItem() instanceof IGun iGun){
                ResourceLocation gunId = iGun.getGunId(itemStack);
                return shopItem.getItem() instanceof IGun shopGun && gunId.equals(shopGun.getGunId(shopItem));
            }else {
                return itemStack.getDisplayName().getString().equals(shopItem.getDisplayName().getString()) && shopItem.getItem() == itemStack.getItem();
            }
        };
    }

    /**
     * 重置物品槽位
     */
    public void reset() {
        cost = defaultCost;
        boughtCount = 0;
        locked = false;
    }

    /**
     * 当组内物品槽位发生变化时调用
     */
    public final void onGroupSlotChanged(ShopSlotChangeEvent event) {
        if(!this.listener.isEmpty()){
            listener.forEach(listenerModule -> {
                listenerModule.handle(event);
            });
        }
    }

    public void handleCheckCostEvent(CheckCostEvent event){
        if(this.canReturn(event.player()) && getListenerNames().contains("returnGoods")){
            event.addCost(this.getCost());
        }
    }

    public void addListener(ListenerModule listener) {
        this.listener.add(listener);
    }
    public List<String> getListenerNames(){
        List<String> names = new ArrayList<>();
        this.listener.forEach(listenerModule -> {
            names.add(listenerModule.getName());
        });
        return names;
    }

    /**
     * 购买物品 不要直接使用！从ShopData层判定与调用
     * @param player 玩家
     * @param money 当前金钱
     * @return 购买后剩余金钱
     */
    public int buy(Player player, int money) {
        boughtCount++;
        player.getInventory().add(process());
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return money - cost;
    }

    //同上
    /**
     * 返回物品
     * @param player 玩家
     * @return 返回后剩余物品数量
     */
    public int returnItem(Player player) {
        return returnItem(player, 1);
    }

    /**
     * 返回指定数量的物品
     * @param player 玩家
     * @param count 返回数量
     * @return 返回后剩余物品数量
     */
    public int returnItem(Player player, int count) {
        count = Math.min(boughtCount, count);
        player.getInventory().clearOrCountMatchingItems(returningChecker, count, player.inventoryMenu.getCraftSlots());
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        boughtCount -= count;
        return count;
    }


}
