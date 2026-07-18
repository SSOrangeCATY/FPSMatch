package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.view.PlaceholderKind;
import com.phasetranscrystal.fpsmatch.core.minimap.view.ShapeMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;

public final class NeoForgeGuiGraphicsMinimapDrawTarget
        implements GuiGraphicsMinimapDrawBackend.DrawTarget {
    private final GuiGraphicsExtractor graphics;
    private final MinimapTextureResolver textures;
    private boolean clipping;

    public NeoForgeGuiGraphicsMinimapDrawTarget(
            GuiGraphicsExtractor graphics,
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
        graphics.enableScissor(floor(x), floor(y), ceil(x + width), ceil(y + height));
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
        MinimapTextureResolver.TextureHandle texture = textures.resolve(textureKey).orElse(null);
        if (texture == null) {
            graphics.fill(
                    floor(x), floor(y), ceil(x + width), ceil(y + height),
                    withAlpha(0x30363F, opacity)
            );
            return;
        }
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture.location(),
                floor(x),
                floor(y),
                0f,
                0f,
                Math.max(1, ceil(width)),
                Math.max(1, ceil(height)),
                texture.width(),
                texture.height(),
                texture.width(),
                texture.height(),
                multiplyAlpha(0xFFFFFFFF, opacity)
        );
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
        Optional<MarkerTextureDrawPlan> texturePlan = MarkerTextureDrawPlan.create(
                x, y, yawDegrees, opacity, presentation
        );
        if (texturePlan.isPresent()) {
            MarkerTextureDrawPlan plan = texturePlan.orElseThrow();
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    Identifier.fromNamespaceAndPath(plan.textureId().namespace(), plan.textureId().path()),
                    floor(plan.left()), floor(plan.top()),
                    0f, 0f, plan.size(), plan.size(),
                    16, 16, 16, 16,
                    multiplyAlpha(0xFFFFFFFF, opacity)
            );
            return;
        }
        int color = withAlpha(adjacent ? 0x7FD4FF : 0xFFFFFF, opacity);
        graphics.fill(floor(x - 2), floor(y - 4), floor(x + 3), floor(y + 4), color);
        graphics.fill(floor(x - 4), floor(y - 4), floor(x + 5), floor(y - 2), color);
    }

    @Override
    public void label(String text, double x, double y, float opacity) {
        label(DisplayLabel.literal(text), x, y, 0xFFFFFFFF, 1.0, opacity);
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
        net.minecraft.network.chat.Component text = label.type() == DisplayLabel.Type.TRANSLATION
                ? net.minecraft.network.chat.Component.translatable(label.value())
                : net.minecraft.network.chat.Component.literal(label.value());
        int width = Minecraft.getInstance().font.width(text);
        graphics.text(
                Minecraft.getInstance().font,
                text,
                floor(x - width * scale * 0.5),
                floor(y - 4 * scale),
                multiplyAlpha(color, opacity),
                true
        );
    }

    @Override
    public void region(String regionId, double[] pointsXY, float opacity) {
        int color = withAlpha(0x72D3FF, opacity);
        for (int index = 0; index < pointsXY.length; index += 2) {
            int next = (index + 2) % pointsXY.length;
            int x1 = floor(pointsXY[index]);
            int y1 = floor(pointsXY[index + 1]);
            int x2 = floor(pointsXY[next]);
            int y2 = floor(pointsXY[next + 1]);
            graphics.fill(Math.min(x1, x2), Math.min(y1, y2),
                    Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
        }
    }

    @Override
    public void placeholder(PlaceholderKind placeholder, double centerX, double centerY) {
        String text = switch (placeholder) {
            case LOADING -> "Loading map";
            case STALE -> "Map updating";
            case ERROR -> "Map unavailable";
        };
        graphics.centeredText(
                Minecraft.getInstance().font,
                text,
                floor(centerX),
                floor(centerY - 4),
                0xFFE5E7EB
        );
    }

    @Override
    public void end() {
        if (clipping) {
            graphics.disableScissor();
            clipping = false;
        }
    }

    private static int withAlpha(int rgb, float opacity) {
        int alpha = Math.max(0, Math.min(255, Math.round(opacity * 255f)));
        return alpha << 24 | rgb & 0x00FFFFFF;
    }

    private static int multiplyAlpha(int argb, float opacity) {
        int alpha = Math.max(0, Math.min(255, Math.round((argb >>> 24) * opacity)));
        return alpha << 24 | argb & 0x00FFFFFF;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int ceil(double value) {
        return (int) Math.ceil(value);
    }
}
