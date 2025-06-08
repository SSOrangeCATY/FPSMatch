package com.phasetranscrystal.fpsmatch.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.phasetranscrystal.fpsmatch.entity.drop.MatchDropEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.NotNull;

public class MatchDropRenderer implements EntityRendererProvider<MatchDropEntity> {

    @Override
    public @NotNull EntityRenderer<MatchDropEntity> create(Context pContext) {
        return new EntityRenderer<>(pContext) {

            final ItemEntityRenderer itemRender = new ItemEntityRenderer(pContext);
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
