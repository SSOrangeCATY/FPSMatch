package com.phasetranscrystal.fpsmatch.client.screen;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class EditorShopContainer extends AbstractContainerMenu {
    // 修改为 4行×5列
    private static final int ROWS = 4;
    private static final int COLS = 5;

    public EditorShopContainer(int containerId, Inventory playerInventory) {
        super(VanillaGuiRegister.EDITOR_SHOP_CONTAINER.get(), containerId);

        // 调整起始坐标，确保 5 列居中
        int slotAreaWidth = COLS * 18; // 5列总宽度 = 5*18 = 90
        int startX = (176 - slotAreaWidth) / 2; // 原版箱子宽度176，居中计算

        // 生成 4x5 槽位
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                this.addSlot(new Slot(playerInventory, col + row * COLS,
                        startX + col * 18,  // X坐标动态居中
                        18 + row * 18       // Y坐标与原版一致
                ));
            }
        }

        // 玩家物品栏位置微调（向下移动以腾出空间）
        addPlayerInventory(playerInventory, 8, 140 + 20);
    }
    private void addPlayerInventory(Inventory playerInventory, int x, int y) {
        // 玩家主物品栏
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        x + col * 18,
                        y + row * 18
                ));
            }
        }
        // 玩家快捷栏
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, x + col * 18, y + 58));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // 简化校验逻辑
    }

    @Override
    public ItemStack quickMoveStack(Player player, int i) {
        return null;
    }
}