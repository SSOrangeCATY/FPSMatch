package com.tacz.guns.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.renderer.BedrockSubmitUtils;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.entity.FirstPersonTracerAnchor;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EntityBulletRenderer extends EntityRenderer<EntityKineticBullet, EntityBulletRenderer.BulletRenderState> {
    private static final double LEGACY_TRACER_HIDE_DISTANCE = 2.0D;
    private static final double FIRST_PERSON_TRACER_HIDE_DISTANCE = 3.0D;

    public EntityBulletRenderer(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    public static Optional<BedrockModel> getModel() {
        return InternalAssetLoader.getBedrockModel(InternalAssetLoader.DEFAULT_BULLET_MODEL);
    }

    @Override
    public BulletRenderState createRenderState() {
        return new BulletRenderState();
    }

    @Override
    public void extractRenderState(EntityKineticBullet bullet, BulletRenderState state, float partialTicks) {
        super.extractRenderState(bullet, state, partialTicks);
        state.gunId = bullet.getGunId();
        state.gunDisplayId = bullet.getGunDisplayId();
        state.ammoId = bullet.getAmmoId();
        state.tracerColorOverride = bullet.getTracerColorOverride().orElse(null);
        state.tracerAmmo = bullet.isTracerAmmo();
        state.tracerSize = bullet.getTracerSizeOverride();
        state.xRot = Mth.lerp(partialTicks, bullet.xRotO, bullet.getXRot());
        state.yRot = Mth.lerp(partialTicks, bullet.yRotO, bullet.getYRot());
        state.deltaMovement = bullet.getDeltaMovement();
        state.bulletPosition = bullet.getPosition(partialTicks);
        state.tickCount = bullet.tickCount;

        Entity shooter = bullet.getOwner();
        state.hasShooter = shooter != null;
        state.shooterIsLocalPlayer = shooter instanceof LocalPlayer
                && this.entityRenderDispatcher.options.getCameraType().isFirstPerson();
        state.shooterEyePosition = shooter == null ? Vec3.ZERO : shooter.getEyePosition(partialTicks);
        state.shooterCurrentEyePosition = shooter == null ? Vec3.ZERO : shooter.getEyePosition();
        state.bulletId = bullet.getId();
        state.firstPersonTracerAnchor = null;
        if (state.shooterIsLocalPlayer) {
            FirstPersonTracerAnchor anchor = bullet.getFirstPersonTracerAnchor();
            if (anchor == null || !anchor.matches(state.gunId, state.gunDisplayId, shooter.getId())) {
                anchor = GunItemRendererWrapper.copyFirstPersonTracerAnchor(state.gunId, state.gunDisplayId, shooter.getId()).orElse(null);
                if (anchor != null && anchor.isFinite()) {
                    bullet.setFirstPersonTracerAnchor(anchor);
                } else {
                    anchor = null;
                }
            }
            if (anchor != null && anchor.isFinite()) {
                state.firstPersonTracerAnchor = anchor;
            }
        }
        BulletTracerDebug.bulletExtract(
                state.bulletId,
                state.tickCount,
                state.shooterIsLocalPlayer,
                state.gunId,
                state.gunDisplayId,
                state.ammoId,
                state.bulletPosition,
                state.deltaMovement,
                state.xRot,
                state.yRot,
                state.firstPersonTracerAnchor,
                state.firstPersonTracerAnchor != null
        );
    }

    @Override
    public void submit(BulletRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        Optional<GunDisplayInstance> display = TimelessAPI.getGunDisplay(state.gunDisplayId, state.gunId);
        if (display.isEmpty()) {
            super.submit(state, poseStack, submitNodeCollector, camera);
            return;
        }

        float @Nullable [] tracerColor = state.tracerColorOverride != null ? state.tracerColorOverride : display.get().getTracerColor();
        TimelessAPI.getClientAmmoIndex(state.ammoId).ifPresent(ammoIndex -> {
            BedrockAmmoModel ammoEntityModel = ammoIndex.getAmmoEntityModel();
            Identifier textureLocation = ammoIndex.getAmmoEntityTextureLocation();
            if (ammoEntityModel != null && textureLocation != null) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 180.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot));
                poseStack.translate(0, 1.5, 0);
                poseStack.scale(-1, -1, 1);
                BedrockSubmitUtils.submitModel(
                        submitNodeCollector,
                        poseStack,
                        RenderTypes.entityTranslucent(textureLocation),
                        ammoEntityModel,
                        ItemDisplayContext.GROUND,
                        state.lightCoords,
                        OverlayTexture.NO_OVERLAY
                );
                poseStack.popPose();
            }

            if (state.tracerAmmo) {
                float[] actualTracerColor = Objects.requireNonNullElse(tracerColor, ammoIndex.getTracerColor());
                renderTracerAmmo(state, actualTracerColor, poseStack, submitNodeCollector, camera);
            }
        });
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    private void renderTracerAmmo(BulletRenderState state, float[] tracerColor, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        getModel().ifPresent(model -> {
            if (!state.hasShooter) {
                return;
            }
            if (state.shooterIsLocalPlayer && !RenderConfig.FIRST_PERSON_BULLET_TRACER_ENABLE.get()) {
                return;
            }

            float width = 0.005f;
            double trailLength = 0.85 * state.deltaMovement.length();
            double disToEye = state.bulletPosition.distanceTo(state.shooterEyePosition);
            double bulletDistance = state.bulletPosition.distanceTo(state.shooterCurrentEyePosition);
            trailLength = Math.min(trailLength, disToEye * 0.8);
            double offsetReducer = 0.0;
            Vec3 appliedOffset = Vec3.ZERO;
            Vec3 renderStartWorld;
            Vec3 renderEndWorld;

            poseStack.pushPose();
            if (state.shooterIsLocalPlayer && state.firstPersonTracerAnchor != null) {
                offsetReducer = Math.max(0, (50 - disToEye)) / 50;
                appliedOffset = state.firstPersonTracerAnchor.worldOffset().scale(offsetReducer);
                poseStack.translate(
                        appliedOffset.x,
                        appliedOffset.y,
                        appliedOffset.z
                );
            }
            renderStartWorld = state.bulletPosition.add(appliedOffset);
            Vec3 tracerDirection = state.deltaMovement.lengthSqr() > 1.0E-8 ? state.deltaMovement.normalize() : Vec3.ZERO;
            renderEndWorld = renderStartWorld.add(tracerDirection.scale(trailLength));
            double nearestSegmentDistance = distanceToSegment(state.shooterCurrentEyePosition, renderStartWorld, renderEndWorld);
            width *= state.tracerSize;
            width *= (float) Math.max(1.0, disToEye / 3.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 180.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot));
            poseStack.translate(0, state.shooterIsLocalPlayer ? 0 : -0.2, trailLength / 2.0);
            poseStack.scale(width, width, (float) trailLength);

            boolean legacyGateAllowsRender = state.tickCount >= 5 || bulletDistance > LEGACY_TRACER_HIDE_DISTANCE;
            // 第一人称只应用旧版固定 muzzle offset；旧 tick gate 通过后仍按最终提交线段隐藏近端曳光。
            boolean visualNearGateAllowsRender = !state.shooterIsLocalPlayer || nearestSegmentDistance > FIRST_PERSON_TRACER_HIDE_DISTANCE;
            boolean tracerSubmitted = legacyGateAllowsRender && visualNearGateAllowsRender;
            String blockedReason = "";
            if (!legacyGateAllowsRender) {
                blockedReason = "near_bullet_distance";
            } else if (!visualNearGateAllowsRender) {
                blockedReason = "near_first_person_segment";
            }
            BulletTracerDebug.tracerRender(state, state.firstPersonTracerAnchor, appliedOffset, offsetReducer, disToEye, bulletDistance,
                    trailLength, legacyGateAllowsRender, visualNearGateAllowsRender, tracerSubmitted, blockedReason, renderStartWorld, renderEndWorld,
                    nearestSegmentDistance, camera);
            if (tracerSubmitted) {
                BedrockSubmitUtils.submitModel(
                        submitNodeCollector,
                        poseStack,
                        RenderTypes.energySwirl(InternalAssetLoader.DEFAULT_BULLET_TEXTURE, 15, 15),
                        model,
                        ItemDisplayContext.NONE,
                        state.lightCoords,
                        OverlayTexture.NO_OVERLAY,
                        tracerColor[0],
                        tracerColor[1],
                        tracerColor[2],
                        1
                );
            }
            poseStack.popPose();
        });
    }

    private static double distanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr <= 1.0E-8) {
            return point.distanceTo(start);
        }
        double t = point.subtract(start).dot(segment) / lengthSqr;
        t = Mth.clamp(t, 0.0D, 1.0D);
        return point.distanceTo(start.add(segment.scale(t)));
    }

    @Override
    protected int getBlockLightLevel(@NotNull EntityKineticBullet entityBullet, @NotNull BlockPos blockPos) {
        return 15;
    }

    @Override
    public boolean shouldRender(EntityKineticBullet bullet, Frustum camera, double pCamX, double pCamY, double pCamZ) {
        AABB aabb = bullet.getBoundingBox().inflate(0.5);
        if (aabb.hasNaN() || aabb.getSize() == 0) {
            aabb = new AABB(bullet.getX() - 2.0, bullet.getY() - 2.0, bullet.getZ() - 2.0, bullet.getX() + 2.0, bullet.getY() + 2.0, bullet.getZ() + 2.0);
        }
        return camera.isVisible(aabb);
    }

    public static class BulletRenderState extends EntityRenderState {
        @Nullable Identifier gunId;
        @Nullable Identifier gunDisplayId;
        @Nullable Identifier ammoId;
        float @Nullable [] tracerColorOverride;
        boolean tracerAmmo;
        float tracerSize = 1.0F;
        float xRot;
        float yRot;
        Vec3 deltaMovement = Vec3.ZERO;
        Vec3 bulletPosition = Vec3.ZERO;
        int tickCount;
        int bulletId;
        boolean hasShooter;
        boolean shooterIsLocalPlayer;
        Vec3 shooterEyePosition = Vec3.ZERO;
        Vec3 shooterCurrentEyePosition = Vec3.ZERO;
        @Nullable FirstPersonTracerAnchor firstPersonTracerAnchor;
    }
}
