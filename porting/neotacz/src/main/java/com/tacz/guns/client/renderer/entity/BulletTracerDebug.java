package com.tacz.guns.client.renderer.entity;

import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.entity.FirstPersonTracerAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BulletTracerDebug {
    private static final boolean ENABLED = Boolean.getBoolean("tacz.debug.tracer");
    private static final int MAX_SAMPLES = Integer.getInteger("tacz.debug.tracer.maxSamples", 16384);
    private static final int MUZZLE_SNAPSHOT_INTERVAL = Math.max(1, Integer.getInteger("tacz.debug.tracer.muzzleInterval", 20));
    private static final Path TRACE_PATH = Path.of(System.getProperty("tacz.debug.tracer.file", "tacz-tracer.jsonl"));
    private static BufferedWriter writer;
    private static int samples;

    private BulletTracerDebug() {
    }

    static boolean enabled() {
        return ENABLED;
    }

    public static void muzzleSnapshot(FirstPersonTracerAnchor anchor) {
        muzzleSnapshot(anchor, "unspecified");
    }

    public static void muzzleSnapshot(FirstPersonTracerAnchor anchor, String source) {
        if (!ENABLED) {
            return;
        }
        if (anchor.sequence() % MUZZLE_SNAPSHOT_INTERVAL != 0) {
            return;
        }
        JsonObject json = base("muzzle_snapshot");
        json.addProperty("source", source);
        addAnchor(json, anchor);
        write(json);
    }

    public static void autoTest(String phase,
                                String direction,
                                float yaw,
                                float pitch,
                                int shotIndex,
                                @Nullable ShootResult result,
                                @Nullable Vec3 playerPosition) {
        if (!ENABLED) {
            return;
        }
        JsonObject json = base("autotest");
        json.addProperty("phase", phase);
        json.addProperty("direction", direction);
        json.addProperty("yaw", yaw);
        json.addProperty("pitch", pitch);
        json.addProperty("shotIndex", shotIndex);
        if (result != null) {
            json.addProperty("result", result.name());
        }
        if (playerPosition != null) {
            addVec(json, "playerPosition", playerPosition);
        }
        write(json);
    }

    static void bulletExtract(int bulletId,
                              int bulletTick,
                              boolean localFirstPerson,
                              @Nullable Identifier gunId,
                              @Nullable Identifier displayId,
                              @Nullable Identifier ammoId,
                              Vec3 bulletPosition,
                              Vec3 deltaMovement,
                              float xRot,
                              float yRot,
                              @Nullable FirstPersonTracerAnchor anchor,
                              boolean acceptedAnchor) {
        if (!ENABLED) {
            return;
        }
        JsonObject json = base("bullet_extract");
        json.addProperty("bulletId", bulletId);
        json.addProperty("bulletTick", bulletTick);
        json.addProperty("localFirstPerson", localFirstPerson);
        json.addProperty("acceptedAnchor", acceptedAnchor);
        addId(json, "gunId", gunId);
        addId(json, "displayId", displayId);
        addId(json, "ammoId", ammoId);
        addVec(json, "bulletPosition", bulletPosition);
        addVec(json, "deltaMovement", deltaMovement);
        json.addProperty("xRot", xRot);
        json.addProperty("yRot", yRot);
        if (anchor != null) {
            addAnchor(json, anchor);
        }
        write(json);
    }

    static void tracerRender(EntityBulletRenderer.BulletRenderState state,
                             @Nullable FirstPersonTracerAnchor anchor,
                             Vec3 appliedOffset,
                             double offsetReducer,
                             double disToEye,
                             double bulletDistance,
                             double trailLength,
                             boolean legacyGateAllowsRender,
                             boolean visualNearGateAllowsRender,
                             boolean tracerSubmitted,
                             String blockedReason,
                             Vec3 renderStartWorld,
                             Vec3 renderEndWorld,
                             double nearestSegmentDistance,
                             CameraRenderState camera) {
        if (!ENABLED) {
            return;
        }
        JsonObject json = base("tracer_render");
        json.addProperty("bulletId", state.bulletId);
        json.addProperty("bulletTick", state.tickCount);
        json.addProperty("localFirstPerson", state.shooterIsLocalPlayer);
        json.addProperty("legacyGate", legacyGateAllowsRender);
        json.addProperty("visualNearGate", visualNearGateAllowsRender);
        json.addProperty("nearGateAllowsRender", tracerSubmitted);
        json.addProperty("tracerSubmitted", tracerSubmitted);
        if (!blockedReason.isEmpty()) {
            json.addProperty("blockedReason", blockedReason);
        }
        json.addProperty("offsetReducer", offsetReducer);
        json.addProperty("disToEye", disToEye);
        json.addProperty("bulletDistance", bulletDistance);
        json.addProperty("nearestSegmentDistance", nearestSegmentDistance);
        json.addProperty("trailLength", trailLength);
        addVec(json, "bulletPosition", state.bulletPosition);
        addVec(json, "appliedOffset", appliedOffset);
        addVec(json, "renderStartWorld", renderStartWorld);
        addVec(json, "renderEndWorld", renderEndWorld);
        addVec(json, "renderCameraPosition", camera.pos);
        addVec(json, "deltaMovement", state.deltaMovement);
        if (anchor != null) {
            addAnchor(json, anchor);
            addVec(json, "bulletToMuzzleOffset", anchor.muzzleWorldPosition().subtract(state.bulletPosition));
            addProjection(json, "screenAnchorMuzzle", project(camera, anchor.muzzleWorldPosition()));
        }
        addProjection(json, "screenStart", project(camera, renderStartWorld));
        addProjection(json, "screenEnd", project(camera, renderEndWorld));
        write(json);
    }

    private static JsonObject base(String event) {
        JsonObject json = new JsonObject();
        json.addProperty("event", event);
        json.addProperty("sample", samples);
        json.addProperty("timeMillis", System.currentTimeMillis());
        return json;
    }

    private static void addAnchor(JsonObject json, FirstPersonTracerAnchor anchor) {
        json.addProperty("anchorSequence", anchor.sequence());
        json.addProperty("anchorGameTime", anchor.gameTime());
        json.addProperty("anchorPlayerId", anchor.playerId());
        addId(json, "anchorGunId", anchor.gunId());
        addId(json, "anchorDisplayId", anchor.displayId());
        addVec(json, "anchorLocalOffset", anchor.localOffset());
        addVec(json, "anchorWorldOffset", anchor.worldOffset());
        addVec(json, "anchorMuzzleWorldPosition", anchor.muzzleWorldPosition());
        addVec(json, "anchorCameraPosition", anchor.cameraPosition());
        addVec(json, "anchorCameraRight", anchor.cameraRight());
        addVec(json, "anchorCameraUp", anchor.cameraUp());
        addVec(json, "anchorCameraForward", anchor.cameraForward());
        json.addProperty("anchorCameraXRot", anchor.cameraXRot());
        json.addProperty("anchorCameraYRot", anchor.cameraYRot());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            json.addProperty("anchorAgeGameTicks", minecraft.level.getGameTime() - anchor.gameTime());
        }
    }

    private static void addId(JsonObject json, String key, @Nullable Identifier id) {
        if (id != null) {
            json.addProperty(key, id.toString());
        }
    }

    private static void addVec(JsonObject json, String key, Vector3f vec) {
        JsonObject value = new JsonObject();
        value.addProperty("x", vec.x);
        value.addProperty("y", vec.y);
        value.addProperty("z", vec.z);
        json.add(key, value);
    }

    private static void addVec(JsonObject json, String key, Vec3 vec) {
        JsonObject value = new JsonObject();
        value.addProperty("x", vec.x);
        value.addProperty("y", vec.y);
        value.addProperty("z", vec.z);
        json.add(key, value);
    }

    private static void addProjection(JsonObject json, String key, @Nullable Projection projection) {
        if (projection == null) {
            return;
        }
        JsonObject value = new JsonObject();
        value.addProperty("x", projection.x);
        value.addProperty("y", projection.y);
        value.addProperty("dx", projection.dx);
        value.addProperty("dy", projection.dy);
        value.addProperty("ndcZ", projection.ndcZ);
        json.add(key, value);
    }

    private static @Nullable Projection project(CameraRenderState camera, Vec3 world) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null) {
            return null;
        }
        Vector4f clip = new Vector4f(
                (float) (world.x - camera.pos.x),
                (float) (world.y - camera.pos.y),
                (float) (world.z - camera.pos.z),
                1.0F
        );
        camera.viewRotationMatrix.transform(clip);
        camera.projectionMatrix.transform(clip);
        if (!Float.isFinite(clip.w) || Math.abs(clip.w) < 1.0E-6F) {
            return null;
        }
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        float ndcZ = clip.z / clip.w;
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        double x = (ndcX * 0.5 + 0.5) * width;
        double y = (0.5 - ndcY * 0.5) * height;
        return new Projection(x, y, x - width / 2.0, y - height / 2.0, ndcZ);
    }

    private static synchronized void write(JsonObject json) {
        if (samples >= MAX_SAMPLES) {
            return;
        }
        try {
            if (writer == null) {
                Path parent = TRACE_PATH.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                writer = Files.newBufferedWriter(TRACE_PATH, StandardCharsets.UTF_8);
            }
            writer.write(json.toString());
            writer.newLine();
            writer.flush();
            samples++;
        } catch (IOException exception) {
            GunMod.LOGGER.warn("Failed to write TACZ tracer debug event to {}", TRACE_PATH, exception);
            samples = MAX_SAMPLES;
        }
    }

    private record Projection(double x, double y, double dx, double dy, float ndcZ) {
    }
}
