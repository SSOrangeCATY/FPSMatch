package com.phasetranscrystal.fpsmatch.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.phasetranscrystal.fpsmatch.entity.CompositionC4Entity;
import com.phasetranscrystal.fpsmatch.entity.MatchDropEntity;
import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;

public class MatchDropRenderer implements EntityRendererProvider<MatchDropEntity> {

    @Override
    public @NotNull EntityRenderer<MatchDropEntity> create(Context pContext) {
        return new EntityRenderer<>(pContext) {

            ItemEntityRenderer itemRender = new ItemEntityRenderer(pContext);
            ItemEntity item = null;

            @Override
            public @NotNull ResourceLocation getTextureLocation(MatchDropEntity pEntity) {
                return TextureAtlas.LOCATION_BLOCKS;
            }
            @Override
            public void render(MatchDropEntity pEntity, float pEntityYaw, float pPartialTicks, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight) {
                pPoseStack.pushPose();
                if(item == null){
                    item = new ItemEntity(pEntity.level(), pEntity.getX(), pEntity.getY(), pEntity.getZ(), pEntity.getItem());
                }
                if(!item.getItem().equals(pEntity.getItem(),false)){
                    item.setItem(pEntity.getItem());
                }
                itemRender.render(item,0,0,pPoseStack,pBuffer,pPackedLight);
                pPoseStack.popPose();
            }
        };
    }
}
