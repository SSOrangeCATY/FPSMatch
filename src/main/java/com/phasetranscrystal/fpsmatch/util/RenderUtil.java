package com.phasetranscrystal.fpsmatch.util;

import com.mojang.blaze3d.platform.NativeImage;
import com.phasetranscrystal.fpsmatch.client.data.ClientTaczTextureData;
import com.tacz.guns.client.resource_legacy.texture.FilePackTexture;
import com.tacz.guns.client.resource_legacy.texture.ZipPackTexture;
import icyllis.modernui.graphics.BitmapFactory;
import icyllis.modernui.graphics.Image;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

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

    public static Image getGunTextureByRL(ResourceLocation resourceLocation){
        Path texturePath;
        Image image = null;
        AbstractTexture abstractTexture = Minecraft.getInstance().textureManager.getTexture(resourceLocation);
        if(abstractTexture instanceof FilePackTexture pack){
            texturePath = ClientTaczTextureData.getPathByGunTexture(pack);
            if(texturePath != null ){
                File textureFile = texturePath.toFile();
                try (InputStream stream = Files.newInputStream(textureFile.toPath())){
                    image = Image.createTextureFromBitmap(BitmapFactory.decodeStream(stream));
                } catch (Exception e){
                    throw new RuntimeException(e);
                }
            }
        }

        if(abstractTexture instanceof ZipPackTexture pack) {
            texturePath = ClientTaczTextureData.getPathByGunTexture(pack);
            if (texturePath != null) {
                try (ZipFile zipFile = new ZipFile(texturePath.toFile())) {
                    ZipEntry entry = zipFile.getEntry(String.format("%s/textures/%s.png", resourceLocation.getNamespace(), resourceLocation.getPath()));
                    if (entry == null) {
                        return null;
                    }
                    InputStream stream = zipFile.getInputStream(entry);
                    image = Image.createTextureFromBitmap(BitmapFactory.decodeStream(stream));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return image;
    }
}
