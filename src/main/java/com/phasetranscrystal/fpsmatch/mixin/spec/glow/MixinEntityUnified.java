package com.phasetranscrystal.fpsmatch.mixin.spec.glow;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.client.data.FPSMClientGlobalData;
import com.phasetranscrystal.fpsmatch.common.client.spec.SpectatorGlowManager;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntityUnified {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null) {
            return;
        }
        Entity self = (Entity) (Object) this;

        if (self instanceof LivingEntity living) {
            // 旁观者发光：由 SpectatorGlowManager 管理
            if (localPlayer.isSpectator()) {
                cir.setReturnValue(SpectatorGlowManager.shouldGlow(living));
                cir.cancel();
                return;
            }

            // 普通队伍玩家：客户端按队伍判断透视发光
            if (living instanceof Player target) {
                FPSMClientGlobalData data = FPSMClient.getGlobalData();
                boolean sameTeam = data.isSameTeam(localPlayer, target);
                boolean localInNormalTeam = data.isInNormalTeam();
                boolean targetIsSelf = target.getUUID().equals(localPlayer.getUUID());

                if (localInNormalTeam && !targetIsSelf) {
                    // 队友发光
                    if (sameTeam && data.isTeamGlow()) {
                        cir.setReturnValue(true);
                        cir.cancel();
                        return;
                    }
                    // 敌方发光
                    if (!sameTeam && data.isEnemyGlow()) {
                        cir.setReturnValue(true);
                        cir.cancel();
                        return;
                    }
                }

                if (FPSMConfig.Server.disableDefaultGlow.get()) {
                    cir.setReturnValue(living.hasEffect(MobEffects.GLOWING));
                    cir.cancel();
                }
                return;
            }

            if (FPSMConfig.Server.disableDefaultGlow.get()) {
                cir.setReturnValue(living.hasEffect(MobEffects.GLOWING));
                cir.cancel();
            }
        } else if (FPSMConfig.Server.disableDefaultGlow.get()) {
            cir.setReturnValue(self instanceof ItemEntity itemEntity && itemEntity.hasGlowingTag());
            cir.cancel();
        }
    }
}