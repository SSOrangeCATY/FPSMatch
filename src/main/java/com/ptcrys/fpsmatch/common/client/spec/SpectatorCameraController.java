package com.ptcrys.fpsmatch.common.client.spec;

import com.ptcrys.fpsmatch.mixin.spec.teammate.CameraInvokerMixin;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Client camera state for fixed death-location and C4 orbit views. */
public final class SpectatorCameraController {
    private static float yaw;
    private static float pitch;

    private SpectatorCameraController() {}

    public static void reset() {
        yaw = 0.0f;
        pitch = 0.0f;
    }

    public static void applyAngles(float deltaYaw, float deltaPitch) {
        if (!SpectateState.isRestricted()) return;
        yaw += deltaYaw;
        pitch = SpectatorCameraMath.clampPitch(pitch + deltaPitch);
    }

    public static void setAngles(float newYaw, float newPitch) {
        yaw = newYaw;
        pitch = SpectatorCameraMath.clampPitch(newPitch);
    }

    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }

    public static Vec3 resolvePosition(float partialTick) {
        SpectateTarget target = SpectateState.getTarget();
        if (target == null) return null;
        if (target.mode() == SpectateMode.C4_ORBIT) {
            return SpectatorCameraMath.orbitPosition(target.anchor(), yaw, pitch, target.orbitRadius());
        }
        return target.anchor();
    }

    public static void applyToCamera(Camera camera, float partialTick) {
        Vec3 position = resolvePosition(partialTick);
        if (position == null || camera == null) return;
        ((CameraInvokerMixin) camera).invokeSetPosition(position.x, position.y, position.z);
        ((CameraInvokerMixin) camera).invokeSetRotation(yaw, pitch);
    }

    public static Entity resolveEntity() {
        SpectateTarget target = SpectateState.getTarget();
        Minecraft mc = Minecraft.getInstance();
        return target == null || mc.level == null || target.entityId() < 0 ? null : mc.level.getEntity(target.entityId());
    }
}
