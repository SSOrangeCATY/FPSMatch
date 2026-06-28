package com.tacz.guns.client.debug;

import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScopeRenderDebug {
    private static final boolean ENABLED = Boolean.getBoolean("tacz.debug.scopeRender");
    private static final int MAX_SAMPLES = Integer.getInteger("tacz.debug.scope.maxSamples", 8192);
    private static final Path TRACE_PATH = Path.of(System.getProperty("tacz.debug.scope.file", "tacz-scope-render.jsonl"));
    private static final String RUN_ID = UUID.randomUUID().toString();
    private static final Set<String> REPORTED_PATHS = ConcurrentHashMap.newKeySet();
    private static BufferedWriter writer;
    private static int samples;

    private ScopeRenderDebug() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static void stage(String stage,
                             @Nullable ItemStack attachmentItem,
                             @Nullable ItemStack gunItem,
                             ItemDisplayContext transformType,
                             boolean isScope,
                             boolean isSight,
                             boolean selective,
                             float aimingProgress,
                             float apertureRadius,
                             String stencilFunc,
                             String stencilOp,
                             boolean submitted,
                             String blockedReason) {
        stageDetailed(stage, attachmentItem, gunItem, transformType, isScope, isSight, selective, aimingProgress,
                apertureRadius, stencilFunc, stencilOp, -1, "", -1, -1, "", "", "none",
                submitted, blockedReason);
    }

    public static void stageDetailed(String stage,
                                     @Nullable ItemStack attachmentItem,
                                     @Nullable ItemStack gunItem,
                                     ItemDisplayContext transformType,
                                     boolean isScope,
                                     boolean isSight,
                                     boolean selective,
                                     float aimingProgress,
                                     float apertureRadius,
                                     String stencilFunc,
                                     String stencilOp,
                                     int order,
                                     String renderType,
                                     int stencilRef,
                                     int ocularIndex,
                                     String ocularPathKind,
                                     String scopeKind,
                                     String gunClipMode,
                                     boolean submitted,
                                     String blockedReason) {
        if (!ENABLED) {
            return;
        }
        JsonObject json = base("scope_stage");
        json.addProperty("stage", stage);
        addIds(json, attachmentItem, gunItem);
        json.addProperty("transformType", transformType.name());
        json.addProperty("firstPerson", transformType.firstPerson());
        json.addProperty("isScope", isScope);
        json.addProperty("isSight", isSight);
        json.addProperty("selective", selective);
        json.addProperty("aimingProgress", aimingProgress);
        json.addProperty("apertureRadius", apertureRadius);
        json.addProperty("stencilFunc", stencilFunc);
        json.addProperty("stencilOp", stencilOp);
        if (order != -1) {
            json.addProperty("order", order);
        }
        if (!renderType.isEmpty()) {
            json.addProperty("renderType", renderType);
        }
        if (stencilRef >= 0) {
            json.addProperty("stencilRef", stencilRef);
        }
        if (ocularIndex >= 0) {
            json.addProperty("ocularIndex", ocularIndex);
        }
        if (!ocularPathKind.isEmpty()) {
            json.addProperty("ocularPathKind", ocularPathKind);
        }
        if (!scopeKind.isEmpty()) {
            json.addProperty("scopeKind", scopeKind);
        }
        if (!gunClipMode.isEmpty()) {
            json.addProperty("gunClipMode", gunClipMode);
        }
        json.addProperty("submitted", submitted);
        if (!blockedReason.isEmpty()) {
            json.addProperty("blockedReason", blockedReason);
        }
        write(json);
    }

    public static void autoTest(String phase,
                                @Nullable Identifier scopeId,
                                float aimingProgress,
                                @Nullable ItemStack gunItem) {
        if (!ENABLED) {
            return;
        }
        JsonObject json = base("scope_autotest");
        json.addProperty("phase", phase);
        if (scopeId != null) {
            json.addProperty("scopeId", scopeId.toString());
        }
        json.addProperty("aimingProgress", aimingProgress);
        addIds(json, null, gunItem);
        if (gunItem != null && gunItem.getItem() instanceof IGun gun) {
            Identifier attachedScopeId = gun.getAttachmentId(gunItem, AttachmentType.SCOPE);
            if (attachedScopeId != null) {
                json.addProperty("attachedScopeId", attachedScopeId.toString());
            }
            Identifier gunId = gun.getGunId(gunItem);
            if (gunId != null && attachedScopeId != null) {
                json.addProperty("caseId", gunId + "," + attachedScopeId);
            }
        }
        write(json);
    }

    public static void resolvedAttachment(@Nullable ItemStack attachmentItem,
                                          @Nullable ItemStack gunItem,
                                          ItemDisplayContext transformType,
                                          @Nullable ClientAttachmentIndex index,
                                          @Nullable Identifier resolvedTexture,
                                          boolean modelPresent,
                                          boolean submitted,
                                          String blockedReason) {
        if (!ENABLED) {
            return;
        }
        JsonObject json = base("scope_resolved_attachment");
        json.addProperty("transformType", transformType.name());
        json.addProperty("firstPerson", transformType.firstPerson());
        addIds(json, attachmentItem, gunItem);
        json.addProperty("modelPresent", modelPresent);
        json.addProperty("submitted", submitted);
        if (index != null) {
            Identifier displayId = index.getDisplayId();
            Identifier displayModel = index.getDisplayModelLocation();
            Identifier displayTexture = index.getDisplayTextureLocation();
            if (displayId != null) {
                json.addProperty("displayId", displayId.toString());
            }
            if (displayModel != null) {
                json.addProperty("displayModel", displayModel.toString());
            }
            if (displayTexture != null) {
                json.addProperty("displayTexture", displayTexture.toString());
            }
            json.addProperty("scopeKind", index.getVisualKind());
            json.addProperty("isScope", index.isScope());
            json.addProperty("isSight", index.isSight());
        }
        if (resolvedTexture != null) {
            json.addProperty("resolvedTexture", resolvedTexture.toString());
        }
        if (!blockedReason.isEmpty()) {
            json.addProperty("blockedReason", blockedReason);
        }
        write(json);
    }

    public static void firstPersonTransform(@Nullable ItemStack gunItem,
                                            float rawAimingProgress,
                                            float dynamicAimingProgress,
                                            float refitScreenOpeningProgress,
                                            @Nullable Identifier scopeId,
                                            int zoomNumber,
                                            int viewIndex,
                                            int currentViewIndex,
                                            int scopeViewPathCount,
                                            boolean scopePosPathPresent,
                                            boolean scopeViewPathPresent,
                                            int aimingPathSize,
                                            float matrixM30,
                                            float matrixM31,
                                            float matrixM32) {
        if (!ENABLED) {
            return;
        }
        JsonObject json = base("scope_first_person_transform");
        addIds(json, null, gunItem);
        json.addProperty("rawAimingProgress", rawAimingProgress);
        json.addProperty("dynamicAimingProgress", dynamicAimingProgress);
        json.addProperty("refitScreenOpeningProgress", refitScreenOpeningProgress);
        if (scopeId != null) {
            json.addProperty("scopeId", scopeId.toString());
        }
        json.addProperty("zoomNumber", zoomNumber);
        json.addProperty("viewIndex", viewIndex);
        json.addProperty("currentViewIndex", currentViewIndex);
        json.addProperty("scopeViewPathCount", scopeViewPathCount);
        json.addProperty("scopePosPathPresent", scopePosPathPresent);
        json.addProperty("scopeViewPathPresent", scopeViewPathPresent);
        json.addProperty("aimingPathSize", aimingPathSize);
        json.addProperty("matrixM30", matrixM30);
        json.addProperty("matrixM31", matrixM31);
        json.addProperty("matrixM32", matrixM32);
        write(json);
    }

    public static void fov(String phase,
                           @Nullable ItemStack gunItem,
                           float vanillaFov,
                           float resultFov,
                           float aimingProgress,
                           float zoom,
                           float selectedModelFov,
                           @Nullable Identifier scopeId,
                           int zoomNumber,
                           String source) {
        if (!ENABLED) {
            return;
        }
        JsonObject json = base("scope_fov");
        json.addProperty("phase", phase);
        addIds(json, null, gunItem);
        json.addProperty("vanillaFov", vanillaFov);
        json.addProperty("resultFov", resultFov);
        json.addProperty("aimingProgress", aimingProgress);
        json.addProperty("zoom", zoom);
        json.addProperty("selectedModelFov", selectedModelFov);
        if (scopeId != null) {
            json.addProperty("scopeId", scopeId.toString());
        }
        json.addProperty("zoomNumber", zoomNumber);
        if (!source.isEmpty()) {
            json.addProperty("source", source);
        }
        write(json);
    }

    public static void path(String path,
                            @Nullable ItemStack attachmentItem,
                            @Nullable ItemStack gunItem,
                            ItemDisplayContext transformType,
                            String detail) {
        if (!ENABLED) {
            return;
        }
        Identifier gunId = null;
        Identifier attachedScopeId = null;
        if (gunItem != null && gunItem.getItem() instanceof IGun gun) {
            gunId = gun.getGunId(gunItem);
            attachedScopeId = gun.getAttachmentId(gunItem, AttachmentType.SCOPE);
        }
        Identifier attachmentId = null;
        if (attachmentItem != null && attachmentItem.getItem() instanceof IAttachment attachment) {
            attachmentId = attachment.getAttachmentId(attachmentItem);
        }
        String key = path + "|" + transformType + "|" + gunId + "|" + attachedScopeId + "|" + attachmentId + "|" + detail;
        if (!REPORTED_PATHS.add(key)) {
            return;
        }
        JsonObject json = base("scope_path");
        json.addProperty("path", path);
        json.addProperty("transformType", transformType.name());
        json.addProperty("firstPerson", transformType.firstPerson());
        if (!detail.isEmpty()) {
            json.addProperty("detail", detail);
        }
        addIds(json, attachmentItem, gunItem);
        if (attachedScopeId != null) {
            json.addProperty("attachedScopeId", attachedScopeId.toString());
        }
        write(json);
    }

    private static JsonObject base(String event) {
        JsonObject json = new JsonObject();
        json.addProperty("event", event);
        json.addProperty("runId", RUN_ID);
        json.addProperty("sample", samples);
        json.addProperty("timeMillis", System.currentTimeMillis());
        addViewState(json);
        return json;
    }

    public static void apertureFan(@Nullable ItemStack attachmentItem,
                                   @Nullable ItemStack gunItem,
                                   ItemDisplayContext transformType,
                                   int stencilRef,
                                   int ocularIndex,
                                   String ocularPathKind,
                                   float centerX,
                                   float centerY,
                                   float radius,
                                   int modelViewHash,
                                   float modelViewDeterminant,
                                   boolean submitted,
                                   String blockedReason) {
        if (!ENABLED) {
            return;
        }
        JsonObject json = base("scope_aperture_fan_space");
        addIds(json, attachmentItem, gunItem);
        json.addProperty("transformType", transformType.name());
        json.addProperty("firstPerson", transformType.firstPerson());
        json.addProperty("stencilRef", stencilRef);
        json.addProperty("ocularIndex", ocularIndex);
        json.addProperty("ocularPathKind", ocularPathKind);
        json.addProperty("fanCenterX", centerX);
        json.addProperty("fanCenterY", centerY);
        json.addProperty("apertureRadius", radius);
        json.addProperty("modelViewHash", String.format("%08x", modelViewHash));
        json.addProperty("modelViewDeterminant", modelViewDeterminant);
        json.addProperty("space", "view_plane_inverse_model_view");
        json.addProperty("submitted", submitted);
        if (!blockedReason.isEmpty()) {
            json.addProperty("blockedReason", blockedReason);
        }
        write(json);
    }

    private static void addViewState(JsonObject json) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null) {
            json.addProperty("yaw", player.getYRot());
            json.addProperty("pitch", player.getXRot());
            json.addProperty("yHeadRot", player.getYHeadRot());
            json.addProperty("yBodyRot", player.yBodyRot);
            json.addProperty("x", player.getX());
            json.addProperty("y", player.getY());
            json.addProperty("z", player.getZ());
        }
        if (minecraft.options != null) {
            json.addProperty("bobView", minecraft.options.bobView().get());
            json.addProperty("clientFovSetting", minecraft.options.fov().get());
            json.addProperty("cameraType", minecraft.options.getCameraType().name());
        }
    }

    private static void addIds(JsonObject json, @Nullable ItemStack attachmentItem, @Nullable ItemStack gunItem) {
        Identifier attachmentId = null;
        if (attachmentItem != null && attachmentItem.getItem() instanceof IAttachment attachment) {
            attachmentId = attachment.getAttachmentId(attachmentItem);
        }
        if (attachmentId != null) {
            json.addProperty("attachmentId", attachmentId.toString());
        }
        Identifier gunId = null;
        if (gunItem != null && gunItem.getItem() instanceof IGun gun) {
            gunId = gun.getGunId(gunItem);
        }
        if (gunId != null) {
            json.addProperty("gunId", gunId.toString());
        }
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
            GunMod.LOGGER.warn("Failed to write TACZ scope debug event to {}", TRACE_PATH, exception);
            samples = MAX_SAMPLES;
        }
    }
}
