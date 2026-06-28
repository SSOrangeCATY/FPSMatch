package com.tacz.guns.mixin.client;

import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.third.InnerThirdPersonManager;
import com.tacz.guns.client.renderer.other.LivingEntityRenderStateTracker;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerModel.class, remap = false)
public class PlayerModelMixin extends HumanoidModel<AvatarRenderState> {
    @Shadow(remap = false)
    @Final
    public ModelPart leftSleeve;
    @Shadow(remap = false)
    @Final
    public ModelPart rightSleeve;

    public PlayerModelMixin(ModelPart part) {
        super(part);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At(value = "TAIL"), remap = false)
    private void setRotationAnglesTail(AvatarRenderState state, CallbackInfo ci) {
        // 用于清除默认的手臂旋转
        // 当第一人称渲染是，ageInTicks 正好是 0
        if (state.ageInTicks == 0F) {
            ItemStack currentItem = KeepingItemRenderer.getRenderer().getCurrentItem();
            if (IGun.getIGunOrNull(currentItem) != null) {
                tacz$resetAll(this.rightArm);
                tacz$resetAll(this.leftArm);
                this.rightSleeve.loadPose(this.rightArm.storePose());
                this.leftSleeve.loadPose(this.leftArm.storePose());
            }
            return;
        }

        LivingEntity entity = LivingEntityRenderStateTracker.get(state);
        if (entity != null) {
            InnerThirdPersonManager.setRotationAnglesHead(entity, this.rightArm, this.leftArm, this.body, this.head, state.walkAnimationSpeed);
        }
    }

    /**
     * 将给定模型的旋转角度和旋转点重置为零
     */
    @Unique
    private void tacz$resetAll(ModelPart part) {
        part.xRot = 0.0F;
        part.yRot = 0.0F;
        part.zRot = 0.0F;
    }
}
