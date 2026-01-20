package com.phasetranscrystal.fpsmatch;

import com.phasetranscrystal.fpsmatch.bukkit.FPSMBukkit;
import com.phasetranscrystal.fpsmatch.common.capability.FPSMCapabilityRegister;
import com.phasetranscrystal.fpsmatch.common.client.screen.VanillaGuiRegister;
import com.phasetranscrystal.fpsmatch.common.command.FPSMCommand;
import com.phasetranscrystal.fpsmatch.common.drop.ThrowableRegistry;
import com.phasetranscrystal.fpsmatch.common.packet.*;
import com.phasetranscrystal.fpsmatch.common.packet.attribute.BulletproofArmorAttributeS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.effect.FlashBombAddonS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.entity.ThrowEntityC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;
import com.phasetranscrystal.fpsmatch.common.effect.FPSMEffectRegister;
import com.phasetranscrystal.fpsmatch.common.entity.EntityRegister;
import com.phasetranscrystal.fpsmatch.common.gamerule.FPSMatchRule;
import com.phasetranscrystal.fpsmatch.common.item.FPSMItemRegister;
import com.phasetranscrystal.fpsmatch.common.packet.spec.SpectateModeS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.*;
import com.phasetranscrystal.fpsmatch.common.sound.FPSMSoundRegister;
import com.phasetranscrystal.fpsmatch.common.packet.shop.*;
import com.phasetranscrystal.fpsmatch.compat.CounterStrikeGrenadesCompat;
import com.phasetranscrystal.fpsmatch.compat.cloth.FPSMenuIntegration;
import com.phasetranscrystal.fpsmatch.compat.impl.FPSMImpl;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import com.tacz.guns.client.gui.compat.ClothConfigScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;
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
    private static final String PROTOCOL_VERSION = "1.3.0";
    private static final NetworkPacketRegister PACKET_REGISTER = new NetworkPacketRegister(new ResourceLocation("fpsmatch", "main"),PROTOCOL_VERSION);
    public static final SimpleChannel INSTANCE = PACKET_REGISTER.getChannel();
    public static final String DEBUG_SYS_PROP = "fpsm.debug";
    private static volatile boolean DEBUG_ENABLED =
            Boolean.parseBoolean(System.getProperty(DEBUG_SYS_PROP, "false"));

    public FPSMatch(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onEnqueue);
        MinecraftForge.EVENT_BUS.register(this);
        VanillaGuiRegister.CONTAINERS.register(modEventBus);
        FPSMItemRegister.ITEMS.register(modEventBus);
        FPSMItemRegister.TABS.register(modEventBus);
        FPSMSoundRegister.SOUNDS.register(modEventBus);
        EntityRegister.ENTITY_TYPES.register(modEventBus);
        FPSMEffectRegister.MOB_EFFECTS.register(modEventBus);
        FPSMatchRule.init();
        FPSMCapabilityRegister.register();
        registerThrowables();
        context.registerConfig(ModConfig.Type.CLIENT, FPSMConfig.clientSpec);
        context.registerConfig(ModConfig.Type.COMMON, FPSMConfig.commonSpec);
        context.registerConfig(ModConfig.Type.SERVER, FPSMConfig.initServer());
        if(FPSMBukkit.isBukkitEnvironment()){
            FPSMBukkit.register();
        }
    }

    @SubscribeEvent
    public void onEnqueue(final InterModEnqueueEvent event) {
        event.enqueueWork(()->{
            if(FPSMImpl.findClothConfig()){
                DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> FPSMenuIntegration::registerModsPage);
            }else{
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    ClothConfigScreen.registerNoClothConfigPage();
                }
            }
        });
    }

    private static void registerThrowables() {
        ThrowableRegistry.registerSubType("grenade", 1);
        ThrowableRegistry.registerSubType("flashbang", 1);
        ThrowableRegistry.registerSubType("smoke", 1);
        ThrowableRegistry.registerSubType("molotov", 1);
        ThrowableRegistry.registerSubType("decoy", 1);
        ThrowableRegistry.registerSubType("incendiary", 1);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        PACKET_REGISTER.registerPacket(ShopDataSlotS2CPacket.class);
        PACKET_REGISTER.registerPacket(ShopActionC2SPacket.class);
        PACKET_REGISTER.registerPacket(ShopMoneyS2CPacket.class);
        PACKET_REGISTER.registerPacket(FPSMatchStatsResetS2CPacket.class);
        PACKET_REGISTER.registerPacket(ThrowEntityC2SPacket.class);
        PACKET_REGISTER.registerPacket(FlashBombAddonS2CPacket.class);
        PACKET_REGISTER.registerPacket(FPSMatchGameTypeS2CPacket.class);
        PACKET_REGISTER.registerPacket(FPSMSoundPlayS2CPacket.class);
        PACKET_REGISTER.registerPacket(FPSMusicPlayS2CPacket.class);
        PACKET_REGISTER.registerPacket(FPSMSoundPlayC2SPacket.class);
        PACKET_REGISTER.registerPacket(FPSMusicStopS2CPacket.class);
        PACKET_REGISTER.registerPacket(SaveSlotDataC2SPacket.class);
        PACKET_REGISTER.registerPacket(EditToolClickC2SPacket.class);
        PACKET_REGISTER.registerPacket(PullGameInfoC2SPacket.class);
        PACKET_REGISTER.registerPacket(FPSMatchRespawnS2CPacket.class);
        PACKET_REGISTER.registerPacket(TeamPlayerStatsS2CPacket.class);
        PACKET_REGISTER.registerPacket(TeamPlayerLeaveS2CPacket.class);
        PACKET_REGISTER.registerPacket(OpenEditorC2SPacket.class);
        PACKET_REGISTER.registerPacket(BulletproofArmorAttributeS2CPacket.class);
        PACKET_REGISTER.registerPacket(FPSMAddTeamS2CPacket.class);
        PACKET_REGISTER.registerPacket(TeamCapabilitiesS2CPacket.class);
        PACKET_REGISTER.registerPacket(SpectateModeS2CPacket.class);
        PACKET_REGISTER.registerPacket(FPSMInventorySelectedS2CPacket.class);
        PACKET_REGISTER.registerPacket(TeamChatMessageC2SPacket.class);

        event.enqueueWork(()->{
            ThrowableRegistry.registerItemToSubType(FPSMItemRegister.FLASH_BOMB.get(),ThrowableRegistry.FLASH_BANG);
            ThrowableRegistry.registerItemToSubType(FPSMItemRegister.GRENADE.get(),ThrowableRegistry.GRENADE);
            ThrowableRegistry.registerItemToSubType(FPSMItemRegister.SMOKE_SHELL.get(),ThrowableRegistry.SMOKE);
            ThrowableRegistry.registerItemToSubType(FPSMItemRegister.CT_INCENDIARY_GRENADE.get(),ThrowableRegistry.MOLOTOV);
            ThrowableRegistry.registerItemToSubType(FPSMItemRegister.T_INCENDIARY_GRENADE.get(),ThrowableRegistry.MOLOTOV);

            if(FPSMImpl.findCounterStrikeGrenadesMod()){
                CounterStrikeGrenadesCompat.init();
            }
        });
    }

    public static <M> void sendTo(Player player,M message){
        if(player.level().isClientSide){
            sendToServer(message);
        }else{
            sendToPlayer((ServerPlayer)player, message);
        }
    }

    public static <M> void sendToPlayer(ServerPlayer player,M message){
        NetworkPacketRegister.getChannelFromCache(message.getClass()).send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static <M> void sendToServer(M message){
        NetworkPacketRegister.getChannelFromCache(message.getClass()).sendToServer(message);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FPSMCommand.onRegisterCommands(event);
    }

    public static synchronized boolean switchDebug(){
        return DEBUG_ENABLED = !DEBUG_ENABLED;
    }

    public static boolean isDebugEnabled(){
        return DEBUG_ENABLED;
    }

    public static void debug(String msg, Object... args) { if (DEBUG_ENABLED) LOGGER.info(msg, args); }

    public static void info(String msg, Object... args) { LOGGER.info(msg,args);}

    @OnlyIn(Dist.CLIENT)
    public static void pullGameInfo(){
        INSTANCE.sendToServer(new PullGameInfoC2SPacket());
    }
}
