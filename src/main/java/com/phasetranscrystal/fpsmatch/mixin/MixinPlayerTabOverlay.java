package com.phasetranscrystal.fpsmatch.mixin;

import com.phasetranscrystal.fpsmatch.common.client.FPSMGameHudManager;
import com.phasetranscrystal.fpsmatch.common.client.tab.TabManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(value = PlayerTabOverlay.class, remap = false)
public abstract class MixinPlayerTabOverlay{
    @Inject(at = {@At("HEAD")}, method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V", cancellable = true, remap = false)
    public void fpsMatch$render$Custom(GuiGraphicsExtractor guiGraphics, int windowWidth, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        if(!FPSMGameHudManager.shouldRender()) {
            return;
        }
        
        List<PlayerInfo> playerInfoList = this.getPlayerInfos();
        if(playerInfoList == null) return;

        TabManager.getInstance().render(guiGraphics, windowWidth, playerInfoList, scoreboard, objective);
        ci.cancel();
    }

    @Shadow(remap = false)
    protected abstract List<PlayerInfo> getPlayerInfos();
}
