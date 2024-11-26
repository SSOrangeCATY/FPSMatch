package com.phasetranscrystal.fpsmatch;

import com.mojang.logging.LogUtils;
import com.phasetranscrystal.fpsmatch.client.renderer.C4Renderer;
import com.phasetranscrystal.fpsmatch.client.screen.CSGameOverlay;
import com.phasetranscrystal.fpsmatch.command.FPSMCommand;
import com.phasetranscrystal.fpsmatch.entity.EntityRegister;
import com.phasetranscrystal.fpsmatch.net.*;
import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

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

    public FPSMatch(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        FPSMItemRegister.ITEMS.register(modEventBus);
        EntityRegister.ENTITY_TYPES.register(modEventBus);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        INSTANCE.messageBuilder(CSGameSettingsS2CPacket.class, 0)
                .encoder(CSGameSettingsS2CPacket::encode)
                .decoder(CSGameSettingsS2CPacket::decode)
                .consumerNetworkThread(CSGameSettingsS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(ShopDataSlotS2CPacket.class, 1)
                .encoder(ShopDataSlotS2CPacket::encode)
                .decoder(ShopDataSlotS2CPacket::decode)
                .consumerNetworkThread(ShopDataSlotS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(ShopActionC2SPacket.class, 2)
                .encoder(ShopActionC2SPacket::encode)
                .decoder(ShopActionC2SPacket::decode)
                .consumerNetworkThread(ShopActionC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(ShopActionS2CPacket.class, 3)
                .encoder(ShopActionS2CPacket::encode)
                .decoder(ShopActionS2CPacket::decode)
                .consumerNetworkThread(ShopActionS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(BombActionC2SPacket.class, 4)
                .encoder(BombActionC2SPacket::encode)
                .decoder(BombActionC2SPacket::decode)
                .consumerNetworkThread(BombActionC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(BombActionS2CPacket.class, 5)
                .encoder(BombActionS2CPacket::encode)
                .decoder(BombActionS2CPacket::decode)
                .consumerNetworkThread(BombActionS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(BombDemolitionProgressS2CPacket.class, 6)
                .encoder(BombDemolitionProgressS2CPacket::encode)
                .decoder(BombDemolitionProgressS2CPacket::decode)
                .consumerNetworkThread(BombDemolitionProgressS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(ShopMoneyS2CPacket.class, 7)
                .encoder(ShopMoneyS2CPacket::encode)
                .decoder(ShopMoneyS2CPacket::decode)
                .consumerNetworkThread(ShopMoneyS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(ShopStatesS2CPacket.class, 8)
                .encoder(ShopStatesS2CPacket::encode)
                .decoder(ShopStatesS2CPacket::decode)
                .consumerNetworkThread(ShopStatesS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(CSGameTabStatsS2CPacket.class, 9)
                .encoder(CSGameTabStatsS2CPacket::encode)
                .decoder(CSGameTabStatsS2CPacket::decode)
                .consumerNetworkThread(CSGameTabStatsS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(FPSMatchStatsResetS2CPacket.class, 10)
              .encoder(FPSMatchStatsResetS2CPacket::encode)
              .decoder(FPSMatchStatsResetS2CPacket::decode)
              .consumerNetworkThread(FPSMatchStatsResetS2CPacket::handle)
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
        }


        @SubscribeEvent
        public static void onRegisterEntityRenderEvent(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EntityRegister.C4.get(), new C4Renderer());
        }

    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientEvents {

    }


}
