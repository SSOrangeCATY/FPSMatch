package com.phasetranscrystal.fpsmatch;

import com.phasetranscrystal.fpsmatch.bukkit.FPSMBukkit;
import com.phasetranscrystal.fpsmatch.common.client.FPSMGameHudManager;
import com.phasetranscrystal.fpsmatch.common.client.renderer.*;
import com.phasetranscrystal.fpsmatch.common.client.screen.VanillaGuiRegister;
import com.phasetranscrystal.fpsmatch.common.client.screen.hud.*;
import com.phasetranscrystal.fpsmatch.common.command.FPSMCommand;
import com.phasetranscrystal.fpsmatch.common.packet.*;
import com.phasetranscrystal.fpsmatch.common.packet.attribute.BulletproofArmorAttributeS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.effect.FlashBombAddonS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.entity.ThrowEntityC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;
import com.phasetranscrystal.fpsmatch.common.effect.FPSMEffectRegister;
import com.phasetranscrystal.fpsmatch.common.entity.EntityRegister;
import com.phasetranscrystal.fpsmatch.common.gamerule.FPSMatchRule;
import com.phasetranscrystal.fpsmatch.common.item.FPSMItemRegister;
import com.phasetranscrystal.fpsmatch.common.sound.FPSMSoundRegister;
import com.phasetranscrystal.fpsmatch.common.packet.shop.*;
import com.tacz.guns.config.sync.SyncConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final String PROTOCOL_VERSION = "1.2.1";
    private static final NetworkPacketRegister PACKET_REGISTER = new NetworkPacketRegister(new ResourceLocation("fpsmatch", "main"),PROTOCOL_VERSION);
    public static final SimpleChannel INSTANCE = PACKET_REGISTER.getChannel();

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
        // ApiClientExample.login();
        // context.registerConfig(ModConfig.Type.SERVER, Config.serverSpec);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        registerPackets();
    }

    private static void registerPackets() {
        PACKET_REGISTER.registerPacket(ShopDataSlotS2CPacket.class);
        PACKET_REGISTER.registerPacket(ShopActionC2SPacket.class);
        PACKET_REGISTER.registerPacket(ShopMoneyS2CPacket.class);
        PACKET_REGISTER.registerPacket(FPSMatchStatsResetS2CPacket.class);
        PACKET_REGISTER.registerPacket(ThrowEntityC2SPacket.class);
        PACKET_REGISTER.registerPacket(FlashBombAddonS2CPacket.class);
        PACKET_REGISTER.registerPacket(FPSMatchGameTypeS2CPacket.class);
        PACKET_REGISTER.registerPacket(FPSMSoundPlayS2CPacket.class);
        PACKET_REGISTER.registerPacket(FPSMusicPlayS2CPacket.class);
        PACKET_REGISTER.registerPacket(FPSMusicStopS2CPacket.class);
        PACKET_REGISTER.registerPacket(SaveSlotDataC2SPacket.class);
        PACKET_REGISTER.registerPacket(EditToolSelectMapC2SPacket.class);
        PACKET_REGISTER.registerPacket(PullGameInfoC2SPacket.class);
        PACKET_REGISTER.registerPacket(FPSMatchRespawnS2CPacket.class);
        PACKET_REGISTER.registerPacket(GameTabStatsS2CPacket.class);
        PACKET_REGISTER.registerPacket(OpenEditorC2SPacket.class);
        PACKET_REGISTER.registerPacket(BulletproofArmorAttributeS2CPacket.class);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FPSMCommand.onRegisterCommands(event);
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            //注册原版GUI
            VanillaGuiRegister.register();
        }

        @SubscribeEvent
        public static void onRegisterGuiOverlaysEvent(RegisterGuiOverlaysEvent event) {
            event.registerBelow(VanillaGuiOverlay.CHAT_PANEL.id(),"flash_bomb_hud", FlashBombHud.INSTANCE);
            event.registerBelowAll("hud_manager", FPSMGameHudManager.INSTANCE);
        }


        @SubscribeEvent
        public static void onRegisterEntityRenderEvent(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EntityRegister.SMOKE_SHELL.get(), new SmokeShellRenderer());
            event.registerEntityRenderer(EntityRegister.INCENDIARY_GRENADE.get(), new IncendiaryGrenadeRenderer());
            event.registerEntityRenderer(EntityRegister.GRENADE.get(), new GrenadeRenderer());
            event.registerEntityRenderer(EntityRegister.FLASH_BOMB.get(),new FlashBombRenderer());
            event.registerEntityRenderer(EntityRegister.MATCH_DROP_ITEM.get(),new MatchDropRenderer());
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void pullGameInfo(){
        INSTANCE.sendToServer(new PullGameInfoC2SPacket());
    }
}
