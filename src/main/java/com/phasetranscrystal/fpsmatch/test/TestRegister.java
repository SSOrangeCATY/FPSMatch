package com.phasetranscrystal.fpsmatch.test;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class TestRegister {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FPSMatch.MODID);
    public static final RegistryObject<Item> TEST_ITEM = ITEMS.register("test_item", () -> new TestItem(new Item.Properties()));

}
