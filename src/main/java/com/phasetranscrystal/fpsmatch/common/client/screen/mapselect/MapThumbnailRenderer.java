package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 地图缩略图/预览图渲染器。
 * <p>
 * 渲染优先级：
 * <ol>
 *   <li>若提供贴图路径且资源已注册 → 绘制贴图（等比拉伸填充）</li>
 *   <li>否则 → 绘制柔和渐变色块 + 模式标识</li>
 * </ol>
 * <p>
 * 色块设计原则（甲方要求"看着舒服、颜色不单一"）：
 * <ul>
 *   <li>纵向双色渐变，营造层次感</li>
 *   <li>饱和度低、明度中低，避免鲜艳刺眼</li>
 *   <li>基于 mapName+gameType 哈希确定性分配，同一地图颜色稳定</li>
 *   <li>8 组预设色对，覆盖蓝/紫/青/绿/棕/红/灰/金系</li>
 * </ul>
 */
public final class MapThumbnailRenderer {
    private MapThumbnailRenderer() {
    }

    /**
     * 8 组柔和渐变色对（顶部深色 → 底部浅色）。
     * 均经过降饱和处理，避免刺眼。
     */
    private static final int[][] GRADIENT_PAIRS = {
            {0xFF2C3E50, 0xFF4A6890}, // 0 深蓝灰
            {0xFF3D2E4A, 0xFF6B4F7D}, // 1 深紫灰
            {0xFF1F3A3D, 0xFF3A6166}, // 2 深青灰
            {0xFF2A3D2A, 0xFF4A6B4A}, // 3 深绿灰
            {0xFF3D2E22, 0xFF6B4F36}, // 4 深棕灰
            {0xFF3D2222, 0xFF6B3636}, // 5 深红灰
            {0xFF2A2A2A, 0xFF4A4A4A}, // 6 深石灰
            {0xFF3D3522, 0xFF6B5E36}, // 7 深金灰
    };

    /**
     * 渲染地图缩略图。
     *
     * @param graphics      GuiGraphics
     * @param x             左上 x
     * @param y             左上 y
     * @param width         宽
     * @param height        高
     * @param texturePath   贴图资源路径，空串表示无贴图
     * @param mapName       地图内部名（用于色块哈希）
     * @param gameType      游戏类型（用于色块哈希 + 模式标识）
     * @param displayName   显示名（色块底部叠加）
     * @param showLabel     是否在色块底部叠加地图名标签
     */
    public static void render(GuiGraphics graphics, int x, int y, int width, int height,
                              String texturePath, String mapName, String gameType,
                              String displayName, boolean showLabel) {
        if (texturePath != null && !texturePath.isEmpty() && tryRenderTexture(graphics, x, y, width, height, texturePath)) {
            // 贴图渲染成功，叠加底部暗角与标签
            if (showLabel) {
                drawBottomLabel(graphics, x, y, width, height, displayName);
            }
            return;
        }
        // 色块兜底
        renderGradientBlock(graphics, x, y, width, height, mapName, gameType, displayName, showLabel);
    }

    /**
     * 尝试渲染贴图。返回 false 表示资源未注册或加载失败。
     */
    private static boolean tryRenderTexture(GuiGraphics graphics, int x, int y, int width, int height, String texturePath) {
        try {
            ResourceLocation rl = new ResourceLocation(texturePath);
            // 检查资源是否已注册（避免日志报错刷屏）
            Minecraft mc = Minecraft.getInstance();
            if (mc.getResourceManager().getResource(rl).isEmpty()) {
                return false;
            }
            RenderSystem.enableBlend();
            graphics.blit(rl, x, y, 0, 0, width, height, width, height);
            RenderSystem.disableBlend();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 渲染柔和渐变色块 + 模式标识 + 暗角。
     */
    private static void renderGradientBlock(GuiGraphics graphics, int x, int y, int width, int height,
                                            String mapName, String gameType, String displayName, boolean showLabel) {
        int[] colors = getGradientColors(mapName, gameType);
        int topColor = colors[0];
        int bottomColor = colors[1];

        // ① 纵向渐变（8 段离散 fill，避免 Shader 复杂度）
        int segments = 8;
        int segHeight = height / segments;
        int remainder = height % segments;
        int curY = y;
        for (int i = 0; i < segments; i++) {
            int h = segHeight + (i < remainder ? 1 : 0);
            float ratio = segments == 1 ? 0f : (float) i / (segments - 1);
            int color = lerpColor(topColor, bottomColor, ratio);
            graphics.fill(x, curY, x + width, curY + h, color);
            curY += h;
        }

        // ② 四角暗角（半透明黑，增加聚焦感）
        drawVignette(graphics, x, y, width, height);

        // ③ 模式标识（中央文字，半透明）
        String modeLabel = modeLabel(gameType);
        if (modeLabel != null && !modeLabel.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            int labelWidth = mc.font.width(modeLabel);
            int labelX = x + (width - labelWidth) / 2;
            int labelY = y + (height - mc.font.lineHeight) / 2;
            // 文字阴影
            graphics.drawString(mc.font, modeLabel, labelX + 1, labelY + 1, 0x66000000, false);
            graphics.drawString(mc.font, modeLabel, labelX, labelY, 0x99FFFFFF, false);
        }

        // ④ 底部地图名标签
        if (showLabel) {
            drawBottomLabel(graphics, x, y, width, height, displayName);
        }
    }

    /**
     * 绘制底部半透明黑条 + 地图名（用于贴图和色块两种场景）。
     */
    private static void drawBottomLabel(GuiGraphics graphics, int x, int y, int width, int height, String displayName) {
        if (displayName == null || displayName.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        int labelHeight = Math.min(14, mc.font.lineHeight + 4);
        int labelY = y + height - labelHeight;
        // 半透明黑条
        graphics.fill(x, labelY, x + width, y + height, 0x99000000);
        // 居中文字
        int textWidth = mc.font.width(displayName);
        int textX = x + (width - textWidth) / 2;
        int textY = labelY + (labelHeight - mc.font.lineHeight) / 2;
        graphics.drawString(mc.font, displayName, textX, textY, 0xFFFFFFFF, false);
    }

    /**
     * 四角暗角：在四角各画一个半透明黑渐变块。
     * 简化实现：四角各填充一个 20% 尺寸的半透明黑矩形。
     */
    private static void drawVignette(GuiGraphics graphics, int x, int y, int width, int height) {
        int cornerW = Math.max(8, width / 5);
        int cornerH = Math.max(8, height / 5);
        int alpha = 0x55000000;
        // 左上
        graphics.fill(x, y, x + cornerW, y + cornerH, alpha);
        // 右上
        graphics.fill(x + width - cornerW, y, x + width, y + cornerH, alpha);
        // 左下
        graphics.fill(x, y + height - cornerH, x + cornerW, y + height, alpha);
        // 右下
        graphics.fill(x + width - cornerW, y + height - cornerH, x + width, y + height, alpha);
    }

    /**
     * 基于 mapName + gameType 哈希确定性选取色对索引。
     */
    public static int[] getGradientColors(String mapName, String gameType) {
        int hash = (mapName == null ? 0 : mapName.hashCode()) ^ (gameType == null ? 0 : gameType.hashCode());
        int index = Math.floorMod(hash, GRADIENT_PAIRS.length);
        return GRADIENT_PAIRS[index];
    }

    /**
     * 模式标识文字。无贴图时叠加在色块中央。
     */
    private static String modeLabel(String gameType) {
        if (gameType == null) return "";
        return switch (gameType) {
            case "cs" -> "CS";
            case "csdm" -> "DM";
            default -> gameType.length() >= 2 ? gameType.substring(0, 2).toUpperCase() : gameType.toUpperCase();
        };
    }

    /**
     * ARGB 颜色线性插值。
     */
    private static int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF;
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
