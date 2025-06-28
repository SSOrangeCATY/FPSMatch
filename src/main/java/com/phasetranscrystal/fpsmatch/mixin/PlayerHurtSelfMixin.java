package com.phasetranscrystal.fpsmatch.mixin;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerHurtSelfMixin {
    @Inject(method = "canHarmPlayer(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"), cancellable = true)
    public void canHarmPlayer(Player other, CallbackInfoReturnable<Boolean> cir) {
        if(other.level().isClientSide) return;
        if(FPSMCore.getInstance().getMapByPlayer(other) instanceof BaseMap){
            if(other.is((Player)(Object)this)){
                cir.setReturnValue(true);
            }
        }
    }
}
