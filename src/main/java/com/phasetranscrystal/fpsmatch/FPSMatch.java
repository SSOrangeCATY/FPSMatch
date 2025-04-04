package com.phasetranscrystal.fpsmatch;

import com.mojang.logging.LogUtils;
import com.phasetranscrystal.fpsmatch.bukkit.FPSMBukkit;
import com.phasetranscrystal.fpsmatch.client.FPSMGameHudManager;
import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.client.renderer.*;
import com.phasetranscrystal.fpsmatch.client.screen.VanillaGuiRegister;
import com.phasetranscrystal.fpsmatch.client.screen.hud.*;
import com.phasetranscrystal.fpsmatch.client.tab.TabManager;
import com.phasetranscrystal.fpsmatch.command.FPSMCommand;
import com.phasetranscrystal.fpsmatch.command.VoteCommand;
import com.phasetranscrystal.fpsmatch.core.item.IThrowEntityAble;
import com.phasetranscrystal.fpsmatch.core.shop.functional.LMManager;
import com.phasetranscrystal.fpsmatch.effect.FPSMEffectRegister;
import com.phasetranscrystal.fpsmatch.entity.EntityRegister;
import com.phasetranscrystal.fpsmatch.gamerule.FPSMatchRule;
import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import com.phasetranscrystal.fpsmatch.item.FPSMSoundRegister;
import com.phasetranscrystal.fpsmatch.net.*;
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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Mod(FPSMatch.MODID)
public class FPSMatch {
    public static final TicketType<UUID> ENTITY_CHUNK_TICKET = TicketType.create("fpsm_chunk_ticket", (a, b) -> 0);
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
        VanillaGuiRegister.CONTAINERS.register(modEventBus);
        FPSMItemRegister.ITEMS.register(modEventBus);
        FPSMItemRegister.TABS.register(modEventBus);
        FPSMSoundRegister.SOUNDS.register(modEventBus);
        EntityRegister.ENTITY_TYPES.register(modEventBus);
        FPSMEffectRegister.MOB_EFFECTS.register(modEventBus);
        FPSMatchRule.init();
        context.registerConfig(ModConfig.Type.CLIENT, FPSMConfig.clientSpec);
        context.registerConfig(ModConfig.Type.COMMON, FPSMConfig.commonSpec);
        FPSMBukkit.register();
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

        INSTANCE.messageBuilder(ThrowEntityC2SPacket.class, i.getAndIncrement())
                .encoder(ThrowEntityC2SPacket::encode)
                .decoder(ThrowEntityC2SPacket::decode)
                .consumerNetworkThread(ThrowEntityC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(FlashBombAddonS2CPacket.class, i.getAndIncrement())
                .encoder(FlashBombAddonS2CPacket::encode)
                .decoder(FlashBombAddonS2CPacket::decode)
                .consumerNetworkThread(FlashBombAddonS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(FPSMatchTabRemovalS2CPacket.class, i.getAndIncrement())
                .encoder(FPSMatchTabRemovalS2CPacket::encode)
                .decoder(FPSMatchTabRemovalS2CPacket::decode)
                .consumerNetworkThread(FPSMatchTabRemovalS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(FPSMatchGameTypeS2CPacket.class, i.getAndIncrement())
                .encoder(FPSMatchGameTypeS2CPacket::encode)
                .decoder(FPSMatchGameTypeS2CPacket::decode)
                .consumerNetworkThread(FPSMatchGameTypeS2CPacket::handle)
                .add();
        INSTANCE.messageBuilder(MvpMessageS2CPacket.class, i.getAndIncrement())
                .encoder(MvpMessageS2CPacket::encode)
                .decoder(MvpMessageS2CPacket::decode)
                .consumerNetworkThread(MvpMessageS2CPacket::handle)
                .add();
        INSTANCE.messageBuilder(MvpHUDCloseS2CPacket.class, i.getAndIncrement())
                .encoder(MvpHUDCloseS2CPacket::encode)
                .decoder(MvpHUDCloseS2CPacket::decode)
                .consumerNetworkThread(MvpHUDCloseS2CPacket::handle)
                .add();
        INSTANCE.messageBuilder(FPSMusicPlayS2CPacket.class, i.getAndIncrement())
                .encoder(FPSMusicPlayS2CPacket::encode)
                .decoder(FPSMusicPlayS2CPacket::decode)
                .consumerNetworkThread(FPSMusicPlayS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(FPSMusicStopS2CPacket.class, i.getAndIncrement())
                .encoder(FPSMusicStopS2CPacket::encode)
                .decoder(FPSMusicStopS2CPacket::decode)
                .consumerNetworkThread(FPSMusicStopS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(SaveSlotDataC2SPacket.class, i.getAndIncrement())
                .encoder(SaveSlotDataC2SPacket::encode)
                .decoder(SaveSlotDataC2SPacket::decode)
                .consumerNetworkThread(SaveSlotDataC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(EditToolSelectMapC2SPacket.class, i.getAndIncrement())
                .encoder(EditToolSelectMapC2SPacket::encode)
                .decoder(EditToolSelectMapC2SPacket::decode)
                .consumerNetworkThread(EditToolSelectMapC2SPacket::handle)
                .add();
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
            if((ClientData.isWaiting || ClientData.isPause) && (!ClientData.currentMap.equals("fpsm_none") && !ClientData.currentTeam.equals("spectator"))){
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().options.keyUp.setDown(false);
                    Minecraft.getInstance().options.keyLeft.setDown(false);
                    Minecraft.getInstance().options.keyDown.setDown(false);
                    Minecraft.getInstance().options.keyRight.setDown(false);
                    Minecraft.getInstance().options.keyJump.setDown(false);
                }
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
