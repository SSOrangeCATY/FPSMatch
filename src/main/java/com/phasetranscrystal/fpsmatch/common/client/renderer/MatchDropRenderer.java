package com.phasetranscrystal.fpsmatch.common.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.phasetranscrystal.fpsmatch.common.entity.drop.MatchDropEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.NotNull;

public class MatchDropRenderer implements EntityRendererProvider<MatchDropEntity> {

    @Override
    public @NotNull EntityRenderer<MatchDropEntity> create(@NotNull Context context) {
        return new EntityRenderer<>(context) {

            final ItemEntityRenderer itemRender = new ItemEntityRenderer(context);
            ItemEntity item = null;

            @Override
            public @NotNull ResourceLocation getTextureLocation(@NotNull MatchDropEntity entity) {
                return TextureAtlas.LOCATION_BLOCKS;
            }
            @Override
            public void render(@NotNull MatchDropEntity entity, float pEntityYaw, float pPartialTicks, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int pPackedLight) {
                poseStack.pushPose();
                if(item == null){
                    item = new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getItem());
                }
                if(!item.getItem().equals(entity.getItem(),false)){
                    item.setItem(entity.getItem());
                }
                itemRender.render(item,0,0,poseStack,bufferSource,pPackedLight);
                poseStack.popPose();
            }
        };
    }
}
