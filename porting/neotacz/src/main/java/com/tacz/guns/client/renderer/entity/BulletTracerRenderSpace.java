package com.tacz.guns.client.renderer.entity;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class BulletTracerRenderSpace {
    private BulletTracerRenderSpace() {
    }

    public static Vec3 muzzleRenderOffsetToWorldOffset(Vector3f renderOffset) {
        // cacheMuzzlePosition reads the final first-person model pose after the hand renderer
        // has already been oriented for the camera. Rotating this value by camera basis again
        // double-applies yaw and locks non -Z tracers back toward the original axis.
        return new Vec3(renderOffset.x, renderOffset.y, renderOffset.z);
    }

    public static boolean isFinite(Vec3 offset) {
        return Double.isFinite(offset.x) && Double.isFinite(offset.y) && Double.isFinite(offset.z);
    }
}
