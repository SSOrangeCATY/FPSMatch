package com.phasetranscrystal.fpsmatch.effect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = FPSMatch.MODID,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FlashBlindnessMobEffect extends MobEffect {
    private float fullBlindnessTime;
    private float totalBlindnessTime;
    private float ticker = -1f;
    public FlashBlindnessMobEffect(MobEffectCategory pCategory,float fullBlindnessTime,float totalBlindnessTime) {
        super(pCategory, RenderUtil.color(255,255,255));
        this.fullBlindnessTime = fullBlindnessTime * 20;
        this.totalBlindnessTime = totalBlindnessTime * 20;
        this.ticker = this.totalBlindnessTime;
    }

    public float getFullBlindnessTime() {
        return fullBlindnessTime;
    }

    public void setFullBlindnessTime(float fullBlindnessTime) {
        this.fullBlindnessTime = fullBlindnessTime;
    }

    public float getTotalBlindnessTime() {
        return totalBlindnessTime;
    }

    public void setTotalBlindnessTime(float totalBlindnessTime) {
        this.totalBlindnessTime = totalBlindnessTime;
    }

    public float getTicker(){
        return ticker;
    }
    public void setTotalAndTicker(float totalBlindnessTime){
        this.totalBlindnessTime = totalBlindnessTime;
        this.ticker = totalBlindnessTime;
    }

    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        float alpha = 1.0F;

        if(fullBlindnessTime > 0) {
            guiGraphics.fill(0, 0, screenWidth, screenHeight, RenderUtil.color(255, 255, 255, (int)(alpha * 255)));
            fullBlindnessTime--;
        }

        if(fullBlindnessTime == 0){
            // 根据持续时间降低透明度直至结束
            if(ticker == -1){
                ticker = totalBlindnessTime;
            }

            if(ticker > 0){
                ticker--;
            }

            alpha = ticker / totalBlindnessTime;
            guiGraphics.fill(0, 0, screenWidth, screenHeight, RenderUtil.color(255, 255, 255, (int)(alpha * 255)));
        }
    }

    @SubscribeEvent
    public static void onServerTickEvent(TickEvent.PlayerTickEvent event){
        if(event.phase == TickEvent.Phase.END){
            if (event.player.hasEffect(FPSMEffectRegister.FLASH_BLINDNESS.get())) {
                MobEffectInstance effectInstance = event.player.getEffect(FPSMEffectRegister.FLASH_BLINDNESS.get());
                if(effectInstance != null && effectInstance.getEffect() instanceof FlashBlindnessMobEffect flashBlindnessMobEffect){
                    float ticker = flashBlindnessMobEffect.getTicker();
                    if(flashBlindnessMobEffect.getTicker() > ticker && ticker < 1){
                        event.player.removeEffect(FPSMEffectRegister.FLASH_BLINDNESS.get());
                    }
                };
            }
        }
    }
}