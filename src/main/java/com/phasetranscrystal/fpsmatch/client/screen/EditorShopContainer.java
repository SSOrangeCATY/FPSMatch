package com.phasetranscrystal.fpsmatch.client.screen;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import com.phasetranscrystal.fpsmatch.item.ShopEditTool;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;


import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

public class EditorShopContainer extends AbstractContainerMenu {
    private static final int SLOT_SIZE = 18;
    private static final int ROWS = 5;
    private static final int COLS = 5;
    private static final int d = 10; // 设定间隔
    private ItemStack guiItemStack; // 存储打开 GUI 的物品

    public EditorShopContainer(int containerId, Inventory playerInventory, ItemStack stack) {
        super(VanillaGuiRegister.EDITOR_SHOP_CONTAINER.get(), containerId);
        this.guiItemStack = stack;

        int startX = (176 - (COLS * (SLOT_SIZE + 4 * d))) / 2; // 居中于默认 GUI 宽度
        int startY = 18;

        // **创建 4×5 格子并加入间隔**
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                ItemStack slotItem = this.getAllSlots().get(col + row * COLS).process();
                this.addSlot(new Slot(
                                     playerInventory,
                                     col + row * COLS,
                                     startX + col * (SLOT_SIZE + 4 * d), // **加上间隔 d**
                                     startY + row * (SLOT_SIZE + d)  // **加上间隔 d**
                             )
//                             {
//                                 @Override
//                                 public boolean mayPlace(ItemStack stack) {
//                                     return false;
//                                 }
//
//                                 @Override
//                                 public boolean mayPickup(Player player) {
//                                     return false;
//                                 }
//                             }
                ).set(slotItem.isEmpty() ? ItemStack.EMPTY : slotItem)
                ;
            }
        }

        // **玩家物品栏（下移，避免与 GUI 重叠）**
        addPlayerInventory(playerInventory, 8, 160);
    }





    private void addPlayerInventory(Inventory playerInventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, x + col * 18, y + 58));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int i) {
        return ItemStack.EMPTY;
    }

    private FPSMShop getShop() {
        BaseMap map = FPSMCore.getInstance().getAllMaps().values().iterator().next().get(0);
        if (map instanceof ShopMap<?> shopMap) {
            if (guiItemStack.getItem() instanceof ShopEditTool shopEditTool) {
                return shopMap.getShop(shopEditTool.getTag(guiItemStack, ShopEditTool.SHOP_TAG));
            }
        }
        return null;
    }

    //按行展开
    public List<ShopSlot> getAllSlots() {
        //遍历 0 到 maxRow - 1 的索引，模拟逐行读取数据
        return IntStream.range(0,
                        Objects.requireNonNull(this.getShop()).getDefaultShopDataMap().values().stream()
                                .mapToInt(List::size)
                                .max().orElse(0))  // 获取最大行数
                //按列顺序遍历行
                .mapToObj(row -> this.getShop().getDefaultShopDataMap().entrySet().stream()//按列创建流
                        .sorted(Comparator.comparingInt(entry -> entry.getKey().typeIndex)) // 确保列顺序
                        .map(Map.Entry::getValue)
                        .filter(slotList -> row < slotList.size())  // 过滤掉短列 【遗留问题，是否存在占位符？】
                        .map(slotList -> slotList.get(row)))  // 取出当前行的元素
                .flatMap(Function.identity())  // 展开所有元素
                .toList();  // 转换成 List<ShopSlot>
    }

}
