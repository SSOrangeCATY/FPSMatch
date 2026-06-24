package com.phasetranscrystal.fpsmatch.mixin.accessor;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = SoundEngine.class, remap = false)
public interface SoundEngineAccessor {
    @Accessor(value = "instanceToChannel", remap = false)
    Map<SoundInstance, ChannelAccess.ChannelHandle> getInstanceToChannel();
}
