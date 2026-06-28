package com.tacz.guns.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.model.functional.ShellRender;
import com.tacz.guns.client.renderer.other.HumanoidOffhandRender;
import com.tacz.guns.client.renderer.other.LivingEntityRenderStateTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemInHandLayer.class, remap = false)
public class ItemInHandLayerMixin {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V", at = @At("TAIL"), remap = false)
    private void tacz$submitTail(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight,
                                 ArmedEntityRenderState state, float yRot, float xRot, CallbackInfo ci) {
        MuzzleFlashRender.isSelf = false;
        ShellRender.isSelf = false;

        LivingEntity entity = LivingEntityRenderStateTracker.get(state);
        if (entity != null) {
            HumanoidOffhandRender.renderGun(entity, poseStack, submitNodeCollector, packedLight);
        }
    }

    @Inject(method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void tacz$submitArmWithItemHead(ArmedEntityRenderState state, ItemStackRenderState itemState, ItemStack itemStack,
                                            HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                            int packedLight, CallbackInfo ci) {
        LivingEntity entity = LivingEntityRenderStateTracker.get(state);
        if (entity == null) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (entity.equals(player)) {
            MuzzleFlashRender.isSelf = true;
            ShellRender.isSelf = true;
        }
        if (IGun.mainHandHoldGun(entity) && arm == HumanoidArm.LEFT) {
            ci.cancel();
        }
    }

    @Inject(method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V", at = @At("TAIL"), remap = false)
    private void tacz$submitArmWithItemTail(ArmedEntityRenderState state, ItemStackRenderState itemState, ItemStack itemStack,
                                            HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                            int packedLight, CallbackInfo ci) {
        MuzzleFlashRender.isSelf = false;
        ShellRender.isSelf = false;
    }
}
