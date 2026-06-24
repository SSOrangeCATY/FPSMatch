package com.phasetranscrystal.fpsmatch;

import com.phasetranscrystal.fpsmatch.common.capability.FPSMCapabilityRegister;
import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.client.net.FPSMClientPacketRegistrar;
import com.phasetranscrystal.fpsmatch.common.client.screen.VanillaGuiRegister;
import com.phasetranscrystal.fpsmatch.common.command.FPSMCommand;
import com.phasetranscrystal.fpsmatch.common.drop.ThrowableRegistry;
import com.phasetranscrystal.fpsmatch.common.effect.FPSMEffectRegister;
import com.phasetranscrystal.fpsmatch.common.entity.EntityRegister;
import com.phasetranscrystal.fpsmatch.common.gamerule.FPSMatchRule;
import com.phasetranscrystal.fpsmatch.common.item.FPSMItemRegister;
import com.phasetranscrystal.fpsmatch.common.packet.AddAreaDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.AddPointDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.EditToolClickC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMInventorySelectedS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMSoundPlayC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMSoundPlayS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchGameTypeS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchRespawnS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchStatsResetS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMusicPlayS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMusicStopS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.MapCreatorToolActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenMapCreatorToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenSpawnPointToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.PullGameInfoC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.RemoveDebugDataByPrefixS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.SpawnPointToolActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.ToolInteractionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.attribute.BulletproofArmorAttributeS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.effect.FlashBombAddonS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.entity.ThrowEntityC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetailS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomInvitationS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomSettingsC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomToastS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapSelectionAccessS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapSelectionSnapshotS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.OpenMapSelectionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;
import com.phasetranscrystal.fpsmatch.common.packet.shop.OpenShopEditorC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.shop.SaveSlotDataC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.shop.ShopActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.shop.ShopDataSlotS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.shop.ShopMoneyS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.spec.SpectateModeS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.FPSMAddTeamS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamCapabilitiesS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamChatMessageC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamManageActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamManageResultS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamPlayerLeaveS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.team.TeamPlayerStatsS2CPacket;
import com.phasetranscrystal.fpsmatch.common.sound.FPSMSoundRegister;
import com.phasetranscrystal.fpsmatch.compat.spectate.net.SpectatorSyncNetwork;
import com.phasetranscrystal.fpsmatch.compat.tacz.TACZBootstrap;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(FPSMatch.MODID)
public class FPSMatch {
    public static final String MODID = "fpsmatch";
    public static final Logger LOGGER = LoggerFactory.getLogger("FPSMatch");
    private static final String PROTOCOL_VERSION = "1.3.0";
    private static final NetworkPacketRegister PACKET_REGISTER = new NetworkPacketRegister(Identifier.fromNamespaceAndPath("fpsmatch", "main"), PROTOCOL_VERSION);
    public static final String DEBUG_SYS_PROP = "fpsm.debug";
    private static volatile boolean DEBUG_ENABLED = Boolean.parseBoolean(System.getProperty(DEBUG_SYS_PROP, "false"));

    public FPSMatch(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onRegisterPackets);
        modEventBus.addListener(PACKET_REGISTER::registerPayloadHandlers);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.addListener(FPSMClient::onRegisterKeyMappings);
            modEventBus.addListener(FPSMClient::onClientSetup);
            modEventBus.addListener(FPSMClient::onRegisterGuiLayersEvent);
            modEventBus.addListener(FPSMClient::onRegisterEntityRenderEvent);
            modEventBus.addListener(VanillaGuiRegister::register);
        }
        NeoForge.EVENT_BUS.register(this);
        VanillaGuiRegister.CONTAINERS.register(modEventBus);
        FPSMItemRegister.ITEMS.register(modEventBus);
        FPSMItemRegister.TABS.register(modEventBus);
        FPSMSoundRegister.SOUNDS.register(modEventBus);
        EntityRegister.ENTITY_TYPES.register(modEventBus);
        FPSMEffectRegister.MOB_EFFECTS.register(modEventBus);
        FPSMatchRule.register(modEventBus);
        FPSMCapabilityRegister.register();
        container.registerConfig(ModConfig.Type.CLIENT, FPSMConfig.clientSpec);
        container.registerConfig(ModConfig.Type.COMMON, FPSMConfig.commonSpec);
        container.registerConfig(ModConfig.Type.SERVER, FPSMConfig.initServer());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ThrowableRegistry.registerItemToSubType(FPSMItemRegister.FLASH_BOMB.get(), ThrowableRegistry.FLASH_BANG);
            ThrowableRegistry.registerItemToSubType(FPSMItemRegister.GRENADE.get(), ThrowableRegistry.GRENADE);
            ThrowableRegistry.registerItemToSubType(FPSMItemRegister.SMOKE_SHELL.get(), ThrowableRegistry.SMOKE);
            ThrowableRegistry.registerItemToSubType(FPSMItemRegister.CT_INCENDIARY_GRENADE.get(), ThrowableRegistry.MOLOTOV);
            ThrowableRegistry.registerItemToSubType(FPSMItemRegister.T_INCENDIARY_GRENADE.get(), ThrowableRegistry.MOLOTOV);
            registerCompat();
        });
    }

    private static void registerCompat() {
        if (ModList.get().isLoaded("tacz")) {
            TACZBootstrap.registerCompat();
        }
    }

    private void onRegisterPackets(final FMLCommonSetupEvent event) {
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
        PACKET_REGISTER.registerPacket(OpenShopEditorC2SPacket.class);
        PACKET_REGISTER.registerPacket(BulletproofArmorAttributeS2CPacket.class);
        PACKET_REGISTER.registerPacket(FPSMAddTeamS2CPacket.class);
        PACKET_REGISTER.registerPacket(TeamCapabilitiesS2CPacket.class);
        PACKET_REGISTER.registerPacket(SpectateModeS2CPacket.class);
        PACKET_REGISTER.registerPacket(FPSMInventorySelectedS2CPacket.class);
        PACKET_REGISTER.registerPacket(TeamChatMessageC2SPacket.class);
        PACKET_REGISTER.registerPacket(AddAreaDataS2CPacket.class);
        PACKET_REGISTER.registerPacket(AddPointDataS2CPacket.class);
        PACKET_REGISTER.registerPacket(RemoveDebugDataByPrefixS2CPacket.class);
        PACKET_REGISTER.registerPacket(ToolInteractionC2SPacket.class);
        PACKET_REGISTER.registerPacket(OpenMapCreatorToolScreenS2CPacket.class);
        PACKET_REGISTER.registerPacket(MapCreatorToolActionC2SPacket.class);
        PACKET_REGISTER.registerPacket(OpenSpawnPointToolScreenS2CPacket.class);
        PACKET_REGISTER.registerPacket(SpawnPointToolActionC2SPacket.class);
        PACKET_REGISTER.registerPacket(OpenMapSelectionC2SPacket.class);
        PACKET_REGISTER.registerPacket(MapSelectionAccessS2CPacket.class);
        PACKET_REGISTER.registerPacket(MapSelectionSnapshotS2CPacket.class);
        PACKET_REGISTER.registerPacket(MapRoomActionC2SPacket.class);
        PACKET_REGISTER.registerPacket(MapRoomDetailS2CPacket.class);
        PACKET_REGISTER.registerPacket(MapRoomSettingsC2SPacket.class);
        PACKET_REGISTER.registerPacket(MapRoomToastS2CPacket.class);
        PACKET_REGISTER.registerPacket(MapRoomInvitationS2CPacket.class);
        PACKET_REGISTER.registerPacket(TeamManageActionC2SPacket.class);
        PACKET_REGISTER.registerPacket(TeamManageResultS2CPacket.class);
        SpectatorSyncNetwork.registerPackets();

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            event.enqueueWork(FPSMClientPacketRegistrar::registerAll);
        }
    }

    public static <M> void sendTo(Player player, M message) {
        if (player.level().isClientSide()) {
            sendToServer(message);
        } else {
            sendToPlayer((ServerPlayer) player, message);
        }
    }

    public static <M> void sendToPlayer(ServerPlayer player, M message) {
        NetworkPacketRegister.getRegisterFromCache(message.getClass()).sendToPlayer(player, message);
    }

    public static <M> void sendToServer(M message) {
        NetworkPacketRegister.getRegisterFromCache(message.getClass()).sendToServer(message);
    }

    public static <M> void sendToAllPlayers(M message) {
        NetworkPacketRegister.getRegisterFromCache(message.getClass()).sendToAllPlayers(message);
    }

    public static <M> void registerPacket(Class<M> packetClass) {
        PACKET_REGISTER.registerPacket(packetClass);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FPSMCommand.onRegisterCommands(event);
    }

    public static synchronized boolean switchDebug() {
        return DEBUG_ENABLED = !DEBUG_ENABLED;
    }

    public static boolean isDebugEnabled() {
        return DEBUG_ENABLED;
    }

    public static void debug(String msg, Object... args) {
        if (DEBUG_ENABLED) {
            LOGGER.info(msg, args);
        }
    }

    public static void info(String msg, Object... args) {
        LOGGER.info(msg, args);
    }

    public static void pullGameInfo() {
        sendToServer(new PullGameInfoC2SPacket());
    }
}
