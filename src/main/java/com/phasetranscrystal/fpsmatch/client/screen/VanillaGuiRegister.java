package com.phasetranscrystal.fpsmatch.client.screen;

import com.phasetranscrystal.fpsmatch.FPSMatch;


import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.common.extensions.IForgeMenuType;
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
                        return new EditorShopContainer(windowId, inv, stack);
                    })
            );

    public static final RegistryObject<MenuType<EditShopSlotMenu>> EDIT_SHOP_SLOT_MENU = CONTAINERS.register(
            "edit_shop_slot_menu", () -> IForgeMenuType.create(EditShopSlotMenu::new));

    public static void register(){
        MenuScreens.register(EDITOR_SHOP_CONTAINER.get(), EditorShopScreen::new);
        MenuScreens.register(EDIT_SHOP_SLOT_MENU.get(), EditShopSlotScreen::new);
    }
}
