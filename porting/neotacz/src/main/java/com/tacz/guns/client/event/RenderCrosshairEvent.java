package com.tacz.guns.client.event;

import net.neoforged.fml.common.EventBusSubscriber;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateContext;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.renderer.crosshair.CrosshairType;
import com.tacz.guns.compat.shouldersurfing.ShoulderSurfingCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.MinecraftGuiCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;


@EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)
public class RenderCrosshairEvent {
    private static final Identifier HIT_ICON = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/crosshair/hit/hit_marker.png");
    private static final long KEEP_TIME = 300;
    private static boolean isRefitScreen = false;
    private static long hitTimestamp = -1L;
    private static long killTimestamp = -1L;
    private static long headShotTimestamp = -1L;

    /**
     * 当玩家手上拿着枪时，播放特定动画、或瞄准时需要隐藏准心
     */
    @SubscribeEvent(receiveCanceled = true)
    public static void onRenderOverlay(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            if (!IGun.mainHandHoldGun(player)) {
                return;
            }
            // 全面替换成自己的
            event.setCanceled(true);
            // 击中显示
            renderHitMarker(event.getGuiGraphics());
            // 换弹进行时取消准心渲染
            ReloadState reloadState = IGunOperator.fromLivingEntity(player).getSynReloadState();
            if (reloadState.getStateType().isReloading()) {
                return;
            }
            // 打开枪械改装界面的时候，取消准心渲染
            if (isRefitScreen) {
                return;
            }
            // 播放的动画需要隐藏准心时，取消准心渲染
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof IGun)) {
                return;
            }

            IClientPlayerGunOperator playerGunOperator = IClientPlayerGunOperator.fromLocalPlayer(player);
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            TimelessAPI.getGunDisplay(stack).ifPresent(gunIndex -> {
                // 瞄准快要完成时，取消准心渲染
                if (playerGunOperator.getClientAimingProgress(partialTick) > 0.9) {
                    // 枪包可以强制显示准星
                    boolean forceShow = gunIndex.isShowCrosshair();
                    // 越肩视角可以强制显示准星
                    boolean shoulderSurfingForceShow = ShoulderSurfingCompat.showCrosshair();
                    // 两个强制都没有时，那么才允许隐藏
                    if (!forceShow && !shoulderSurfingForceShow) {
                        return;
                    }
                }

                AnimationStateMachine<?> animationStateMachine = gunIndex.getAnimationStateMachine();
                if (animationStateMachine == null) {
                    renderCrosshair(event.getGuiGraphics());
                    return;
                }
                AnimationStateContext context = animationStateMachine.getContext();
                if (context == null || !context.shouldHideCrossHair()) {
                    renderCrosshair(event.getGuiGraphics());
                }
            });
        }
    }

    @SubscribeEvent
    public static void onRenderTick(RenderFrameEvent.Pre event) {
        // 奇迹的是，RenderGameOverlayEvent.PreLayer 事件中，screen 还未被赋值...
        isRefitScreen = MinecraftGuiCompat.screen() instanceof GunRefitScreen;
    }

    private static void renderCrosshair(GuiGraphicsExtractor graphics) {
        Options options = Minecraft.getInstance().options;
        // 越肩视角可以强制显示准星
        boolean shoulderSurfingForceShow = ShoulderSurfingCompat.showCrosshair();
        if (!options.getCameraType().isFirstPerson() && !shoulderSurfingForceShow) {
            return;
        }
        if (Minecraft.getInstance().gui.hud.isHidden()) {
            return;
        }
        MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
        if (gameMode == null) {
            return;
        }
        if (gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        Identifier location = CrosshairType.getTextureLocation(RenderConfig.CROSSHAIR_TYPE.get());

        float x = width / 2f - 8;
        float y = height / 2f - 8;
        graphics.blit(RenderPipelines.GUI_TEXTURED, location, (int) x, (int) y, 0, 0, 16, 16, 16, 16, ARGB.white(0.9f));
    }

    private static void renderHitMarker(GuiGraphicsExtractor graphics) {
        long remainHitTime = System.currentTimeMillis() - hitTimestamp;
        long remainKillTime = System.currentTimeMillis() - killTimestamp;
        long remainHeadShotTime = System.currentTimeMillis() - headShotTimestamp;
        float offset = RenderConfig.HIT_MARKET_START_POSITION.get().floatValue();
        float fadeTime;

        if (remainKillTime > KEEP_TIME) {
            if (remainHitTime > KEEP_TIME) {
                return;
            } else {
                fadeTime = remainHitTime;
            }
        } else {
            // 最大位移为 4 像素
            offset += (remainKillTime * 4f) / KEEP_TIME;
            fadeTime = remainKillTime;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float x = width / 2f - 8;
        float y = height / 2f - 8;

        float alpha = 1 - fadeTime / KEEP_TIME;
        int color = remainHeadShotTime > KEEP_TIME ? ARGB.white(alpha) : ARGB.color(alpha, 0xFF0000);
        graphics.blit(RenderPipelines.GUI_TEXTURED, HIT_ICON, (int) (x - offset), (int) (y - offset), 0, 0, 8, 8, 16, 16, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, HIT_ICON, (int) (x + 8 + offset), (int) (y - offset), 8, 0, 8, 8, 16, 16, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, HIT_ICON, (int) (x - offset), (int) (y + 8 + offset), 0, 8, 8, 8, 16, 16, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, HIT_ICON, (int) (x + 8 + offset), (int) (y + 8 + offset), 8, 8, 8, 8, 16, 16, color);
    }

    public static void markHitTimestamp() {
        RenderCrosshairEvent.hitTimestamp = System.currentTimeMillis();
    }

    public static void markKillTimestamp() {
        RenderCrosshairEvent.killTimestamp = System.currentTimeMillis();
    }

    public static void markHeadShotTimestamp() {
        RenderCrosshairEvent.headShotTimestamp = System.currentTimeMillis();
    }
}
