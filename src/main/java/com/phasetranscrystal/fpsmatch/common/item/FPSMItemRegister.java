package com.phasetranscrystal.fpsmatch.common.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.sound.FPSMSoundRegister;
import com.phasetranscrystal.fpsmatch.common.entity.throwable.FlashBombEntity;
import com.phasetranscrystal.fpsmatch.common.entity.throwable.GrenadeEntity;
import com.phasetranscrystal.fpsmatch.common.entity.throwable.IncendiaryGrenadeEntity;
import com.phasetranscrystal.fpsmatch.common.entity.throwable.SmokeShellEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class FPSMItemRegister {
    public static final DeferredRegister<CreativeModeTab> TABS;
    public static RegistryObject<CreativeModeTab> FPSM_TAB;
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FPSMatch.MODID);
    public static final RegistryObject<BaseThrowAbleItem> SMOKE_SHELL = ITEMS.register("smoke_shell",
            () -> new BaseThrowAbleItem(new Item.Properties(), SmokeShellEntity::new , FPSMSoundRegister.VOICE_SMOKE::get));
    public static final RegistryObject<BaseThrowAbleItem> CT_INCENDIARY_GRENADE = ITEMS.register("ct_incendiary_grenade",
            () -> new BaseThrowAbleItem(new Item.Properties(),
            (player,level)-> new IncendiaryGrenadeEntity(player,level,3,FPSMItemRegister.CT_INCENDIARY_GRENADE::get)));
    public static final RegistryObject<BaseThrowAbleItem> T_INCENDIARY_GRENADE = ITEMS.register("t_incendiary_grenade",
            () -> new BaseThrowAbleItem(new Item.Properties(),
            (player,level)-> new IncendiaryGrenadeEntity(player,level,4,FPSMItemRegister.T_INCENDIARY_GRENADE::get)));
    public static final RegistryObject<BaseThrowAbleItem> GRENADE = ITEMS.register("grenade",
            () -> new BaseThrowAbleItem(new Item.Properties(), GrenadeEntity::new, FPSMSoundRegister.VOICE_GRENADE::get));
    public static final RegistryObject<BaseThrowAbleItem> FLASH_BOMB = ITEMS.register("flash_bomb",
            () -> new BaseThrowAbleItem(new Item.Properties(), FlashBombEntity::new, FPSMSoundRegister.VOICE_FLASH::get));
    public static final RegistryObject<Item> SHOP_EDIT_TOOL = ITEMS.register("shop_edit_tool",
            () -> new ShopEditTool(new Item.Properties()));
    public static final RegistryObject<Item> BULLETPROOF_ARMOR = ITEMS.register("bulletproof_armor", () -> new BulletproofArmor(new Item.Properties(),false));
    public static final RegistryObject<Item> BULLETPROOF_WITH_HELMET = ITEMS.register("bulletproof_with_helmet", () -> new BulletproofArmor(new Item.Properties(),true));

    static {
        TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "fpsmatch");
        FPSM_TAB = TABS.register("other", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.tab.fpsm"))
                .icon(() -> T_INCENDIARY_GRENADE.get().getDefaultInstance()).displayItems((parameters, output) -> {
            ITEMS.getEntries().forEach((entry) -> {
                output.accept(entry.get());
            });
        }).build());
    }
}
