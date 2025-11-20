package com.phasetranscrystal.fpsmatch.mixin;

import com.mojang.authlib.GameProfile;
import com.phasetranscrystal.fpsmatch.core.item.IThrowEntityAble;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LocalPlayer.class, priority = 800)
public abstract class LocalPlayerMixin extends AbstractClientPlayer {

    public LocalPlayerMixin(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(
            method = "isUsingItem",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fpsm$IsUsingItem(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer instance = (LocalPlayer) (Object) this;
        boolean flag = instance.getUseItem().getItem() instanceof IThrowEntityAble;
        cir.setReturnValue(!flag && cir.getReturnValue());
    }
}