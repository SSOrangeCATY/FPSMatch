package com.tacz.guns.entity;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Client-only first-person tracer anchor captured from the rendered gun muzzle.
 * It intentionally carries the camera basis from the capture frame so entity
 * rendering does not reinterpret a camera-local muzzle offset with a later pose.
 */
public final class FirstPersonTracerAnchor {
    private final int playerId;
    private final @Nullable Identifier gunId;
    private final @Nullable Identifier displayId;
    private final Vector3f localOffset;
    private final Vec3 worldOffset;
    private final Vec3 muzzleWorldPosition;
    private final Vec3 cameraPosition;
    private final Vec3 cameraRight;
    private final Vec3 cameraUp;
    private final Vec3 cameraForward;
    private final float cameraXRot;
    private final float cameraYRot;
    private final long gameTime;
    private final long sequence;

    public FirstPersonTracerAnchor(int playerId,
                                   @Nullable Identifier gunId,
                                   @Nullable Identifier displayId,
                                   Vector3f localOffset,
                                   Vec3 cameraPosition,
                                   Vec3 cameraRight,
                                   Vec3 cameraUp,
                                   Vec3 cameraForward,
                                   Vec3 worldOffset,
                                   float cameraXRot,
                                   float cameraYRot,
                                   long gameTime,
                                   long sequence) {
        this.playerId = playerId;
        this.gunId = gunId;
        this.displayId = displayId;
        this.localOffset = new Vector3f(localOffset);
        this.cameraPosition = cameraPosition;
        this.cameraRight = cameraRight;
        this.cameraUp = cameraUp;
        this.cameraForward = cameraForward;
        this.worldOffset = worldOffset;
        this.muzzleWorldPosition = cameraPosition.add(worldOffset);
        this.cameraXRot = cameraXRot;
        this.cameraYRot = cameraYRot;
        this.gameTime = gameTime;
        this.sequence = sequence;
    }

    public boolean matches(@Nullable Identifier gunId, @Nullable Identifier displayId, int playerId) {
        return this.playerId == playerId
                && Objects.equals(this.gunId, gunId)
                && Objects.equals(this.displayId, displayId);
    }

    public boolean isFinite() {
        return isFinite(localOffset)
                && isFinite(worldOffset)
                && isFinite(muzzleWorldPosition)
                && isFinite(cameraPosition)
                && isFinite(cameraRight)
                && isFinite(cameraUp)
                && isFinite(cameraForward);
    }

    public int playerId() {
        return playerId;
    }

    public @Nullable Identifier gunId() {
        return gunId;
    }

    public @Nullable Identifier displayId() {
        return displayId;
    }

    public Vector3f localOffset() {
        return new Vector3f(localOffset);
    }

    public Vec3 worldOffset() {
        return worldOffset;
    }

    public Vec3 muzzleWorldPosition() {
        return muzzleWorldPosition;
    }

    public Vec3 cameraPosition() {
        return cameraPosition;
    }

    public Vec3 cameraRight() {
        return cameraRight;
    }

    public Vec3 cameraUp() {
        return cameraUp;
    }

    public Vec3 cameraForward() {
        return cameraForward;
    }

    public float cameraXRot() {
        return cameraXRot;
    }

    public float cameraYRot() {
        return cameraYRot;
    }

    public long gameTime() {
        return gameTime;
    }

    public long sequence() {
        return sequence;
    }

    private static boolean isFinite(Vector3f value) {
        return Float.isFinite(value.x) && Float.isFinite(value.y) && Float.isFinite(value.z);
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
