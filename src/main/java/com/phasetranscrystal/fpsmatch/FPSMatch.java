package com.phasetranscrystal.fpsmatch;

import com.mojang.logging.LogUtils;
import com.phasetranscrystal.fpsmatch.client.screen.CSGameOverlay;
import com.phasetranscrystal.fpsmatch.command.FPSMCommand;
import com.phasetranscrystal.fpsmatch.cs.MapRegister;
import com.phasetranscrystal.fpsmatch.net.CSGameSettingsS2CPacket;
import com.phasetranscrystal.fpsmatch.net.ShopActionC2SPacket;
import com.phasetranscrystal.fpsmatch.net.ShopActionS2CPacket;
import com.phasetranscrystal.fpsmatch.net.ShopDataSlotS2CPacket;
import com.phasetranscrystal.fpsmatch.test.TestRegister;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
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
        MapRegister.register();
        TestRegister.ITEMS.register(modEventBus);
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
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
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
    }
}
