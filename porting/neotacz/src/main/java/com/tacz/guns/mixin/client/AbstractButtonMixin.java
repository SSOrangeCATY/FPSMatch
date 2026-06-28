package com.tacz.guns.mixin.client;

import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractButton.class, remap = false)
public class AbstractButtonMixin {
    /**
     * 记录点击按钮的时间，后续方便给予射击冷却，防止点击按钮后误触开火
     */
    @Inject(method = "onClick", at = @At("HEAD"), remap = false)
    public void onClickHead(MouseButtonEvent event, boolean doubleClick, CallbackInfo ci) {
        LocalPlayerDataHolder.clientClickButtonTimestamp = System.currentTimeMillis();
    }
}
