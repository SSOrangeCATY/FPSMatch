package com.tacz.guns.util;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.compat.ar.ARCompat;
import com.tacz.guns.compat.optifine.OptifineCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.function.BiConsumer;

public final class RenderHelper {
    private static final ThreadLocal<SubmitNodeCollector> CURRENT_SUBMIT_NODE_COLLECTOR = new ThreadLocal<>();
    private static final ThreadLocal<Integer> CUSTOM_GEOMETRY_CALLBACK_DEPTH = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> DEFERRED_RENDERERS_SUPPRESSED = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> DEFERRED_FUNCTIONAL_RENDERER_COLLECTION = new ThreadLocal<>();
    private static final VertexConsumer NOOP_VERTEX_CONSUMER = new VertexConsumer() {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    };

    public static void withSubmitNodeCollector(SubmitNodeCollector collector, Runnable action) {
        SubmitNodeCollector previous = CURRENT_SUBMIT_NODE_COLLECTOR.get();
        CURRENT_SUBMIT_NODE_COLLECTOR.set(collector);
        try {
            action.run();
        } finally {
            if (previous == null) {
                CURRENT_SUBMIT_NODE_COLLECTOR.remove();
            } else {
                CURRENT_SUBMIT_NODE_COLLECTOR.set(previous);
            }
        }
    }

    public static SubmitNodeCollector currentSubmitNodeCollector() {
        return CURRENT_SUBMIT_NODE_COLLECTOR.get();
    }

    public static void submitCustomGeometry(SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType,
                                            BiConsumer<PoseStack.Pose, VertexConsumer> renderer) {
        if (collector == null) {
            return;
        }
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) ->
                withSubmitCustomGeometryContext(collector, () -> renderer.accept(pose, buffer)));
    }

    public static boolean isInsideSubmitCustomGeometryCallback() {
        Integer depth = CUSTOM_GEOMETRY_CALLBACK_DEPTH.get();
        return depth != null && depth > 0;
    }

    public static void withSubmitCustomGeometryContext(SubmitNodeCollector collector, Runnable action) {
        withSubmitNodeCollector(collector, () -> {
            Integer previousDepth = CUSTOM_GEOMETRY_CALLBACK_DEPTH.get();
            int depth = previousDepth == null ? 0 : previousDepth;
            CUSTOM_GEOMETRY_CALLBACK_DEPTH.set(depth + 1);
            try {
                action.run();
            } finally {
                if (previousDepth == null) {
                    CUSTOM_GEOMETRY_CALLBACK_DEPTH.remove();
                } else {
                    CUSTOM_GEOMETRY_CALLBACK_DEPTH.set(previousDepth);
                }
            }
        });
    }

    public static boolean areDeferredRenderersSuppressed() {
        return Boolean.TRUE.equals(DEFERRED_RENDERERS_SUPPRESSED.get());
    }

    public static void withDeferredRenderersSuppressed(Runnable action) {
        Boolean previous = DEFERRED_RENDERERS_SUPPRESSED.get();
        DEFERRED_RENDERERS_SUPPRESSED.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            if (previous == null) {
                DEFERRED_RENDERERS_SUPPRESSED.remove();
            } else {
                DEFERRED_RENDERERS_SUPPRESSED.set(previous);
            }
        }
    }

    public static boolean isCollectingDeferredFunctionalRenderers() {
        return Boolean.TRUE.equals(DEFERRED_FUNCTIONAL_RENDERER_COLLECTION.get());
    }

    public static void withDeferredFunctionalRendererCollection(Runnable action) {
        Boolean previous = DEFERRED_FUNCTIONAL_RENDERER_COLLECTION.get();
        DEFERRED_FUNCTIONAL_RENDERER_COLLECTION.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            if (previous == null) {
                DEFERRED_FUNCTIONAL_RENDERER_COLLECTION.remove();
            } else {
                DEFERRED_FUNCTIONAL_RENDERER_COLLECTION.set(previous);
            }
        }
    }

    public static VertexConsumer noopVertexConsumer() {
        return NOOP_VERTEX_CONSUMER;
    }

    public static void enableItemEntityStencilTest() {
        RenderSystem.assertOnRenderThread();
        if (OptifineCompat.isOptifineInstalled()) {
            // 以下代码用于应对 使用 optifine 的场景
            int depthTextureId = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            int stencilTextureId = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (depthTextureId != GL30.GL_NONE && stencilTextureId == GL30.GL_NONE) {
                GL30.glBindTexture(GL30.GL_TEXTURE_2D, depthTextureId);
                int dataType = GL30.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D, 0, GL30.GL_TEXTURE_DEPTH_TYPE);
                if (dataType == GL30.GL_UNSIGNED_NORMALIZED) {
                    int width = GL30.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D, 0, GL30.GL_TEXTURE_WIDTH);
                    int height = GL30.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D, 0, GL30.GL_TEXTURE_HEIGHT);
                    GlStateManager._texImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_DEPTH24_STENCIL8, width, height, 0, GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8, null);
                    GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, 3553, depthTextureId, 0);
                }
            }
        }
        GlStateManager._enableStencilTest();
    }

    public static void disableItemEntityStencilTest() {
        RenderSystem.assertOnRenderThread();
        resetStencilState();
        GlStateManager._disableStencilTest();
    }

    public static void resetStencilState() {
        stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
        stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        stencilMask(0xFF);
    }

    public static void stencilFunc(int func, int ref, int mask) {
        RenderSystem.assertOnRenderThread();
        GlStateManager._stencilFunc(func, ref, mask);
    }

    public static void stencilOp(int sfail, int dpfail, int dppass) {
        RenderSystem.assertOnRenderThread();
        GlStateManager._stencilOp(sfail, dpfail, dppass);
    }

    public static void stencilMask(int mask) {
        RenderSystem.assertOnRenderThread();
        GlStateManager._stencilMask(mask);
    }

    public static void clearStencilBuffer() {
        RenderSystem.assertOnRenderThread();
        GL11.glClearStencil(0);
        GlStateManager._clear(GL11.GL_STENCIL_BUFFER_BIT);
    }

    public static void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        RenderSystem.assertOnRenderThread();
        int writeMask = 0;
        if (red) {
            writeMask |= ColorTargetState.WRITE_RED;
        }
        if (green) {
            writeMask |= ColorTargetState.WRITE_GREEN;
        }
        if (blue) {
            writeMask |= ColorTargetState.WRITE_BLUE;
        }
        if (alpha) {
            writeMask |= ColorTargetState.WRITE_ALPHA;
        }
        GlStateManager._colorMask(writeMask);
    }

    public static void depthMask(boolean flag) {
        RenderSystem.assertOnRenderThread();
        GlStateManager._depthMask(flag);
    }

    public static void disableDepthTest() {
        RenderSystem.assertOnRenderThread();
        GlStateManager._disableDepthTest();
    }

    public static void enableDepthTest() {
        RenderSystem.assertOnRenderThread();
        GlStateManager._enableDepthTest();
    }

    public static void renderFirstPersonArm(LocalPlayer player, HumanoidArm hand, PoseStack matrixStack, int combinedLight) {
        SubmitNodeCollector submitNodeCollector = CURRENT_SUBMIT_NODE_COLLECTOR.get();
        if (player == null || submitNodeCollector == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher renderManager = mc.getEntityRenderDispatcher();
        AvatarRenderer<AbstractClientPlayer> renderer = renderManager.getPlayerRenderer(player);
        Identifier skinTexture = player.getSkin().body().texturePath();
        boolean sleeveVisible = player.isModelPartShown(hand == HumanoidArm.RIGHT ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);

        boolean accelerated = ARCompat.shouldAccelerate();
        if (accelerated) {
            ARCompat.setRenderingLevel();
        }
        try {
            if (hand == HumanoidArm.RIGHT) {
                renderer.renderRightHand(matrixStack, submitNodeCollector, combinedLight, skinTexture, sleeveVisible, player);
            } else {
                renderer.renderLeftHand(matrixStack, submitNodeCollector, combinedLight, skinTexture, sleeveVisible, player);
            }
        } finally {
            if (accelerated) {
                ARCompat.resetRenderingLevel();
            }
        }
    }
}
