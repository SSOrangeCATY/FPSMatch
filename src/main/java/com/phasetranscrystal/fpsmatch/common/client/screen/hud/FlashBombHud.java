package com.phasetranscrystal.fpsmatch.common.client.screen.hud;

import com.phasetranscrystal.fpsmatch.common.effect.FPSMEffectRegister;
import com.phasetranscrystal.fpsmatch.common.effect.FlashBlindnessMobEffect;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.gui.GuiLayer;

public class FlashBombHud implements GuiLayer {

    public static final FlashBombHud INSTANCE = new FlashBombHud();
    public final Minecraft minecraft;

    public FlashBombHud() {
        minecraft = Minecraft.getInstance();
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        LocalPlayer player = minecraft.player;
        if (player != null && player.hasEffect(FPSMEffectRegister.FLASH_BLINDNESS)) {
            MobEffectInstance effectInstance = player.getEffect(FPSMEffectRegister.FLASH_BLINDNESS);
            if (effectInstance != null && effectInstance.getEffect().value() instanceof FlashBlindnessMobEffect flashBlindnessMobEffect) {
                float fullBlindnessTime = flashBlindnessMobEffect.getFullBlindnessTime();
                if(fullBlindnessTime > 0){
                    int colorWithAlpha = RenderUtil.color(255, 255, 255, 255);
                    guiGraphics.fill(0, 0, screenWidth, screenHeight, colorWithAlpha);
                }else{
                    float ticker = flashBlindnessMobEffect.getTicker();
                    if (ticker > 0) {
                        int alpha = (int) (ticker / flashBlindnessMobEffect.getTotalBlindnessTime() * 255);
                        int colorWithAlpha = RenderUtil.color(255, 255, 255, alpha);
                        guiGraphics.fill(0, 0, screenWidth, screenHeight, colorWithAlpha);
                    }
                }
            }
        }
    }

}
