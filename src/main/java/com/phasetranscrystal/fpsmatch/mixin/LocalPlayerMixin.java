package com.phasetranscrystal.fpsmatch.mixin;

import com.mojang.authlib.GameProfile;
import com.phasetranscrystal.fpsmatch.core.item.IThrowEntityAble;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayer {

    public LocalPlayerMixin(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }


    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"
            )
    )
    private boolean redirectIsUsingItem(LocalPlayer instance) {
        ItemStack activeItem = instance.getUseItem();
        boolean flag = activeItem.getItem() instanceof IThrowEntityAble;
        return !flag && instance.isUsingItem();
    }

}