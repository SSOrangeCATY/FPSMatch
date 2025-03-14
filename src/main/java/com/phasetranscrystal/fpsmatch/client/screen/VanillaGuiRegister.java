package com.phasetranscrystal.fpsmatch.client.screen;

import com.phasetranscrystal.fpsmatch.FPSMatch;


import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class VanillaGuiRegister {
    public static final DeferredRegister<MenuType<?>> CONTAINERS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, FPSMatch.MODID);
    public static final RegistryObject<MenuType<EditorShopContainer>> EDITOR_SHOP_CONTAINER =
            CONTAINERS.register("editor_shop_menu",
                    () -> IForgeMenuType.create((windowId, inv, buf) -> {
                        ItemStack stack = buf.readItem(); // 读取 ItemStack

                        // 从 ItemStack 获取 ItemStackHandler
                        ItemStackHandler handler = stack.getCapability(ForgeCapabilities.ITEM_HANDLER)
                                .filter(h -> h instanceof ItemStackHandler) // 确保是 ItemStackHandler
                                .map(h -> (ItemStackHandler) h) // 强制转换
                                .orElse(new ItemStackHandler(25)); // 默认 27 格存储

                        return new EditorShopContainer(windowId, inv, stack, handler);
                    })
            );

    public static final RegistryObject<MenuType<EditShopSlotMenu>> EDIT_SHOP_SLOT_MENU = CONTAINERS.register(
            "edit_shop_slot_menu", () -> IForgeMenuType.create(EditShopSlotMenu::new));

}
