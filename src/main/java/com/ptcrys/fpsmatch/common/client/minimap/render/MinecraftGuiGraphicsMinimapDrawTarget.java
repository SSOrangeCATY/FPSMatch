package com.ptcrys.fpsmatch.common.client.minimap.render;

import com.mojang.math.Axis;
import com.ptcrys.fpsmatch.core.minimap.view.PlaceholderKind;
import com.ptcrys.fpsmatch.core.minimap.view.ShapeMode;
import com.ptcrys.fpsmatch.core.minimap.model.DisplayLabel;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

public final class MinecraftGuiGraphicsMinimapDrawTarget
        implements GuiGraphicsMinimapDrawBackend.DrawTarget {
    private final GuiGraphics graphics;
    private final MinimapTextureResolver textures;
    private boolean clipping;

    public MinecraftGuiGraphicsMinimapDrawTarget(
            GuiGraphics graphics,
            MinimapTextureResolver textures
    ) {
        this.graphics = Objects.requireNonNull(graphics, "graphics");
        this.textures = Objects.requireNonNull(textures, "textures");
    }

    @Override
    public void begin(
            ShapeMode shape,
            double x,
            double y,
            double width,
            double height,
            float backgroundOpacity
    ) {
        Objects.requireNonNull(shape, "shape");
        graphics.enableScissor(
                floor(x),
                floor(y),
                ceil(x + width),
                ceil(y + height)
        );
        clipping = true;
        graphics.fill(
                floor(x), floor(y), ceil(x + width), ceil(y + height),
                withAlpha(0x181D24, backgroundOpacity)
        );
    }

    @Override
    public void texture(
            String textureKey,
            double x,
            double y,
            double width,
            double height,
            float opacity,
            float rotationDegrees,
            double rotationCenterX,
            double rotationCenterY
    ) {
        MinimapTextureResolver.TextureHandle texture = textures.resolve(textureKey)
                .orElse(null);
        if (texture == null) {
            graphics.fill(
                    floor(x), floor(y), ceil(x + width), ceil(y + height),
                    withAlpha(0x30363F, opacity)
            );
            return;
        }
        graphics.pose().pushPose();
        try {
            if (rotationDegrees != 0f) {
                graphics.pose().translate(rotationCenterX, rotationCenterY, 0);
                graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotationDegrees));
                graphics.pose().translate(-rotationCenterX, -rotationCenterY, 0);
            }
            graphics.setColor(1f, 1f, 1f, opacity);
            graphics.blit(
                    texture.location(),
                    floor(x),
                    floor(y),
                    Math.max(1, ceil(width)),
                    Math.max(1, ceil(height)),
                    0f,
                    0f,
                    texture.width(),
                    texture.height(),
                    texture.width(),
                    texture.height()
            );
        } finally {
            graphics.setColor(1f, 1f, 1f, 1f);
            graphics.pose().popPose();
        }
    }

    @Override
    public void marker(
            String markerId,
            NamespacedId typeId,
            NamespacedId styleId,
            double x,
            double y,
            float yawDegrees,
            float opacity,
            boolean adjacent,
            Optional<MarkerPresentationResolver.Resolved> presentation
    ) {
        Optional<MarkerTextureDrawPlan> texturePlan =
                MarkerTextureDrawPlan.create(
                        x, y, yawDegrees, opacity, presentation
                );
        if (texturePlan.isPresent()) {
            drawMarkerTexture(texturePlan.orElseThrow(), x, y);
            return;
        }
        int color = withAlpha(adjacent ? 0x7FD4FF : 0xFFFFFF, opacity);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(yawDegrees));
            graphics.fill(-2, -4, 3, 4, color);
            graphics.fill(-4, -4, 5, -2, color);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void drawMarkerTexture(
            MarkerTextureDrawPlan plan,
            double centerX,
            double centerY
    ) {
        ResourceLocation texture = ResourceLocation.tryBuild(
                plan.textureId().namespace(), plan.textureId().path()
        );
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(centerX, centerY, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(plan.yawDegrees()));
            graphics.pose().translate(-centerX, -centerY, 0);
            graphics.setColor(1f, 1f, 1f, plan.opacity());
            graphics.blit(
                    texture,
                    plan.left(),
                    plan.top(),
                    plan.size(),
                    plan.size(),
                    0f,
                    0f,
                    16,
                    16,
                    16,
                    16
            );
        } finally {
            graphics.setColor(1f, 1f, 1f, 1f);
            graphics.pose().popPose();
        }
    }

    @Override
    public void label(String text, double x, double y, float opacity) {
        label(
                DisplayLabel.literal(text),
                x,
                y,
                0xFFFFFFFF,
                1.0,
                opacity
        );
    }

    @Override
    public void label(
            DisplayLabel label,
            double x,
            double y,
            int color,
            double scale,
            float opacity
    ) {
        net.minecraft.network.chat.Component text =
                label.type() == DisplayLabel.Type.TRANSLATION
                        ? net.minecraft.network.chat.Component.translatable(
                        label.value()
                )
                        : net.minecraft.network.chat.Component.literal(
                        label.value()
                );
        int resolvedColor = multiplyAlpha(color, opacity);
        int width = Minecraft.getInstance().font.width(text);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale((float) scale, (float) scale, 1f);
            int left = floor(-width * 0.5) - 2;
            graphics.fill(left, -6, left + width + 4, 6, 0xA8000000);
            graphics.drawString(
                    Minecraft.getInstance().font,
                    text,
                    left + 2,
                    -4,
                    resolvedColor,
                    true
            );
        } finally {
            graphics.pose().popPose();
        }
    }

    @Override
    public void region(
            String regionId,
            double[] pointsXY,
            float opacity
    ) {
        int color = withAlpha(0x72D3FF, opacity);
        for (int index = 0; index < pointsXY.length; index += 2) {
            int next = (index + 2) % pointsXY.length;
            line(
                    pointsXY[index], pointsXY[index + 1],
                    pointsXY[next], pointsXY[next + 1],
                    color
            );
        }
    }

    @Override
    public void placeholder(
            PlaceholderKind placeholder,
            double centerX,
            double centerY
    ) {
        Component text = switch (placeholder) {
            case LOADING -> Component.translatable(
                    "gui.fpsm.minimap.placeholder.loading"
            );
            case STALE -> Component.translatable(
                    "gui.fpsm.minimap.placeholder.stale"
            );
            case ERROR -> Component.translatable(
                    "gui.fpsm.minimap.placeholder.error"
            );
        };
        int width = Minecraft.getInstance().font.width(text);
        graphics.drawString(
                Minecraft.getInstance().font,
                text,
                floor(centerX - width * 0.5),
                floor(centerY - 4),
                0xFFE5E7EB,
                true
        );
    }

    @Override
    public void floorLabel(
            DisplayLabel label,
            double centerX,
            double baselineY
    ) {
        label(label, centerX, baselineY, 0xFFFFFFFF, 0.75, 1f);
    }

    @Override
    public void compass(
            float rotationDegrees,
            double centerX,
            double centerY
    ) {
        Component north = Component.translatable(
                "gui.fpsm.minimap.compass.north"
        );
        int width = Minecraft.getInstance().font.width(north);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(centerX, centerY, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotationDegrees));
            graphics.fill(-1, -7, 2, -1, 0xFFE6F4FF);
            graphics.drawString(
                    Minecraft.getInstance().font,
                    north,
                    -width / 2,
                    1,
                    0xFFFFFFFF,
                    true
            );
        } finally {
            graphics.pose().popPose();
        }
    }

    @Override
    public void end() {
        if (clipping) {
            graphics.disableScissor();
            clipping = false;
        }
    }

    private void line(
            double x1,
            double y1,
            double x2,
            double y2,
            int color
    ) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.hypot(dx, dy);
        if (length < 0.5) {
            return;
        }
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x1, y1, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(
                    (float) Math.toDegrees(Math.atan2(dy, dx))
            ));
            graphics.fill(0, 0, Math.max(1, ceil(length)), 1, color);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static int withAlpha(int rgb, float opacity) {
        int alpha = Math.max(0, Math.min(255, Math.round(opacity * 255f)));
        return alpha << 24 | rgb & 0x00FFFFFF;
    }

    private static int multiplyAlpha(int argb, float opacity) {
        int sourceAlpha = argb >>> 24;
        int alpha = Math.max(
                0,
                Math.min(255, Math.round(sourceAlpha * opacity))
        );
        return alpha << 24 | argb & 0x00FFFFFF;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int ceil(double value) {
        return (int) Math.ceil(value);
    }
}
