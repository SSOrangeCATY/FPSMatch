package com.phasetranscrystal.fpsmatch.client.data;

import com.tacz.guns.client.resource.texture.FilePackTexture;
import com.tacz.guns.client.resource.texture.ZipPackTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ClientTaczTextureData {
    public static final Map<ResourceLocation, Path> textureFilePath = new HashMap<>();
    public static final Map<ResourceLocation, Path> textureZipPath = new HashMap<>();
    @Nullable
    public static Path getPathByGunTexture(FilePackTexture texture){
        return textureFilePath.getOrDefault(texture.getRegisterId(),null);
    }

    @Nullable
    public static Path getPathByGunTexture(ZipPackTexture texture){
        return textureZipPath.getOrDefault(texture.getRegisterId(),null);
    }

}
