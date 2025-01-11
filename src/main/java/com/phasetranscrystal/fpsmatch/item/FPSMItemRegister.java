package com.phasetranscrystal.fpsmatch.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.entity.FlashBombEntity;
import com.phasetranscrystal.fpsmatch.test.TestItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class FPSMItemRegister {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FPSMatch.MODID);
    public static final RegistryObject<Item> TEST_ITEM = ITEMS.register("test_item", () -> new TestItem(new Item.Properties()));
    public static final RegistryObject<Item> C4 = ITEMS.register("c4", () -> new CompositionC4(new Item.Properties()));
    public static final RegistryObject<SmokeShell> SMOKE_SHELL = ITEMS.register("smoke_shell", () -> new SmokeShell(new Item.Properties()));
    public static final RegistryObject<BombDisposalKit> BOMB_DISPOSAL_KIT = ITEMS.register("bomb_disposal_kit", () -> new BombDisposalKit(new Item.Properties()));
    public static final RegistryObject<IncendiaryGrenade> INCENDIARY_GRENADE = ITEMS.register("incendiary_grenade", () -> new IncendiaryGrenade(new Item.Properties()));
    public static final RegistryObject<Grenade> GRENADE = ITEMS.register("grenade", () -> new Grenade(new Item.Properties()));
    public static final RegistryObject<FlashBomb> FLASH_BOMB = ITEMS.register("flash_bomb", () -> new FlashBomb(new Item.Properties()));
}
