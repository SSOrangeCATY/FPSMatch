package com.tacz.guns.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = HumanoidModel.class, remap = false)
public class HumanoidModelMixin {
    // 26.1 HumanoidRenderState no longer exposes the LivingEntity required by TACZ's old third-person animation path.
    // The real behavior needs a later render-state modifier slice instead of a half-preserved entity lookup here.
}
