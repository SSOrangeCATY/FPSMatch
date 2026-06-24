package com.phasetranscrystal.fpsmatch.common.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.sound.FPSMSoundRegister;
import com.phasetranscrystal.fpsmatch.common.entity.throwable.FlashBombEntity;
import com.phasetranscrystal.fpsmatch.common.entity.throwable.GrenadeEntity;
import com.phasetranscrystal.fpsmatch.common.entity.throwable.IncendiaryGrenadeEntity;
import com.phasetranscrystal.fpsmatch.common.entity.throwable.SmokeShellEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class FPSMItemRegister {
    public static final DeferredRegister<CreativeModeTab> TABS;
    public static DeferredHolder<CreativeModeTab, CreativeModeTab> FPSM_TAB;
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FPSMatch.MODID);
    public static final DeferredItem<BaseThrowAbleItem> SMOKE_SHELL = ITEMS.registerItem("smoke_shell",
            properties -> new BaseThrowAbleItem(properties.stacksTo(1), SmokeShellEntity::new , FPSMSoundRegister.VOICE_SMOKE::get));
    public static final DeferredItem<BaseThrowAbleItem> CT_INCENDIARY_GRENADE = ITEMS.registerItem("ct_incendiary_grenade",
            properties -> new BaseThrowAbleItem(properties.stacksTo(1),
            (player,level)-> new IncendiaryGrenadeEntity(player,level,3,FPSMItemRegister.CT_INCENDIARY_GRENADE::get)));
    public static final DeferredItem<BaseThrowAbleItem> T_INCENDIARY_GRENADE = ITEMS.registerItem("t_incendiary_grenade",
            properties -> new BaseThrowAbleItem(properties.stacksTo(1),
            (player,level)-> new IncendiaryGrenadeEntity(player,level,4,FPSMItemRegister.T_INCENDIARY_GRENADE::get)));
    public static final DeferredItem<BaseThrowAbleItem> GRENADE = ITEMS.registerItem("grenade",
            properties -> new BaseThrowAbleItem(properties.stacksTo(1), GrenadeEntity::new, FPSMSoundRegister.VOICE_GRENADE::get));
    public static final DeferredItem<BaseThrowAbleItem> FLASH_BOMB = ITEMS.registerItem("flash_bomb",
            properties -> new BaseThrowAbleItem(properties.stacksTo(1), FlashBombEntity::new, FPSMSoundRegister.VOICE_FLASH::get));
    public static final DeferredItem<Item> BULLETPROOF_ARMOR = ITEMS.registerItem("bulletproof_armor", properties -> new BulletproofArmor(properties.stacksTo(1),false));
    public static final DeferredItem<Item> BULLETPROOF_WITH_HELMET = ITEMS.registerItem("bulletproof_with_helmet", properties -> new BulletproofArmor(properties.stacksTo(1),true));

    public static final DeferredItem<MapCreatorTool> MAP_CREATOR_TOOL = ITEMS.registerItem("map_creator_tool", MapCreatorTool::new);
    public static final DeferredItem<SpawnPointTool> SPAWN_POINT_TOOL = ITEMS.registerItem("spawn_point_tool", SpawnPointTool::new);

    static {
        TABS = DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, "fpsmatch");
        FPSM_TAB = TABS.register("other", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.tab.fpsm"))
                .icon(() -> T_INCENDIARY_GRENADE.get().getDefaultInstance()).displayItems((parameters, output) -> {
            ITEMS.getEntries().forEach((entry) -> {
                output.accept(entry.get());
            });
        }).build());
    }
}
