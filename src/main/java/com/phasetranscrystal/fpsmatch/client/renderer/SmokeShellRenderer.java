package com.phasetranscrystal.fpsmatch.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.phasetranscrystal.fpsmatch.entity.SmokeShellEntity;
import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class SmokeShellRenderer implements EntityRendererProvider<SmokeShellEntity> {

    @Override
    public @NotNull EntityRenderer<SmokeShellEntity> create(Context pContext) {
        return new EntityRenderer<>(pContext) {
            ItemEntity item = null;
            ItemEntityRenderer itemRender = null;

            @Override
            public @NotNull ResourceLocation getTextureLocation(SmokeShellEntity pEntity) {
                return TextureAtlas.LOCATION_BLOCKS;
            }

            @Override
            public void render(SmokeShellEntity pEntity, float pEntityYaw, float pPartialTicks, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight) {
                pPoseStack.pushPose();
                pPoseStack.translate(0.0F, -0.25F, 0.0F);
                if(item == null){
                    item = new ItemEntity(pEntity.level(), pEntity.getX(), pEntity.getY(), pEntity.getZ(), new ItemStack(FPSMItemRegister.SMOKE_SHELL.get()));
                    itemRender = new ItemEntityRenderer(pContext);
                }
                itemRender.render(item, pEntityYaw, 0, pPoseStack, pBuffer, pPackedLight);
                pPoseStack.popPose();
                super.render(pEntity, pEntityYaw, pPartialTicks, pPoseStack, pBuffer, pPackedLight);
            }

        };
    }
}