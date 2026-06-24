package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.phasetranscrystal.fpsmatch.FPSMatch;


import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class VanillaGuiRegister {
    public static final DeferredRegister<MenuType<?>> CONTAINERS =
            DeferredRegister.create(Registries.MENU, FPSMatch.MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<EditorShopContainer>> EDITOR_SHOP_CONTAINER =
            CONTAINERS.register("editor_shop_menu",
                    () -> IMenuTypeExtension.create(EditorShopContainer::new)
            );

    public static final DeferredHolder<MenuType<?>, MenuType<EditShopSlotMenu>> EDIT_SHOP_SLOT_MENU = CONTAINERS.register(
            "edit_shop_slot_menu", () -> IMenuTypeExtension.create(EditShopSlotMenu::new));

    public static void register(RegisterMenuScreensEvent event){
        event.register(EDITOR_SHOP_CONTAINER.get(), EditorShopScreen::new);
        event.register(EDIT_SHOP_SLOT_MENU.get(), EditShopSlotScreen::new);
    }
}
