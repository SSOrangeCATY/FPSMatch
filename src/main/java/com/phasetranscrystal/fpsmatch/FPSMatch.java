package com.phasetranscrystal.fpsmatch;

import com.mojang.logging.LogUtils;
import com.phasetranscrystal.fpsmatch.client.CSGameOverlay;
import com.phasetranscrystal.fpsmatch.command.FPSMCommand;
import com.phasetranscrystal.fpsmatch.cs.MapRegister;
import com.phasetranscrystal.fpsmatch.net.CSGameSettingsPacket;
import com.phasetranscrystal.fpsmatch.net.ShopDataSlotPacket;
import com.phasetranscrystal.fpsmatch.test.TestRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
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
        INSTANCE.messageBuilder(CSGameSettingsPacket.class, 0)
                .encoder(CSGameSettingsPacket::encode)
                .decoder(CSGameSettingsPacket::decode)
                .consumerNetworkThread(CSGameSettingsPacket::handle)
                .add();

        INSTANCE.messageBuilder(ShopDataSlotPacket.class, 1)
                .encoder(ShopDataSlotPacket::encode)
                .decoder(ShopDataSlotPacket::decode)
                .consumerNetworkThread(ShopDataSlotPacket::handle)
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
            event.registerBelowAll("fpsm_cs", new CSGameOverlay());
        }
    }
}
