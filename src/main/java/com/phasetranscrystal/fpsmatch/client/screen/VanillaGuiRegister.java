package com.phasetranscrystal.fpsmatch.client.screen;

import com.phasetranscrystal.fpsmatch.FPSMatch;


import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class VanillaGuiRegister {
    public static final DeferredRegister<MenuType<?>> CONTAINERS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, FPSMatch.MODID);
    public static final RegistryObject<MenuType<EditorShopContainer>> EDITOR_SHOP_CONTAINER =
            CONTAINERS.register("editor_shop_menu",
                    () -> IForgeMenuType.create((windowId, inv, buf) ->
                            new EditorShopContainer(windowId, inv, buf.readItem()))
            );
    public static final RegistryObject<MenuType<EditShopSlotMenu>> EDIT_SHOP_SLOT_MENU = CONTAINERS.register(
            "edit_shop_slot_menu", () -> IForgeMenuType.create(EditShopSlotMenu::new));

}
