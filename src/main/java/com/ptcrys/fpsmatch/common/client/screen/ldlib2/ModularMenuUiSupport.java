package com.ptcrys.fpsmatch.common.client.screen.ldlib2;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/** Bridges LDLib2 ModularUI with existing AbstractContainerMenu slot graphs. */
public final class ModularMenuUiSupport {
    private ModularMenuUiSupport() {
    }

    public static void attach(ModularUI modularUI, AbstractContainerMenu menu) {
        IModularUIHolderMenu holder = (IModularUIHolderMenu) menu;
        holder.setModularUI(modularUI);
        for (ItemSlot itemSlot : modularUI.getElementsByType(ItemSlot.class)) {
            Slot slot = itemSlot.getSlot();
            if (slot != null) {
                holder.ldlib2$addSlot(itemSlot);
            }
        }
    }

    public static void syncSlotPositions(AbstractContainerMenu menu) {
        IModularUIHolderMenu holder = (IModularUIHolderMenu) menu;
        for (Slot slot : menu.slots) {
            ItemSlot itemSlot = holder.getItemSlot(slot);
            if (itemSlot != null) {
                itemSlot.updateSlotPosition();
            }
        }
    }
}
