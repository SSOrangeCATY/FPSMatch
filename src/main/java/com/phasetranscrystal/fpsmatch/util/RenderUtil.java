package com.phasetranscrystal.fpsmatch.util;

import com.phasetranscrystal.fpsmatch.client.data.ClientTaczTextureData;
import com.tacz.guns.client.resource.texture.FilePackTexture;
import com.tacz.guns.client.resource.texture.ZipPackTexture;
import icyllis.modernui.graphics.BitmapFactory;
import icyllis.modernui.graphics.Image;
import net.minecraft.Optionull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.PlayerTeam;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        if(Minecraft.getInstance().textureManager.getTexture(resourceLocation) instanceof FilePackTexture pack){
            texturePath = ClientTaczTextureData.getPathByGunTexture(pack);
            if(texturePath != null ){
                File textureFile = texturePath.toFile();
                String path = textureFile.getPath();
                try (InputStream stream = new FileInputStream(path.replace("\\","/"))){
                    image = Image.createTextureFromBitmap(BitmapFactory.decodeStream(stream));
                } catch (Exception e){
                    throw new RuntimeException(e);
                }
            }
        }

        if(Minecraft.getInstance().textureManager.getTexture(resourceLocation) instanceof ZipPackTexture pack) {
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


    public static final Comparator<PlayerInfo> PLAYER_COMPARATOR = Comparator.<PlayerInfo>comparingInt((playerInfo) -> 0)
            .thenComparing((playerInfo) -> Optionull.mapOrDefault(playerInfo.getTeam(), PlayerTeam::getName, ""))
            .thenComparing((playerInfo) -> playerInfo.getProfile().getName(), String::compareToIgnoreCase);


    public static List<PlayerInfo> getPlayerInfos() {
        if (Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.connection.getListedOnlinePlayers().stream().sorted(PLAYER_COMPARATOR).limit(80L).toList();
        }
        return new ArrayList<>();
    }


}
