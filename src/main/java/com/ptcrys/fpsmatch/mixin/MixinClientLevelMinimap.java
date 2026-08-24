package com.ptcrys.fpsmatch.mixin;

import com.ptcrys.fpsmatch.common.client.minimap.generated.MinecraftClientGeneratedMinimapRuntime;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client-loaded chunk lifecycle bridge for generated minimap tiles. */
@Mixin(ClientLevel.class)
public abstract class MixinClientLevelMinimap {
    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void fpsmatch$onChunkLoaded(ChunkPos position, CallbackInfo callback) {
        MinecraftClientGeneratedMinimapRuntime.instance().onChunkLoaded(
                (ClientLevel) (Object) this,
                position.x,
                position.z
        );
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void fpsmatch$onChunkUnloaded(
            LevelChunk chunk,
            CallbackInfo callback
    ) {
        MinecraftClientGeneratedMinimapRuntime.instance().onChunkUnloaded(
                (ClientLevel) (Object) this,
                chunk
        );
    }

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("TAIL")
    )
    private void fpsmatch$onBlockChanged(
            BlockPos position,
            BlockState state,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (callback.getReturnValueZ()) {
            MinecraftClientGeneratedMinimapRuntime.instance().onBlockChanged(
                    (ClientLevel) (Object) this,
                    position
            );
        }
    }
}
