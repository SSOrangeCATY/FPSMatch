package com.phasetranscrystal.fpsmatch.mixin;

import com.phasetranscrystal.fpsmatch.client.data.ClientTaczTextureData;
import com.tacz.guns.client.resource.texture.ZipPackTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

@Mixin(value = ZipPackTexture.class , remap = false)
public class MixinZipPackTexture {
    @Final
    @Shadow
    private ResourceLocation registerId;
    @Final
    @Shadow
    private Path zipFilePath;
    @Inject(method = {"doLoad()V"}, at = {@At("HEAD")})
    public void fpsMatch$onLoad$SavePath(CallbackInfo ci) {
        ClientTaczTextureData.textureZipPath.put(registerId,zipFilePath);
    }
}
