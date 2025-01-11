package com.phasetranscrystal.fpsmatch;

import com.mojang.logging.LogUtils;
import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.client.renderer.C4Renderer;
import com.phasetranscrystal.fpsmatch.client.renderer.GrenadeRenderer;
import com.phasetranscrystal.fpsmatch.client.renderer.IncendiaryGrenadeRenderer;
import com.phasetranscrystal.fpsmatch.client.renderer.SmokeShellRenderer;
import com.phasetranscrystal.fpsmatch.client.screen.CSGameOverlay;
import com.phasetranscrystal.fpsmatch.client.screen.DeathMessageHud;
import com.phasetranscrystal.fpsmatch.client.screen.FlashBombHud;
import com.phasetranscrystal.fpsmatch.command.FPSMCommand;
import com.phasetranscrystal.fpsmatch.core.shop.functional.LMManager;
import com.phasetranscrystal.fpsmatch.effect.FPSMEffectRegister;
import com.phasetranscrystal.fpsmatch.entity.EntityRegister;
import com.phasetranscrystal.fpsmatch.item.FPSMSoundRegister;
import com.phasetranscrystal.fpsmatch.net.*;
import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
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

import java.util.concurrent.atomic.AtomicInteger;

@Mod(FPSMatch.MODID)
public class FPSMatch {
    public static final String MODID = "fpsmatch";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("fpsmatch", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static LMManager listenerModuleManager;

    public FPSMatch(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        FPSMItemRegister.ITEMS.register(modEventBus);
        FPSMSoundRegister.SOUNDS.register(modEventBus);
        EntityRegister.ENTITY_TYPES.register(modEventBus);
        FPSMEffectRegister.MOB_EFFECTS.register(modEventBus);
        context.registerConfig(ModConfig.Type.CLIENT, Config.clientSpec);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        AtomicInteger i = new AtomicInteger();
        INSTANCE.messageBuilder(CSGameSettingsS2CPacket.class, i.getAndIncrement())
                .encoder(CSGameSettingsS2CPacket::encode)
                .decoder(CSGameSettingsS2CPacket::decode)
                .consumerNetworkThread(CSGameSettingsS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(ShopDataSlotS2CPacket.class, i.getAndIncrement())
                .encoder(ShopDataSlotS2CPacket::encode)
                .decoder(ShopDataSlotS2CPacket::decode)
                .consumerNetworkThread(ShopDataSlotS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(ShopActionC2SPacket.class, i.getAndIncrement())
                .encoder(ShopActionC2SPacket::encode)
                .decoder(ShopActionC2SPacket::decode)
                .consumerNetworkThread(ShopActionC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(BombActionC2SPacket.class, i.getAndIncrement())
                .encoder(BombActionC2SPacket::encode)
                .decoder(BombActionC2SPacket::decode)
                .consumerNetworkThread(BombActionC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(BombActionS2CPacket.class, i.getAndIncrement())
                .encoder(BombActionS2CPacket::encode)
                .decoder(BombActionS2CPacket::decode)
                .consumerNetworkThread(BombActionS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(BombDemolitionProgressS2CPacket.class, i.getAndIncrement())
                .encoder(BombDemolitionProgressS2CPacket::encode)
                .decoder(BombDemolitionProgressS2CPacket::decode)
                .consumerNetworkThread(BombDemolitionProgressS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(ShopMoneyS2CPacket.class, i.getAndIncrement())
                .encoder(ShopMoneyS2CPacket::encode)
                .decoder(ShopMoneyS2CPacket::decode)
                .consumerNetworkThread(ShopMoneyS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(ShopStatesS2CPacket.class, i.getAndIncrement())
                .encoder(ShopStatesS2CPacket::encode)
                .decoder(ShopStatesS2CPacket::decode)
                .consumerNetworkThread(ShopStatesS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(CSGameTabStatsS2CPacket.class, i.getAndIncrement())
                .encoder(CSGameTabStatsS2CPacket::encode)
                .decoder(CSGameTabStatsS2CPacket::decode)
                .consumerNetworkThread(CSGameTabStatsS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(FPSMatchStatsResetS2CPacket.class, i.getAndIncrement())
              .encoder(FPSMatchStatsResetS2CPacket::encode)
              .decoder(FPSMatchStatsResetS2CPacket::decode)
              .consumerNetworkThread(FPSMatchStatsResetS2CPacket::handle)
              .add();

        INSTANCE.messageBuilder(DeathMessageS2CPacket.class, i.getAndIncrement())
                .encoder(DeathMessageS2CPacket::encode)
                .decoder(DeathMessageS2CPacket::decode)
                .consumerNetworkThread(DeathMessageS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(FPSMatchLoginMessageS2CPacket.class, i.getAndIncrement())
                .encoder(FPSMatchLoginMessageS2CPacket::encode)
                .decoder(FPSMatchLoginMessageS2CPacket::decode)
                .consumerNetworkThread(FPSMatchLoginMessageS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(ThrowSmokeShellC2SPacket.class, i.getAndIncrement())
                .encoder(ThrowSmokeShellC2SPacket::encode)
                .decoder(ThrowSmokeShellC2SPacket::decode)
                .consumerNetworkThread(ThrowSmokeShellC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(ThrowIncendiaryGrenadeC2SPacket.class, i.getAndIncrement())
                .encoder(ThrowIncendiaryGrenadeC2SPacket::encode)
                .decoder(ThrowIncendiaryGrenadeC2SPacket::decode)
                .consumerNetworkThread(ThrowIncendiaryGrenadeC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(ThrowGrenade2CSPacket.class, i.getAndIncrement())
                .encoder(ThrowGrenade2CSPacket::encode)
                .decoder(ThrowGrenade2CSPacket::decode)
                .consumerNetworkThread(ThrowGrenade2CSPacket::handle)
                .add();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        new FPSMCommand().onRegisterCommands(event);
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {

        }

        @SubscribeEvent
        public static void onRegisterGuiOverlaysEvent(RegisterGuiOverlaysEvent event) {
            event.registerBelowAll("fpsm_cs_scores_bar", new CSGameOverlay());
            event.registerBelowAll("fpsm_death_message", DeathMessageHud.INSTANCE);
            event.registerBelow(VanillaGuiOverlay.CHAT_PANEL.id(),"flash_bomb_hud", FlashBombHud.INSTANCE);
        }


        @SubscribeEvent
        public static void onRegisterEntityRenderEvent(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EntityRegister.C4.get(), new C4Renderer());
            event.registerEntityRenderer(EntityRegister.SMOKE_SHELL.get(), new SmokeShellRenderer());
            event.registerEntityRenderer(EntityRegister.INCENDIARY_GRENADE.get(), new IncendiaryGrenadeRenderer());
            event.registerEntityRenderer(EntityRegister.GRENADE.get(), new GrenadeRenderer());
        }

    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onEvent(TickEvent.ClientTickEvent event) {
            if(ClientData.isWaiting || ClientData.isPause && !ClientData.currentMap.equals("fpsm_none")){
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().options.keyUp.setDown(false);
                    Minecraft.getInstance().options.keyLeft.setDown(false);
                    Minecraft.getInstance().options.keyDown.setDown(false);
                    Minecraft.getInstance().options.keyRight.setDown(false);
                    Minecraft.getInstance().options.keyJump.setDown(false);
                }
            }
        }
    }


}
