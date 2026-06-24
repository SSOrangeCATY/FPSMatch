package com.phasetranscrystal.fpsmatch.common.effect;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.effect.FlashBombAddonS2CPacket;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;


@net.neoforged.fml.common.EventBusSubscriber(modid = FPSMatch.MODID)
public class FlashBlindnessMobEffect extends MobEffect {
    private int fullBlindnessTime = 0;
    private int totalBlindnessTime = 0;
    private int ticker = 0;
    public FlashBlindnessMobEffect(MobEffectCategory pCategory) {
        super(pCategory, RenderUtil.color(255,255,255));
    }

    public int getFullBlindnessTime() {
        return fullBlindnessTime;
    }

    public void setFullBlindnessTime(int fullBlindnessTime) {
        this.fullBlindnessTime = fullBlindnessTime;
    }

    public int getTotalBlindnessTime() {
        return totalBlindnessTime;
    }

    public void setTotalBlindnessTime(int totalBlindnessTime) {
        this.totalBlindnessTime = totalBlindnessTime;
    }

    public void setTicker(int ticker){
        this.ticker = ticker;
    }

    public int getTicker(){
        return ticker;
    }
    public void setTotalAndTicker(int totalBlindnessTime){
        this.totalBlindnessTime = totalBlindnessTime;
        this.ticker = totalBlindnessTime;
    }


    @SubscribeEvent
    public static void onServerTickEvent(PlayerTickEvent.Post event){
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!player.level().isClientSide() && player.hasEffect(FPSMEffectRegister.FLASH_BLINDNESS)) {
                MobEffectInstance effectInstance = player.getEffect(FPSMEffectRegister.FLASH_BLINDNESS);
                if(effectInstance != null && effectInstance.getEffect().value() instanceof FlashBlindnessMobEffect flashBlindnessMobEffect){
                    int fullBlindnessTime = flashBlindnessMobEffect.getFullBlindnessTime();
                    if(fullBlindnessTime > 0){
                        flashBlindnessMobEffect.setFullBlindnessTime(fullBlindnessTime - 1);
                        FPSMatch.sendToPlayer(player, new FlashBombAddonS2CPacket(flashBlindnessMobEffect.getFullBlindnessTime(),flashBlindnessMobEffect.getTotalBlindnessTime(),flashBlindnessMobEffect.getTicker()));
                    }else{
                        int ticker = flashBlindnessMobEffect.getTicker();
                        if(ticker >= 1){
                            flashBlindnessMobEffect.setTicker(ticker - 1);
                        }
                        FPSMatch.sendToPlayer(player, new FlashBombAddonS2CPacket(flashBlindnessMobEffect.getFullBlindnessTime(),flashBlindnessMobEffect.getTotalBlindnessTime(),flashBlindnessMobEffect.getTicker()));
                        if(flashBlindnessMobEffect.getTicker() == 0){
                            player.removeEffect(FPSMEffectRegister.FLASH_BLINDNESS);
                        }
                    }
                }
            }
        }
    }
}
