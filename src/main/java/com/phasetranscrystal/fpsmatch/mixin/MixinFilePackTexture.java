package com.phasetranscrystal.fpsmatch.mixin;

import com.phasetranscrystal.fpsmatch.client.data.ClientTaczTextureData;
import com.tacz.guns.client.resource.texture.FilePackTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

@Mixin(value = FilePackTexture.class , remap = false)
public class MixinFilePackTexture {
    @Final
    @Shadow
    private ResourceLocation registerId;
    @Final
    @Shadow
    private Path filePath;
    @Inject(method = {"doLoad()V"}, at = {@At("HEAD")})
    public void fpsMatch$onLoad$SavePath(CallbackInfo ci) {
        ClientTaczTextureData.textureFilePath.put(registerId,filePath);
    }
}
