package com.tacz.guns.client.init;

import net.neoforged.fml.common.EventBusSubscriber;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.other.ThirdPersonManager;
import com.tacz.guns.client.gui.compat.ClothConfigScreen;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import com.tacz.guns.client.gui.overlay.HeatBarOverlay;
import com.tacz.guns.client.gui.overlay.InteractKeyTextOverlay;
import com.tacz.guns.client.gui.overlay.KillAmountOverlay;
import com.tacz.guns.client.input.*;
import com.tacz.guns.client.model.ScopeStencilFeatureRenderer;
import com.tacz.guns.client.model.ScopeStencilRenderTypes;
import com.tacz.guns.client.network.ClientNetworkMessageHandler;
import com.tacz.guns.client.network.ClientNetworkSender;
import com.tacz.guns.client.renderer.item.AmmoBoxItemModelProperty;
import com.tacz.guns.client.renderer.item.AmmoBoxItemTintSource;
import com.tacz.guns.client.renderer.item.TaczItemRenderers;
import com.tacz.guns.client.renderer.item.TaczCustomItemModel;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.client.tooltip.ClientAmmoBoxTooltip;
import com.tacz.guns.client.tooltip.ClientAttachmentItemTooltip;
import com.tacz.guns.client.tooltip.ClientBlockItemTooltip;
import com.tacz.guns.client.tooltip.ClientGunTooltip;
import com.tacz.guns.compat.ar.ARCompat;
import com.tacz.guns.compat.cloth.MenuIntegration;
import com.tacz.guns.compat.controllable.ControllableCompat;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import com.tacz.guns.compat.shouldersurfing.ShoulderSurfingCompat;
import com.tacz.guns.init.CompatRegistry;
import com.tacz.guns.inventory.tooltip.AmmoBoxTooltip;
import com.tacz.guns.inventory.tooltip.AttachmentItemTooltip;
import com.tacz.guns.inventory.tooltip.BlockItemTooltip;
import com.tacz.guns.inventory.tooltip.GunTooltip;
import com.tacz.guns.item.AmmoBoxItem;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ConfigureMainRenderTargetEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterFeatureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import com.tacz.guns.network.NetworkHandler;

import static net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR;

@EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)
public class ClientSetupEvent {
    private static final Identifier INTERNAL_ASSETS_RELOAD_LISTENER = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "client/internal_assets");

    @SubscribeEvent
    public static void onClientSetup(RegisterKeyMappingsEvent event) {
        // 注册键位
        event.registerCategory(TaczKeyMappings.CATEGORY);
        event.register(InspectKey.INSPECT_KEY);
        event.register(ReloadKey.RELOAD_KEY);
        event.register(ShootKey.SHOOT_KEY);
        event.register(InteractKey.INTERACT_KEY);
        event.register(FireSelectKey.FIRE_SELECT_KEY);
        event.register(AimKey.AIM_KEY);
        event.register(CrawlKey.CRAWL_KEY);
        event.register(RefitKey.REFIT_KEY);
        event.register(ZoomKey.ZOOM_KEY);
        event.register(MeleeKey.MELEE_KEY);
        event.register(ConfigKey.OPEN_CONFIG_KEY);
    }

    @SubscribeEvent
    public static void onClientSetup(RegisterClientTooltipComponentFactoriesEvent event) {
        // 注册文本提示
        event.register(GunTooltip.class, ClientGunTooltip::new);
        event.register(AmmoBoxTooltip.class, ClientAmmoBoxTooltip::new);
        event.register(AttachmentItemTooltip.class, ClientAttachmentItemTooltip::new);
        event.register(BlockItemTooltip.class, ClientBlockItemTooltip::new);
    }

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiLayersEvent event) {
        // 注册 HUD
        event.registerAboveAll(guiLayerId("tac_gun_hud_overlay"), new GunHudOverlay());
        event.registerAboveAll(guiLayerId("tac_heat_bar"), new HeatBarOverlay());
        event.registerAboveAll(guiLayerId("tac_kill_amount_overlay"), new KillAmountOverlay());
        event.registerAbove(CROSSHAIR, guiLayerId("tac_interact_key_overlay"), new InteractKeyTextOverlay());

    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 注册自己的的硬编码第三人称动画
        event.enqueueWork(ThirdPersonManager::registerDefault);

        // 初始化自己的枪包下载器
//        event.enqueueWork(ClientGunPackDownloadManager::init);

//        // 与 player animator 的兼容
//        event.enqueueWork(PlayerAnimatorCompat::init);

        // 与 Shoulder Surfing Reloaded 的兼容
        event.enqueueWork(ShoulderSurfingCompat::init);

		// 与 Accelerated Rendering 的兼容
		event.enqueueWork(ARCompat::init);

        // 客户端可选兼容注册必须留在 client setup 中，避免 dedicated server 加载 GUI/controller 类。
        event.enqueueWork(ControllableCompat::init);
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded(CompatRegistry.CLOTH_CONFIG)) {
                MenuIntegration.registerModsPage();
            } else {
                ClothConfigScreen.registerNoClothConfigPage();
            }
        });
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        TaczItemRenderers.registerFirstPersonRenderers();
    }

    @SubscribeEvent
    public static void registerItemTintSources(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "ammo_box"), AmmoBoxItemTintSource.MAP_CODEC);
    }

    @SubscribeEvent
    public static void registerRangeSelectItemModelProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(AmmoBoxItem.PROPERTY_NAME, AmmoBoxItemModelProperty.MAP_CODEC);
    }

    @SubscribeEvent
    public static void registerItemModels(RegisterItemModelsEvent event) {
        event.register(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "custom_item"), TaczCustomItemModel.Unbaked.MAP_CODEC);
    }

    @SubscribeEvent
    public static void registerClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        NetworkHandler.registerClientSender(ClientNetworkSender::sendToServer);
        NetworkHandler.registerClientboundDispatcher(ClientNetworkMessageHandler::handle);
        NetworkHandler.registerClientboundMessageHandlers();
        event.register(NetworkHandler.ClientboundTaczPayload.TYPE, NetworkHandler::handleClientPayload);
    }

    @SubscribeEvent
    public static void configureMainRenderTarget(ConfigureMainRenderTargetEvent event) {
        event.enableStencil();
    }

    @SubscribeEvent
    public static void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        ScopeStencilRenderTypes.registerPipelines(event);
    }

    @SubscribeEvent
    public static void registerFeatureRenderers(RegisterFeatureRenderersEvent event) {
        event.register(ScopeStencilFeatureRenderer.TYPE, new ScopeStencilFeatureRenderer());
    }

    @SubscribeEvent
    public static void onClientResourceReload(AddClientReloadListenersEvent event) {
        PlayerAnimatorCompat.init();
        event.addListener(INTERNAL_ASSETS_RELOAD_LISTENER, new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void preparations, ResourceManager manager, ProfilerFiller profiler) {
                // Internal TACZ models/animations must refresh before gunpack assets consume them.
                InternalAssetLoader.onResourceReload();
                SoundPlayManager.clearSoundResourceCache();
            }
        });
        ClientAssetsManager.INSTANCE.reloadAndRegister(event::addListener);
        if (PlayerAnimatorCompat.isInstalled()) {
            PlayerAnimatorCompat.registerReloadListener(event::addListener);
        }
    }

    private static Identifier guiLayerId(String path) {
        return Identifier.fromNamespaceAndPath(GunMod.MOD_ID, path);
    }
}
