package com.phasetranscrystal.fpsmatch.common.client;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.data.FPSMClientGlobalData;
import com.phasetranscrystal.fpsmatch.common.client.event.FPSMClientResetEvent;
import com.phasetranscrystal.fpsmatch.common.client.key.*;
import com.phasetranscrystal.fpsmatch.common.client.renderer.*;
import com.phasetranscrystal.fpsmatch.common.client.screen.VanillaGuiRegister;
import com.phasetranscrystal.fpsmatch.common.client.screen.hud.FlashBombHud;
import com.phasetranscrystal.fpsmatch.common.effect.FPSMEffectRegister;
import com.phasetranscrystal.fpsmatch.common.entity.EntityRegister;
import net.minecraft.Optionull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.*;

public class FPSMClient {
    private static final FPSMClientGlobalData DATA = new FPSMClientGlobalData();
    public static final Comparator<PlayerInfo> PLAYER_COMPARATOR = Comparator.<PlayerInfo>comparingInt((playerInfo) -> 0)
            .thenComparing((playerInfo) -> Optionull.mapOrDefault(playerInfo.getTeam(), PlayerTeam::getName, ""))
            .thenComparing((playerInfo) -> playerInfo.getProfile().name(), String::compareToIgnoreCase);

    public static FPSMClientGlobalData getGlobalData(){
        return DATA;
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        // 注册键位
        event.registerCategory(FPSMKeyCategories.FPSM);
        event.register(CustomHudKey.KEY);
        event.register(SwitchPreviousItemKey.KEY);
        event.register(ClearRenderableAreasKey.KEY);
    }

    public static void onClientSetup(FMLClientSetupEvent event)
    {
        //注册原版GUI
    }

    public static void onRegisterGuiLayersEvent(RegisterGuiLayersEvent event) {
        event.registerBelow(
                VanillaGuiLayers.CHAT,
                Identifier.fromNamespaceAndPath(FPSMatch.MODID, "flash_bomb_hud"),
                FlashBombHud.INSTANCE);
        event.registerBelowAll(
                Identifier.fromNamespaceAndPath(FPSMatch.MODID, "hud_manager"),
                FPSMGameHudManager.INSTANCE);
    }

    public static void onRegisterEntityRenderEvent(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityRegister.SMOKE_SHELL.get(), new SmokeShellRenderer());
        event.registerEntityRenderer(EntityRegister.INCENDIARY_GRENADE.get(), new IncendiaryGrenadeRenderer());
        event.registerEntityRenderer(EntityRegister.GRENADE.get(), new GrenadeRenderer());
        event.registerEntityRenderer(EntityRegister.FLASH_BOMB.get(),new FlashBombRenderer());
        event.registerEntityRenderer(EntityRegister.MATCH_DROP_ITEM.get(),new MatchDropRenderer());
    }


    public static List<PlayerInfo> getPlayerInfos() {
        if (Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.connection.getListedOnlinePlayers().stream().sorted(PLAYER_COMPARATOR).limit(80L).toList();
        }
        return new ArrayList<>();
    }

    public static void reset() {
        DATA.reset();
        NeoForge.EVENT_BUS.post(new FPSMClientResetEvent());
    }
}
