package com.phasetranscrystal.fpsmatch.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.phasetranscrystal.fpsmatch.client.data.ClientTaczTextureData;
import com.tacz.guns.client.resource_legacy.texture.FilePackTexture;
import com.tacz.guns.client.resource_legacy.texture.ZipPackTexture;
import icyllis.modernui.graphics.BitmapFactory;
import icyllis.modernui.graphics.Image;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class RenderUtil {
    public static int color(int r,int g,int b){
        return (((0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | ((b & 0xFF)));
    }

    public static int color(int r,int g,int b,int a){
        return (((a) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | ((b & 0xFF)));
    }


    public static void renderReverseTexture(GuiGraphics guiGraphics, ResourceLocation icon,
                                            int x, int y, int width, int height){
        renderIcon(guiGraphics,icon,x,y,width,height,true,false);
    }

    public static void renderIcon(GuiGraphics guiGraphics, ResourceLocation icon,
                                  int x, int y, int width, int height,
                                  boolean flipHorizontal, boolean flipVertical) {
        if (!flipHorizontal && !flipVertical) {
            guiGraphics.blit(icon, x, y, 0, 0, width, height, width, height);
            return;
        }

        // Calculate UV coordinates
        float minU = 0;
        float maxU = 1;
        float minV = 0;
        float maxV = 1;

        if (flipHorizontal) {
            float temp = minU;
            minU = maxU;
            maxU = temp;
        }

        if (flipVertical) {
            float temp = minV;
            minV = maxV;
            maxV = temp;
        }

        PoseStack poseStack = guiGraphics.pose();
        RenderSystem.setShaderTexture(0, icon);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        // 构建顶点
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(matrix, x, y, 0).uv(minU, minV).endVertex();
        buffer.vertex(matrix, x, y + height, 0).uv(minU, maxV).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0).uv(maxU, maxV).endVertex();
        buffer.vertex(matrix, x + width, y, 0).uv(maxU, minV).endVertex();

        BufferUploader.drawWithShader(buffer.end());
    }
}
