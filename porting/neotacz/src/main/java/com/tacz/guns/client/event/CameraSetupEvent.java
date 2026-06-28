package com.tacz.guns.client.event;

import net.neoforged.fml.common.EventBusSubscriber;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonRenderHandler;
import com.github.exopandora.shouldersurfing.api.client.IShoulderSurfingCamera;
import com.github.exopandora.shouldersurfing.api.client.ShoulderSurfing;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.api.modifier.ParameterizedCachePair;
import com.tacz.guns.client.renderer.item.TaczItemRenderers;
import com.tacz.guns.client.debug.ScopeRenderDebug;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.compat.shouldersurfing.ShoulderSurfingCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.RecoilModifier;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.math.MathUtil;
import com.tacz.guns.util.math.SecondOrderDynamics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.util.Optional;

@EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)
public class CameraSetupEvent {
    /**
     * 用于平滑 FOV 变化
     */
    public static final SecondOrderDynamics WORLD_FOV_DYNAMICS = new SecondOrderDynamics(0.5f, 1.2f, 0.5f, 0);
    public static final SecondOrderDynamics ITEM_MODEL_FOV_DYNAMICS = new SecondOrderDynamics(0.5f, 1.2f, 0.5f, 0);
    private static PolynomialSplineFunction pitchSplineFunction;
    private static PolynomialSplineFunction yawSplineFunction;
    private static long shootTimeStamp = -1L;
    private static double xRotO = 0;
    private static double yRotO = 0;

    @SubscribeEvent
    public static void applyLevelCameraAnimation(ViewportEvent.ComputeCameraAngles event) {
        if (!Minecraft.getInstance().options.bobView().get()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack stack = getActiveFirstPersonItem();
        if (!isActiveFirstPersonItem(player, stack)) {
            return;
        }
        // 尝试调用物品的自定义相机动画
        TaczItemRenderers.getAnimated(stack)
                .ifPresent(renderer -> renderer.applyLevelCameraAnimation(event, stack, player));

    }

    @SubscribeEvent
    public static void applyItemInHandCameraAnimation(BeforeRenderHandEvent event) {
        if (!Minecraft.getInstance().options.bobView().get()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack stack = getActiveFirstPersonItem();
        if (!isActiveFirstPersonItem(player, stack)) {
            return;
        }
        // 尝试调用物品的自定义相机动画
        TaczItemRenderers.getAnimated(stack)
                .ifPresent(renderer -> renderer.applyItemInHandCameraAnimation(event, stack, player));
    }

    private static boolean isActiveFirstPersonItem(LocalPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        var activeInstance = FirstPersonRenderHandler.getActiveAnimationInstance();
        if (activeInstance != null && !activeInstance.currentItem().isEmpty()) {
            return ItemStack.matches(activeInstance.currentItem(), stack);
        }
        return ItemStack.matches(player.getMainHandItem(), stack);
    }

    @SubscribeEvent
    public static void applyScopeMagnification(ViewportEvent.ComputeFov event) {
        // MC 26.2 stores world FOV on Camera before extracting retained render state.
        // CameraHudFovMixin applies the authoritative 1.20.1 zoom semantics at Camera.calculateFov().
    }

    public static float computeWorldFov(float vanillaWorldFov, float partialTick) {
        Entity entity = Minecraft.getInstance().getCameraEntity();
        if (!(entity instanceof LivingEntity livingEntity)) {
            float result = WORLD_FOV_DYNAMICS.update(vanillaWorldFov);
            ScopeRenderDebug.fov("world_no_living", ItemStack.EMPTY, vanillaWorldFov, result, 0.0F, 1.0F, vanillaWorldFov, null, 0, "no_living");
            return result;
        }
        ItemStack stack = livingEntity instanceof LocalPlayer
                ? getActiveFirstPersonItem()
                : KeepingItemRenderer.getRenderer().getCurrentItem();
        if (livingEntity instanceof LocalPlayer localPlayer && !isActiveFirstPersonItem(localPlayer, stack)) {
            float result = WORLD_FOV_DYNAMICS.update(vanillaWorldFov);
            ScopeRenderDebug.fov("world_inactive_item", stack, vanillaWorldFov, result, 0.0F, 1.0F, vanillaWorldFov, null, 0, "inactive_item");
            return result;
        }
        if (!(stack.getItem() instanceof IGun iGun)) {
            float result = WORLD_FOV_DYNAMICS.update(vanillaWorldFov);
            ScopeRenderDebug.fov("world_not_gun", stack, vanillaWorldFov, result, 0.0F, 1.0F, vanillaWorldFov, null, 0, "not_gun");
            return result;
        }
        float zoom = iGun.getAimingZoom(stack);
        float aimingProgress;
        if (livingEntity instanceof LocalPlayer localPlayer) {
            aimingProgress = IClientPlayerGunOperator.fromLocalPlayer(localPlayer).getClientAimingProgress(partialTick);
        } else {
            aimingProgress = IGunOperator.fromLivingEntity(livingEntity).getSynAimingProgress();
        }
        float target = (float) MathUtil.magnificationToFov(1 + (zoom - 1) * aimingProgress, vanillaWorldFov);
        float result = WORLD_FOV_DYNAMICS.update(target);
        Identifier scopeItemId = iGun.getAttachmentId(stack, AttachmentType.SCOPE);
        CompoundTag scopeTag = iGun.getAttachmentTag(stack, AttachmentType.SCOPE);
        int zoomNumber = AttachmentItemDataAccessor.getZoomNumberFromTag(scopeTag);
        ScopeRenderDebug.fov("world", stack, vanillaWorldFov, result, aimingProgress, zoom, target, scopeItemId, zoomNumber, "calculateFov");
        return result;
    }

    @SubscribeEvent
    public static void applyGunModelFovModifying(ViewportEvent.ComputeFov event) {
        // MC 26.2 exposes ComputeFov without the old Forge usedConfiguredFov split.
        // The first-person hand projection now comes from CameraRenderState.hudFov,
        // so CameraHudFovMixin applies the 1.20.1 item-model FOV semantics there.
    }

    public static float computeGunModelHudFov(float vanillaHudFov, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return ITEM_MODEL_FOV_DYNAMICS.update(vanillaHudFov);
        }
        ItemStack stack = getActiveFirstPersonItem();
        if (!isActiveFirstPersonItem(player, stack) || !(stack.getItem() instanceof IGun iGun)) {
            return ITEM_MODEL_FOV_DYNAMICS.update(vanillaHudFov);
        }

        Identifier scopeItemId = iGun.getAttachmentId(stack, AttachmentType.SCOPE);
        if (scopeItemId.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
            scopeItemId = iGun.getBuiltInAttachmentId(stack, AttachmentType.SCOPE);
        }
        CompoundTag scopeTag = iGun.getAttachmentTag(stack, AttachmentType.SCOPE);
        int zoomNumber = AttachmentItemDataAccessor.getZoomNumberFromTag(scopeTag);
        float modifiedFov = TimelessAPI.getClientAttachmentIndex(scopeItemId)
                .map(index -> {
                    float[] viewsFov = index.getViewsFov();
                    return viewsFov[zoomNumber % viewsFov.length];
                })
                .orElse(
                        TimelessAPI.getGunDisplay(stack)
                                .map(GunDisplayInstance::getZoomModelFov)
                                .orElse(vanillaHudFov)
                );
        IClientPlayerGunOperator gunOperator = IClientPlayerGunOperator.fromLocalPlayer(player);
        float aimingProgress = gunOperator.getClientAimingProgress(partialTick);
        float target = Mth.lerp(aimingProgress, vanillaHudFov, modifiedFov);
        float result = ITEM_MODEL_FOV_DYNAMICS.update(target);
        ScopeRenderDebug.fov("hud", stack, vanillaHudFov, result, aimingProgress, iGun.getAimingZoom(stack), modifiedFov, scopeItemId, zoomNumber, "extractRenderState");
        return result;
    }

    private static ItemStack getActiveFirstPersonItem() {
        var activeInstance = FirstPersonRenderHandler.getActiveAnimationInstance();
        if (activeInstance != null && !activeInstance.currentItem().isEmpty()) {
            return activeInstance.currentItem();
        }
        ItemStack keepingItem = KeepingItemRenderer.getRenderer().getCurrentItem();
        if (!keepingItem.isEmpty()) {
            return keepingItem;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getMainHandItem();
    }

    @SubscribeEvent
    public static void initialCameraRecoil(GunFireEvent event) {
        if (event.getLogicalSide().isClient()) {
            LivingEntity shooter = event.getShooter();
            LocalPlayer player = Minecraft.getInstance().player;
            if (!shooter.equals(player)) {
                return;
            }
            ItemStack mainHandItem = player.getMainHandItem();
            if (!(mainHandItem.getItem() instanceof IGun iGun)) {
                return;
            }
            AttachmentCacheProperty cacheProperty = IGunOperator.fromLivingEntity(player).getCacheProperty();
            if (cacheProperty == null) {
                return;
            }
            Identifier gunId = iGun.getGunId(mainHandItem);
            Optional<ClientGunIndex> gunIndexOptional = TimelessAPI.getClientGunIndex(gunId);
            if (gunIndexOptional.isEmpty()) {
                return;
            }
            ClientGunIndex gunIndex = gunIndexOptional.get();
            GunData gunData = gunIndex.getGunData();
            // 获取所有配件对摄像机后坐力的修改
            ParameterizedCachePair<Float, Float> attachmentRecoilModifier = cacheProperty.getCache(RecoilModifier.ID);
            IClientPlayerGunOperator clientPlayerGunOperator = IClientPlayerGunOperator.fromLocalPlayer(player);
            float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            float aimingProgress = clientPlayerGunOperator.getClientAimingProgress(partialTicks);
            float zoom = iGun.getAimingZoom(mainHandItem);
            float aimingRecoilModifier = 1 - aimingProgress + aimingProgress / (float) Math.min(Math.sqrt(zoom), 1.5);
            // 如果是趴下，那么后坐力按 data 设计减少（默认为降低一半）
            if (!player.isSwimming() && player.getPose() == Pose.SWIMMING) {
                aimingRecoilModifier = aimingRecoilModifier * gunData.getCrawlRecoilMultiplier();
            }
            pitchSplineFunction = gunData.getRecoil().genPitchSplineFunction((float) attachmentRecoilModifier.left().eval(aimingRecoilModifier));
            yawSplineFunction = gunData.getRecoil().genYawSplineFunction((float) attachmentRecoilModifier.right().eval(aimingRecoilModifier));
            shootTimeStamp = System.currentTimeMillis();
            xRotO = 0;
            yRotO = 0;
        }
    }

    @SubscribeEvent
    public static void applyCameraRecoil(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        long timeTotal = System.currentTimeMillis() - shootTimeStamp;
        if (pitchSplineFunction != null && pitchSplineFunction.isValidPoint(timeTotal)) {
            double value = pitchSplineFunction.value(timeTotal);
            if (ShoulderSurfingCompat.isInstalled() && ShoulderSurfing.getInstance().isShoulderSurfing()) {
                IShoulderSurfingCamera camera = ShoulderSurfing.getInstance().getCamera();
                camera.setXRot(camera.getXRot() - (float) (value - xRotO));
            } else {
                player.setXRot(player.getXRot() - (float) (value - xRotO));
            }
            xRotO = value;
        }
        if (yawSplineFunction != null && yawSplineFunction.isValidPoint(timeTotal)) {
            double value = yawSplineFunction.value(timeTotal);
            if (ShoulderSurfingCompat.isInstalled() && ShoulderSurfing.getInstance().isShoulderSurfing()) {
                IShoulderSurfingCamera camera = ShoulderSurfing.getInstance().getCamera();
                camera.setYRot(camera.getYRot() - (float) (value - yRotO));
            } else {
                player.setYRot(player.getYRot() - (float) (value - yRotO));
            }
            yRotO = value;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onComputeMovementFov(ComputeFovModifierEvent event) {
        if (!RenderConfig.DISABLE_MOVEMENT_ATTRIBUTE_FOV.get()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        float f = 1.0f;
        if (player.getMainHandItem().getItem() instanceof AbstractGunItem) {
            if (player.getAbilities().flying) {
                f *= 1.1F;
            }
            event.setNewFovModifier(player.isSprinting() ? 1.15f * f : f);
        }
    }
}
