package com.phasetranscrystal.fpsmatch;

import com.mojang.logging.LogUtils;
import com.phasetranscrystal.fpsmatch.bukkit.FPSMBukkit;
import com.phasetranscrystal.fpsmatch.common.client.FPSMGameHudManager;
import com.phasetranscrystal.fpsmatch.common.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.common.client.renderer.*;
import com.phasetranscrystal.fpsmatch.common.client.screen.VanillaGuiRegister;
import com.phasetranscrystal.fpsmatch.common.client.screen.hud.*;
import com.phasetranscrystal.fpsmatch.common.client.tab.TabManager;
import com.phasetranscrystal.fpsmatch.common.command.FPSMCommand;
import com.phasetranscrystal.fpsmatch.common.cs.command.VoteCommand;
import com.phasetranscrystal.fpsmatch.common.net.*;
import com.phasetranscrystal.fpsmatch.common.net.register.NetworkPacketRegister;
import com.phasetranscrystal.fpsmatch.core.item.IThrowEntityAble;
import com.phasetranscrystal.fpsmatch.common.effect.FPSMEffectRegister;
import com.phasetranscrystal.fpsmatch.common.entity.EntityRegister;
import com.phasetranscrystal.fpsmatch.common.gamerule.FPSMatchRule;
import com.phasetranscrystal.fpsmatch.common.item.FPSMItemRegister;
import com.phasetranscrystal.fpsmatch.common.item.FPSMSoundRegister;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.util.InputExtraCheck;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/*
    <FPSMatch>
    Copyright (C) <2025>  <SSOrangeCATY>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
@Mod(FPSMatch.MODID)
public class FPSMatch {
    public static final String MODID = "fpsmatch";
    public static final Logger LOGGER = LoggerFactory.getLogger("FPSMatch");
    private static final String PROTOCOL_VERSION = "1.2.0";
    public static final TicketType<UUID> ENTITY_CHUNK_TICKET = TicketType.create("fpsm_chunk_ticket", (a, b) -> 0);
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("fpsmatch", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public FPSMatch(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        VanillaGuiRegister.CONTAINERS.register(modEventBus);
        FPSMItemRegister.ITEMS.register(modEventBus);
        FPSMItemRegister.TABS.register(modEventBus);
        FPSMSoundRegister.SOUNDS.register(modEventBus);
        EntityRegister.ENTITY_TYPES.register(modEventBus);
        FPSMEffectRegister.MOB_EFFECTS.register(modEventBus);
        FPSMatchRule.init();
        context.registerConfig(ModConfig.Type.CLIENT, FPSMConfig.clientSpec);
        context.registerConfig(ModConfig.Type.COMMON, FPSMConfig.commonSpec);
        if(FPSMBukkit.isBukkitEnvironment()){
            FPSMBukkit.register();
        }
        // context.registerConfig(ModConfig.Type.SERVER, Config.serverSpec);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        NetworkPacketRegister.registerPackets();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FPSMCommand.onRegisterCommands(event);
        VoteCommand.onRegisterCommands(event);
    }


    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            TabManager.getInstance().registerRenderer(new CSGameTabRenderer());
            //注册原版GUI
            VanillaGuiRegister.register();
        }

        @SubscribeEvent
        public static void onRegisterGuiOverlaysEvent(RegisterGuiOverlaysEvent event) {
            event.registerBelowAll("fpsm_cs_scores_bar", new CSGameOverlay());
            event.registerBelowAll("fpsm_death_message", DeathMessageHud.INSTANCE);
            event.registerBelow(VanillaGuiOverlay.CHAT_PANEL.id(),"flash_bomb_hud", FlashBombHud.INSTANCE);
            event.registerBelow(VanillaGuiOverlay.CHAT_PANEL.id(),"mvp_hud", MVPHud.INSTANCE);
            event.registerBelowAll("hud_manager", FPSMGameHudManager.INSTANCE);
        }


        @SubscribeEvent
        public static void onRegisterEntityRenderEvent(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EntityRegister.C4.get(), new C4Renderer());
            event.registerEntityRenderer(EntityRegister.SMOKE_SHELL.get(), new SmokeShellRenderer());
            event.registerEntityRenderer(EntityRegister.INCENDIARY_GRENADE.get(), new IncendiaryGrenadeRenderer());
            event.registerEntityRenderer(EntityRegister.GRENADE.get(), new GrenadeRenderer());
            event.registerEntityRenderer(EntityRegister.FLASH_BOMB.get(),new FlashBombRenderer());
            event.registerEntityRenderer(EntityRegister.MATCH_DROP_ITEM.get(),new MatchDropRenderer());
        }
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onEvent(TickEvent.ClientTickEvent event) {
            LocalPlayer player = Minecraft.getInstance().player;
            if(player == null) return;
            if((ClientData.isWaiting || ClientData.isPause) && (!ClientData.currentMap.equals("fpsm_none") && !ClientData.currentTeam.equals("spectator"))){
                Minecraft.getInstance().options.keyUp.setDown(false);
                Minecraft.getInstance().options.keyLeft.setDown(false);
                Minecraft.getInstance().options.keyDown.setDown(false);
                Minecraft.getInstance().options.keyRight.setDown(false);
                Minecraft.getInstance().options.keyJump.setDown(false);
            }

            if(ClientData.isStart && (ClientData.currentMap.equals("fpsm_none") || ClientData.currentGameType.equals("none"))){
                FPSMatch.INSTANCE.sendToServer(new PullGameInfoC2SPacket());
            }
        }


        @SubscribeEvent
        public static void onUse(InputEvent.MouseButton.Pre event){
            if((ClientData.isWaiting || ClientData.isPause) && InputExtraCheck.isInGame()){
                if(checkLocalPlayerHand()){
                    event.setCanceled(true);
                }
            }
        }

        public static boolean checkLocalPlayerHand(){
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                Item main = player.getMainHandItem().getItem();
                Item off = player.getOffhandItem().getItem();
                return main instanceof IGun || main instanceof IThrowEntityAble || off instanceof IGun || off instanceof IThrowEntityAble;
            }
            return false;
        }


    }


}
